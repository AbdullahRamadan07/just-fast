package com.justfast.checkout;

import com.justfast.cart.application.CartService;
import com.justfast.catalog.application.CatalogService;
import com.justfast.catalog.domain.Product;
import com.justfast.common.config.StoreProperties;
import com.justfast.common.error.BusinessRuleException;
import com.justfast.identity.application.AddressService;
import com.justfast.identity.domain.Address;
import com.justfast.inventory.application.StockReservationService;
import com.justfast.orders.domain.Order;
import com.justfast.orders.domain.OrderLine;
import com.justfast.orders.domain.ShippingAddress;
import com.justfast.orders.infrastructure.OrderRepository;
import com.justfast.payments.application.PaymentSessionService;
import com.justfast.payments.domain.PaymentAttempt;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional half of checkout: price the cart from current catalog data, reserve stock, and record a pending
 * order plus a pending payment attempt. Either all of it commits or none of it does.
 */
@Service
public class OrderPlacementService {

    private final CartService carts;
    private final CatalogService catalog;
    private final AddressService addresses;
    private final StockReservationService reservations;
    private final PaymentSessionService payments;
    private final OrderRepository orders;
    private final OrderNumberGenerator orderNumbers;
    private final StoreProperties store;
    private final Clock clock;

    public OrderPlacementService(CartService carts, CatalogService catalog, AddressService addresses,
                                 StockReservationService reservations, PaymentSessionService payments,
                                 OrderRepository orders, OrderNumberGenerator orderNumbers, StoreProperties store,
                                 Clock clock) {
        this.carts = carts;
        this.catalog = catalog;
        this.addresses = addresses;
        this.reservations = reservations;
        this.payments = payments;
        this.orders = orders;
        this.orderNumbers = orderNumbers;
        this.store = store;
        this.clock = clock;
    }

    public record PlacedOrder(Long orderId, String orderNumber, String paymentAttemptId, Instant payBy) {
    }

    @Transactional
    public PlacedOrder place(Long userId, Long addressId, String idempotencyKey) {
        Map<Long, Integer> quantities = carts.quantities(userId);
        if (quantities.isEmpty()) {
            throw new BusinessRuleException("CART_EMPTY", "Your cart is empty.");
        }
        Address address = addresses.requireOwned(userId, addressId);
        addresses.requireShippableCountry(address.details().country());

        List<OrderLine> lines = priceLines(quantities);
        Instant now = clock.instant();
        Order order = orders.save(Order.place(orderNumbers.next(now), userId, idempotencyKey, store.currency(), lines,
                store.flatShippingFee(), ShippingAddress.snapshotOf(address.details()), now,
                now.plus(store.reservationTtl())));

        reservations.reserve(order.getId(), lines.stream()
                .map(l -> new StockReservationService.Line(l.productId(), l.productName(), l.quantity()))
                .toList());
        PaymentAttempt attempt = payments.createAttempt(order.getId(), order.getTotal(), order.getCurrency());
        carts.clear(userId);

        return new PlacedOrder(order.getId(), order.getOrderNumber(), attempt.getId(), order.getReservationExpiresAt());
    }

    /** Prices are always taken from the catalog at checkout time, never from the client. */
    private List<OrderLine> priceLines(Map<Long, Integer> quantities) {
        Map<Long, Product> products = catalog.findAllById(quantities.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return quantities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(entry -> {
                    Product product = products.get(entry.getKey());
                    if (product == null || !product.isPurchasable()) {
                        throw new BusinessRuleException("PRODUCT_UNAVAILABLE",
                                "An item in your cart is no longer available. Please review your cart.");
                    }
                    return new OrderLine(product.getId(), product.getSku(), product.getName(), product.getPrice(),
                            entry.getValue());
                })
                .toList();
    }
}
