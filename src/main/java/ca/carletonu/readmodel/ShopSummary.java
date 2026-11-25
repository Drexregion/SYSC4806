package ca.carletonu.readmodel;

import ca.carletonu.events.ProductKind;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;

import java.util.HashMap;
import java.util.Map;

/**
 * A read model: one row per shop, shaped for the catalogue page.
 *
 * This is NOT the source of truth. It is derived entirely from events on the
 * shop-created and product-added topics, which means it can be dropped and
 * rebuilt by replaying the log. Nothing writes to it except the projection.
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

    /**
     * The products already applied to this row, by id, with their kind.
     * Keeping the ids rather than bare counters is what makes product events
     * idempotent: a replayed event finds its id already here and changes nothing.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "shop_summary_product", joinColumns = @JoinColumn(name = "shop_summary_id"))
    @MapKeyColumn(name = "product_id")
    @Column(name = "kind")
    @Enumerated(EnumType.STRING)
    private Map<Long, ProductKind> products = new HashMap<>();

    protected ShopSummary() {
    }

    public ShopSummary(Long shopId, Long sellerId, String name, String description, String type) {
        this.shopId = shopId;
        this.sellerId = sellerId;
        this.name = name;
        this.description = description;
        this.type = type;
    }

    /**
     * A row for a shop whose products arrived before its shop-created event.
     * The two topics are read independently, so on a replay that can happen.
     * The shop's details are filled in by {@link #describe} once its event lands.
     */
    static ShopSummary placeholder(Long shopId) {
        ShopSummary summary = new ShopSummary();
        summary.shopId = shopId;
        return summary;
    }

    /** Whether the shop-created event for this row has been applied yet. */
    public boolean isDescribed() {
        return name != null;
    }

    void describe(Long sellerId, String name, String description, String type) {
        this.sellerId = sellerId;
        this.name = name;
        this.description = description;
        this.type = type;
    }

    /**
     * Records a product against this shop.
     *
     * @return false when the product was already recorded, so nothing changed
     */
    boolean addProduct(Long productId, ProductKind kind) {
        return products.putIfAbsent(productId, kind) == null;
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

    public int getProductCount() {
        return products.size();
    }

    public long getItemCount() {
        return countOf(ProductKind.ITEM);
    }

    public long getServiceCount() {
        return countOf(ProductKind.SERVICE);
    }

    private long countOf(ProductKind kind) {
        return products.values().stream().filter(kind::equals).count();
    }
}
