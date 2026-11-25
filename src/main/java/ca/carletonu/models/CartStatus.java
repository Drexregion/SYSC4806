package ca.carletonu.models;

/**
 * Where a cart is in its life. A customer fills an OPEN cart; checkout moves
 * it to CHECKED_OUT, after which it is history and a new cart can be opened.
 */
public enum CartStatus {
    OPEN,
    CHECKED_OUT
}
