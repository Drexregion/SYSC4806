package ca.carletonu.cqrs.handlers;

/**
 * What a successful checkout bought.
 *
 * Worked out inside the handler's transaction, so the caller does not need to
 * walk the cart's lines, which load lazily, after the transaction has ended.
 */
public record CheckoutResult(
        Long cartId,
        int itemCount,
        double total
) {
}
