package ca.carletonu.events;

import java.util.List;

/**
 * Published when a customer checks out their cart.
 *
 * Keyed by customerId on the checkout-completed topic, so one shopper's
 * orders land on the same partition and are read in the order they happened.
 *
 * Each line carries the product's name and the price paid, so a read model
 * built from this event can show an order without going back to the write
 * tables.
 */
public record CheckoutCompletedEvent(
        Long cartId,
        Long customerId,
        List<Line> lines,
        double total
) {

    public record Line(
            Long productId,
            String productName,
            Long shopId,
            int quantity,
            Double unitPrice
    ) {
    }
}
