package com.justfast.cart.application;

import com.justfast.cart.domain.Cart;
import com.justfast.cart.domain.CartItem;
import com.justfast.cart.infrastructure.CartRepository;
import com.justfast.catalog.application.CatalogService;
import com.justfast.catalog.domain.Product;
import com.justfast.common.config.StoreProperties;
import com.justfast.common.error.BusinessRuleException;
import com.justfast.common.money.Pricing;
import com.justfast.common.money.Pricing.PricedLine;
import com.justfast.common.money.Pricing.Totals;
import com.justfast.inventory.application.InventoryService;
import com.justfast.inventory.application.InventoryService.StockLevel;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cart use cases. Every operation is scoped to the authenticated user's own cart. */
@Service
public class CartService {

    private final CartRepository carts;
    private final CatalogService catalog;
    private final InventoryService inventory;
    private final StoreProperties store;
    private final Clock clock;

    public CartService(CartRepository carts, CatalogService catalog, InventoryService inventory,
                       StoreProperties store, Clock clock) {
        this.carts = carts;
        this.catalog = catalog;
        this.inventory = inventory;
        this.store = store;
        this.clock = clock;
    }

    public record CartLineView(Long productId, String sku, String name, String imageUrl, BigDecimal unitPrice,
                               int quantity, BigDecimal lineTotal, int availableQuantity, boolean purchasable) {
    }

    public record CartView(List<CartLineView> items, String currency, BigDecimal subtotal, BigDecimal shippingFee,
                           BigDecimal total, int itemCount, boolean checkoutReady) {
    }

    @Transactional(readOnly = true)
    public CartView view(Long userId) {
        return carts.findByUserId(userId).map(this::toView).orElseGet(() -> toView(List.of()));
    }

    @Transactional
    public CartView setQuantity(Long userId, Long productId, int quantity) {
        Cart cart = carts.findByUserId(userId).orElseGet(() -> carts.save(new Cart(userId, clock.instant())));
        if (quantity > 0) {
            Product product = catalog.requireActiveProduct(productId);
            int available = inventory.stockLevel(productId).available();
            if (quantity > available) {
                throw new BusinessRuleException("INSUFFICIENT_STOCK",
                        available <= 0
                                ? "\"" + product.getName() + "\" is out of stock."
                                : "Only " + available + " of \"" + product.getName() + "\" available.");
            }
        }
        cart.setQuantity(productId, quantity, clock.instant());
        return toView(cart);
    }

    @Transactional
    public CartView clear(Long userId) {
        carts.findByUserId(userId).ifPresent(cart -> cart.clear(clock.instant()));
        return toView(List.of());
    }

    /** Snapshot of the cart's product quantities, used by checkout. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> quantities(Long userId) {
        return carts.findByUserId(userId)
                .map(cart -> cart.getItems().stream()
                        .collect(Collectors.toMap(CartItem::getProductId, CartItem::getQuantity)))
                .orElse(Map.of());
    }

    private CartView toView(Cart cart) {
        return toView(cart.getItems());
    }

    private CartView toView(List<CartItem> items) {
        List<Long> productIds = items.stream().map(CartItem::getProductId).toList();
        Map<Long, Product> products = catalog.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, StockLevel> stock = inventory.stockLevels(productIds);

        List<CartLineView> lines = items.stream()
                .filter(item -> products.containsKey(item.getProductId()))
                .map(item -> {
                    Product p = products.get(item.getProductId());
                    int available = Math.max(InventoryService.levelOrNone(stock, p.getId()).available(), 0);
                    PricedLine priced = new PricedLine(p.getPrice(), item.getQuantity());
                    return new CartLineView(p.getId(), p.getSku(), p.getName(), p.getImageUrl(), p.getPrice(),
                            item.getQuantity(), priced.lineTotal(), available,
                            p.isPurchasable() && item.getQuantity() <= available);
                })
                .toList();

        Totals totals = Pricing.totals(
                lines.stream().map(l -> new PricedLine(l.unitPrice(), l.quantity())).toList(),
                store.flatShippingFee());
        int count = lines.stream().mapToInt(CartLineView::quantity).sum();
        boolean ready = !lines.isEmpty() && lines.stream().allMatch(CartLineView::purchasable);
        return new CartView(lines, store.currency(), totals.subtotal(), totals.shippingFee(), totals.total(), count, ready);
    }
}
