package com.justfast.inventory.infrastructure;

import com.justfast.inventory.domain.InventoryItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    List<InventoryItem> findByProductIdIn(Collection<Long> productIds);

    /**
     * Atomically reserves units only if enough are available. The conditional UPDATE takes a row lock,
     * so two concurrent checkouts cannot both reserve the last unit: the second one updates 0 rows.
     *
     * @return 1 if reserved, 0 if there was not enough available stock
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update InventoryItem i
               set i.reserved = i.reserved + :quantity, i.version = i.version + 1
             where i.productId = :productId
               and i.onHand - i.reserved >= :quantity
            """)
    int tryReserve(@Param("productId") Long productId, @Param("quantity") int quantity);

    /** Payment succeeded: the reserved units leave the warehouse. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update InventoryItem i
               set i.onHand = i.onHand - :quantity, i.reserved = i.reserved - :quantity, i.version = i.version + 1
             where i.productId = :productId
               and i.reserved >= :quantity
            """)
    int commitReserved(@Param("productId") Long productId, @Param("quantity") int quantity);

    /** Payment failed or expired: the reserved units become available again. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update InventoryItem i
               set i.reserved = i.reserved - :quantity, i.version = i.version + 1
             where i.productId = :productId
               and i.reserved >= :quantity
            """)
    int releaseReserved(@Param("productId") Long productId, @Param("quantity") int quantity);
}
