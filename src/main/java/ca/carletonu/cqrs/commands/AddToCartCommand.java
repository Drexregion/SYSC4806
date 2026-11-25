package ca.carletonu.cqrs.commands;

/**
 * Put a quantity of a product into a customer's open cart.
 *
 * The quantity is a wrapper so a missing value reaches the handler as null and
 * is refused there with a message, rather than failing somewhere earlier.
 */
public record AddToCartCommand(
        Long customerId,
        Long productId,
        Integer quantity
) {
}
