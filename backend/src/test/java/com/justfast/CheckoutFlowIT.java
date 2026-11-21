package com.justfast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justfast.cart.application.CartService;
import com.justfast.catalog.application.CatalogAdminService;
import com.justfast.catalog.domain.Product.ProductDetails;
import com.justfast.checkout.CheckoutService;
import com.justfast.checkout.CheckoutService.CheckoutResult;
import com.justfast.common.error.BusinessRuleException;
import com.justfast.identity.application.AddressService;
import com.justfast.identity.application.RegistrationService;
import com.justfast.identity.domain.Address.AddressDetails;
import com.justfast.identity.domain.Role;
import com.justfast.identity.domain.User;
import com.justfast.identity.infrastructure.UserRepository;
import com.justfast.identity.security.AppUserPrincipal;
import com.justfast.payments.application.PaymentReconciliationService;
import com.justfast.payments.gateway.WebhookSignature;
import com.justfast.payments.gateway.sandbox.SandboxPaymentGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end checks of the acceptance criteria against a real MySQL (Testcontainers) with the sandbox provider.
 * Run with {@code mvn verify} (requires Docker).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CheckoutFlowIT {

    private static final String WEBHOOK_SECRET = "it-webhook-secret";

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("payments.provider", () -> "sandbox");
        registry.add("payments.sandbox.webhook-secret", () -> WEBHOOK_SECRET);
        // The test delivers webhooks itself; make the sandbox's own delivery fail fast.
        registry.add("payments.sandbox.webhook-url", () -> "http://127.0.0.1:9/unreachable");
        registry.add("store.public-base-url", () -> "https://shop.test");
        registry.add("COOKIE_SECURE", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired RegistrationService registration;
    @Autowired UserRepository users;
    @Autowired AddressService addresses;
    @Autowired CartService carts;
    @Autowired CatalogAdminService catalogAdmin;
    @Autowired CheckoutService checkout;
    @Autowired PaymentReconciliationService reconciliation;

    record Customer(AppUserPrincipal principal, Long addressId) {
    }

    // --- Acceptance criteria ---------------------------------------------------------------------------------

    @Test
    void customerFindsProductPaysInSandboxAndSeesConfirmedOrder() throws Exception {
        Long productId = newProduct(new BigDecimal("12.50"), 5);
        Customer customer = newCustomer();
        String sku = jdbc.queryForObject("select sku from products where id = ?", String.class, productId);

        mvc.perform(get("/api/products").param("q", sku))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(productId))
                .andExpect(jsonPath("$.items[0].inStock").value(true));

        mvc.perform(put("/api/cart/items/{id}", productId).with(user(customer.principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(30.00));

        JsonNode result = body(mvc.perform(post("/api/checkout").with(user(customer.principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("addressId", customer.addressId(),
                                "idempotencyKey", UUID.randomUUID().toString()))))
                .andExpect(status().isOk()));
        long orderId = result.path("orderId").asLong();
        String checkoutUrl = result.path("checkoutUrl").asText();
        assertThat(checkoutUrl).startsWith("https://shop.test/api/sandbox-provider/checkout/sbx_cs_");

        // The customer pays on the provider's hosted page...
        String sessionPath = checkoutUrl.replace("https://shop.test", "");
        mvc.perform(post(sessionPath + "/pay")).andExpect(status().isSeeOther());
        // ...and the signed webhook confirms it.
        sendWebhook("payment.succeeded", attemptIdOf(orderId), new BigDecimal("30.00"), "USD");

        mvc.perform(get("/api/orders/{id}", orderId).with(user(customer.principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(12.50));
        assertThat(stock(productId)).containsExactly(3, 0);
    }

    @Test
    void twoConcurrentCheckoutsCannotReserveTheLastUnit() throws Exception {
        Long productId = newProduct(new BigDecimal("50.00"), 1);
        Customer first = newCustomer();
        Customer second = newCustomer();
        carts.setQuantity(first.principal().id(), productId, 1);
        carts.setQuantity(second.principal().id(), productId, 1);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<CheckoutResult>> futures = new ArrayList<>();
        for (Customer c : List.of(first, second)) {
            Callable<CheckoutResult> task = () -> {
                start.await();
                return checkout.checkout(c.principal().id(), c.principal().email(), c.addressId(), UUID.randomUUID().toString());
            };
            futures.add(pool.submit(task));
        }
        start.countDown();

        int succeeded = 0;
        int outOfStock = 0;
        for (Future<CheckoutResult> f : futures) {
            try {
                f.get();
                succeeded++;
            } catch (java.util.concurrent.ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(BusinessRuleException.class);
                assertThat(((BusinessRuleException) e.getCause()).code()).isEqualTo("INSUFFICIENT_STOCK");
                outOfStock++;
            }
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(1);
        assertThat(outOfStock).isEqualTo(1);
        assertThat(stock(productId)).containsExactly(1, 1);
    }

    @Test
    void repeatedCheckoutRequestsCreateOneOrderAndReserveOnce() {
        Long productId = newProduct(new BigDecimal("10.00"), 10);
        Customer customer = newCustomer();
        carts.setQuantity(customer.principal().id(), productId, 3);
        String key = UUID.randomUUID().toString();

        CheckoutResult first = checkout.checkout(customer.principal().id(), customer.principal().email(), customer.addressId(), key);
        CheckoutResult again = checkout.checkout(customer.principal().id(), customer.principal().email(), customer.addressId(), key);

        assertThat(again.orderId()).isEqualTo(first.orderId());
        assertThat(again.checkoutUrl()).isEqualTo(first.checkoutUrl());
        assertThat(jdbc.queryForObject("select count(*) from orders where user_id = ?", Integer.class,
                customer.principal().id())).isEqualTo(1);
        assertThat(stock(productId)).containsExactly(10, 3);
    }

    @Test
    void duplicatePaymentEventsDoNotConsumeInventoryTwice() throws Exception {
        Long productId = newProduct(new BigDecimal("8.00"), 10);
        Customer customer = newCustomer();
        carts.setQuantity(customer.principal().id(), productId, 2);
        CheckoutResult order = checkout.checkout(customer.principal().id(), customer.principal().email(), customer.addressId(),
                UUID.randomUUID().toString());
        String attemptId = attemptIdOf(order.orderId());

        String eventId = "sbx_evt_" + UUID.randomUUID();
        sendWebhook(eventId, "payment.succeeded", attemptId, new BigDecimal("21.00"), "USD");
        sendWebhook(eventId, "payment.succeeded", attemptId, new BigDecimal("21.00"), "USD");
        // A different event id reporting the same success is also harmless.
        sendWebhook("payment.succeeded", attemptId, new BigDecimal("21.00"), "USD");

        assertThat(orderStatus(order.orderId())).isEqualTo("PAID");
        assertThat(stock(productId)).containsExactly(8, 0);
    }

    @Test
    void failedPaymentReleasesStockAndCannotBeFulfilled() throws Exception {
        Long productId = newProduct(new BigDecimal("15.00"), 4);
        Customer customer = newCustomer();
        carts.setQuantity(customer.principal().id(), productId, 4);
        CheckoutResult order = checkout.checkout(customer.principal().id(), customer.principal().email(), customer.addressId(),
                UUID.randomUUID().toString());
        assertThat(stock(productId)).containsExactly(4, 4);

        sendWebhook("payment.failed", attemptIdOf(order.orderId()), new BigDecimal("65.00"), "USD");

        assertThat(orderStatus(order.orderId())).isEqualTo("CANCELLED");
        assertThat(stock(productId)).containsExactly(4, 0);
        mvc.perform(post("/api/admin/orders/{id}/ship", order.orderId()).with(user(newAdmin())).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_TRANSITION"));
    }

    @Test
    void expiredUnpaidOrderIsReconciledAndReleased() {
        Long productId = newProduct(new BigDecimal("5.00"), 2);
        Customer customer = newCustomer();
        carts.setQuantity(customer.principal().id(), productId, 2);
        CheckoutResult order = checkout.checkout(customer.principal().id(), customer.principal().email(), customer.addressId(),
                UUID.randomUUID().toString());
        jdbc.update("update orders set reservation_expires_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)), order.orderId());

        reconciliation.reconcileExpiredOrder(order.orderId());

        assertThat(orderStatus(order.orderId())).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select status from payment_attempts where order_id = ?", String.class,
                order.orderId())).isEqualTo("EXPIRED");
        assertThat(stock(productId)).containsExactly(2, 0);
    }

    @Test
    void latePaymentOnExpiredOrderGoesToReviewNotFulfillment() throws Exception {
        Long productId = newProduct(new BigDecimal("5.00"), 2);
        Customer customer = newCustomer();
        carts.setQuantity(customer.principal().id(), productId, 1);
        CheckoutResult order = checkout.checkout(customer.principal().id(), customer.principal().email(), customer.addressId(),
                UUID.randomUUID().toString());
        String attemptId = attemptIdOf(order.orderId());
        sendWebhook("session.expired", attemptId, null, null);
        assertThat(orderStatus(order.orderId())).isEqualTo("CANCELLED");

        sendWebhook("payment.succeeded", attemptId, new BigDecimal("10.00"), "USD");

        assertThat(orderStatus(order.orderId())).isEqualTo("PAYMENT_REVIEW");
        assertThat(stock(productId)).containsExactly(2, 0);
        mvc.perform(post("/api/admin/orders/{id}/ship", order.orderId()).with(user(newAdmin())).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void webhooksWithInvalidSignaturesAreRejected() throws Exception {
        mvc.perform(post("/api/payments/webhooks/sandbox").contentType(MediaType.APPLICATION_JSON)
                        .header(SandboxPaymentGateway.SIGNATURE_HEADER, "t=1,v1=deadbeef")
                        .content("{\"id\":\"evt_forged\",\"type\":\"payment.succeeded\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/payments/webhooks/sandbox").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customersCannotSeeOtherCustomersOrdersOrCallAdminEndpoints() throws Exception {
        Long productId = newProduct(new BigDecimal("3.00"), 5);
        Customer owner = newCustomer();
        Customer other = newCustomer();
        carts.setQuantity(owner.principal().id(), productId, 1);
        CheckoutResult order = checkout.checkout(owner.principal().id(), owner.principal().email(), owner.addressId(),
                UUID.randomUUID().toString());

        mvc.perform(get("/api/orders/{id}", order.orderId()).with(user(other.principal())))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/{id}/payment-session", order.orderId()).with(user(other.principal())).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/account/addresses").with(user(other.principal())))
                .andExpect(jsonPath("$[?(@.id == " + owner.addressId() + ")]").isEmpty());
        mvc.perform(get("/api/admin/orders").with(user(owner.principal())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/products/{id}/archive", productId).with(user(owner.principal())).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
    }

    @Test
    void browserWritesRequireCsrfToken() throws Exception {
        Customer customer = newCustomer();
        mvc.perform(post("/api/checkout").with(user(customer.principal()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    // --- Helpers ---------------------------------------------------------------------------------------------

    private Long newProduct(BigDecimal price, int stock) {
        Long categoryId = jdbc.queryForObject("select min(id) from categories", Long.class);
        String sku = "IT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return catalogAdmin.createProduct(sku, new ProductDetails("Test " + sku, "test", price, null), categoryId, stock)
                .getId();
    }

    private Customer newCustomer() {
        User user = registration.registerCustomer("c-" + UUID.randomUUID() + "@example.test", "password123", "Test Customer");
        Long addressId = addresses.create(user.getId(),
                new AddressDetails("Test Customer", "1 Test St", null, "Testville", "CA", "90001", "US", null)).getId();
        return new Customer(AppUserPrincipal.from(user), addressId);
    }

    private AppUserPrincipal newAdmin() {
        User admin = users.save(new User("a-" + UUID.randomUUID() + "@example.test", "x", "Admin", Role.ADMIN, Instant.now()));
        return AppUserPrincipal.from(admin);
    }

    private String attemptIdOf(Long orderId) {
        return jdbc.queryForObject("select id from payment_attempts where order_id = ? order by created_at desc limit 1",
                String.class, orderId);
    }

    private String orderStatus(Long orderId) {
        return jdbc.queryForObject("select status from orders where id = ?", String.class, orderId);
    }

    /** [on_hand, reserved] */
    private List<Integer> stock(Long productId) {
        return jdbc.queryForObject("select on_hand, reserved from inventory_items where product_id = ?",
                (rs, n) -> List.of(rs.getInt(1), rs.getInt(2)), productId);
    }

    private void sendWebhook(String type, String attemptId, BigDecimal amount, String currency) throws Exception {
        sendWebhook("sbx_evt_" + UUID.randomUUID(), type, attemptId, amount, currency);
    }

    private void sendWebhook(String eventId, String type, String attemptId, BigDecimal amount, String currency)
            throws Exception {
        Map<String, Object> event = new java.util.LinkedHashMap<>();
        event.put("id", eventId);
        event.put("type", type);
        event.put("attemptId", attemptId);
        event.put("amount", amount == null ? "0" : amount.toPlainString());
        if (currency != null) {
            event.put("currency", currency);
        }
        String payload = json.writeValueAsString(event);
        mvc.perform(post("/api/payments/webhooks/sandbox").contentType(MediaType.APPLICATION_JSON)
                        .header(SandboxPaymentGateway.SIGNATURE_HEADER, WebhookSignature.sign(WEBHOOK_SECRET, payload, Instant.now()))
                        .content(payload))
                .andExpect(status().isOk());
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
