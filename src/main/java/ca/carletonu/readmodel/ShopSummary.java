package ca.carletonu.readmodel;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

/**
 * A read model: one row per shop, shaped for the catalogue page.
 *
 * This is NOT the source of truth. It is derived entirely from events on the
 * shop-created topic, which means it can be dropped and rebuilt by replaying
 * the log. Nothing writes to it except the projection.
 *
 * It deliberately duplicates fields that already live on Shop. That is the
 * point of CQRS: the read side is allowed its own shape, so the page it serves
 * is a single lookup instead of a join across Shop, Seller and Product.
 */
@Entity
public class ShopSummary {

    @Id
    @GeneratedValue
    private Long id;

    /** The id of the shop in the write model this row was built from. */
    private Long shopId;

    private Long sellerId;
    private String name;
    private String description;
    private String type;

    protected ShopSummary() {
    }

    public ShopSummary(Long shopId, Long sellerId, String name, String description, String type) {
        this.shopId = shopId;
        this.sellerId = sellerId;
        this.name = name;
        this.description = description;
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public Long getShopId() {
        return shopId;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getType() {
        return type;
    }
}
