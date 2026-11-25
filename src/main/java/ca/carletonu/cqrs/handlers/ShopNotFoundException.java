package ca.carletonu.cqrs.handlers;

/**
 * Thrown when a command names a shop that does not exist.
 *
 * Kept apart from IllegalArgumentException so the controller can answer with a
 * 404 instead of a form error on a page that is not there.
 */
public class ShopNotFoundException extends RuntimeException {

    public ShopNotFoundException(Long shopId) {
        super("No shop with id " + shopId);
    }
}
