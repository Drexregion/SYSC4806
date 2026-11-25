package ca.carletonu.cqrs.commands;

/**
 * Check out a customer's open cart.
 *
 * Only the customer is named: a customer has at most one open cart, so that
 * is the cart being checked out.
 */
public record CheckoutCommand(
        Long customerId
) {
}
