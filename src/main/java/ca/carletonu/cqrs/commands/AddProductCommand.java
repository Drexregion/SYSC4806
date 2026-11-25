package ca.carletonu.cqrs.commands;

import ca.carletonu.events.ProductKind;

/**
 * Add a product to a shop: either an item or a service.
 *
 * An ITEM needs quantity (and may have a productType); a SERVICE needs
 * duration. The fields that do not apply to the kind are ignored. There is no
 * productId: the id does not exist until the handler saves the product.
 */
public record AddProductCommand(
        Long shopId,
        ProductKind kind,
        String name,
        String description,
        Double price,
        Integer quantity,
        String productType,
        Integer duration
) {

    public static AddProductCommand item(Long shopId, String name, String description, Double price,
                                         Integer quantity, String productType) {
        return new AddProductCommand(shopId, ProductKind.ITEM, name, description, price,
                quantity, productType, null);
    }

    public static AddProductCommand service(Long shopId, String name, String description, Double price,
                                            Integer duration) {
        return new AddProductCommand(shopId, ProductKind.SERVICE, name, description, price,
                null, null, duration);
    }
}
