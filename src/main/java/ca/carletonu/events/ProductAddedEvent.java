package ca.carletonu.events;

/**
 * Published when a merchant adds a product to one of their shops.
 *
 * Keyed by shopId on the product-added topic, so every product of one shop
 * lands on the same partition and is read in the order it was added.
 *
 * The kind says which of the last three fields mean anything:
 * an ITEM carries quantity and productType, a SERVICE carries duration.
 * The fields that do not apply to the kind are null.
 */
public record ProductAddedEvent(
        Long shopId,
        Long productId,
        ProductKind kind,
        String name,
        String description,
        Double price,
        Integer quantity,
        String productType,
        Integer duration
) {

    public static ProductAddedEvent item(Long shopId, Long productId, String name, String description,
                                         Double price, int quantity, String productType) {
        return new ProductAddedEvent(shopId, productId, ProductKind.ITEM, name, description, price,
                quantity, productType, null);
    }

    public static ProductAddedEvent service(Long shopId, Long productId, String name, String description,
                                            Double price, int duration) {
        return new ProductAddedEvent(shopId, productId, ProductKind.SERVICE, name, description, price,
                null, null, duration);
    }
}
