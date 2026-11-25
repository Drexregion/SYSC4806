package ca.carletonu.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * One line of a cart: a product and how many of it the customer wants.
 *
 * The quantity is always greater than zero. Taking a line down to nothing
 * means removing it from the cart, not keeping a line of zero.
 */
@Entity
public class CartItem {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cart_id")
    private Cart cart;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    private int quantity;

    protected CartItem() {
    }

    /**
     * Package private: lines are made through {@link Cart#addProduct}, so a
     * line always belongs to the cart that holds it.
     */
    CartItem(Cart cart, Product product, int quantity) {
        if (product == null) {
            throw new IllegalArgumentException("A cart item needs a product.");
        }
        this.cart = cart;
        this.product = product;
        setQuantity(quantity);
    }

    public Long getId() {
        return id;
    }

    public Cart getCart() {
        return cart;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    /**
     * @throws IllegalArgumentException if the quantity is zero or less
     */
    public void setQuantity(int quantity) {
        requirePositive(quantity);
        this.quantity = quantity;
    }

    /** Price times quantity, or zero for a product with no price set. */
    public double getSubtotal() {
        return product.getPrice() == null ? 0 : product.getPrice() * quantity;
    }

    /**
     * Checked again on the way to the database, so a row with a quantity of
     * zero cannot be written even by a path that skipped the setter, such as
     * a REST client posting JSON.
     */
    @PrePersist
    @PreUpdate
    void checkQuantity() {
        requirePositive(quantity);
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
    }
}
