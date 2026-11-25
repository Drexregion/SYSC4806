package ca.carletonu.events;

/**
 * Published when a customer adds a product to their cart.
 *
 * Keyed by customerId on the cart-item-added topic, so one shopper's adds land
 * on the same partition and are read in the order they happened.
 *
 * quantityAdded is what this add put in; lineQuantity is what the cart line
 * holds afterwards, so a reader does not need the cart to know the total.
 */
public record CartItemAddedEvent(
        Long cartId,
        Long customerId,
        Long productId,
        int quantityAdded,
        int lineQuantity
) {
}
