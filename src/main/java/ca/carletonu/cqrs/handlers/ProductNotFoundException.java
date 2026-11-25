package ca.carletonu.cqrs.handlers;

/**
 * Thrown when a command names a product that does not exist.
 *
 * Kept apart from IllegalArgumentException so the controller can answer with a
 * 404, instead of a form error on a page for a product that is not there.
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long productId) {
        super("No product with id " + productId);
    }
}
