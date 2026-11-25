package ca.carletonu.models;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * What a customer intends to buy, before checkout.
 *
 * A cart belongs to one customer. A customer can have many carts over time,
 * but at most one OPEN cart at once; the rest are checked out. That rule is
 * held by the database as well as by this class, see {@link #openForCustomerId}.
 */
@Entity
public class Cart {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    private CartStatus status = CartStatus.OPEN;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    /**
     * The customer's id while this cart is open, null once it is checked out.
     *
     * The column is unique, and both H2 and PostgreSQL allow any number of
     * nulls in a unique column. So any number of checked-out carts can share a
     * customer, but a second open cart for the same customer is refused by the
     * database, whichever path tried to write it. It is derived from status and
     * customer before every write and never set directly.
     */
    @Column(name = "open_for_customer_id", unique = true)
    private Long openForCustomerId;

    protected Cart() {
    }

    public Cart(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("A cart needs a customer.");
        }
        this.customer = customer;
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public CartStatus getStatus() {
        return status;
    }

    public boolean isOpen() {
        return status == CartStatus.OPEN;
    }

    /** The lines of the cart. Read only: change them through the cart's methods. */
    public List<CartItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    /**
     * Adds a quantity of a product. Adding a product already in the cart
     * raises that line's quantity instead of adding a second line.
     *
     * @return the line that now holds the product
     * @throws IllegalArgumentException if the quantity is zero or less
     * @throws IllegalStateException    if the cart is checked out
     */
    public CartItem addProduct(Product product, int quantity) {
        requireOpen();
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        Optional<CartItem> existing = itemFor(product);
        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(item.getQuantity() + quantity);
            return item;
        }

        CartItem item = new CartItem(this, product, quantity);
        items.add(item);
        return item;
    }

    /**
     * Sets how many of a product the cart holds. Zero removes the line.
     *
     * @throws IllegalArgumentException if the quantity is negative, or the
     *                                  product is not in the cart
     * @throws IllegalStateException    if the cart is checked out
     */
    public void setQuantity(Product product, int quantity) {
        requireOpen();
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative.");
        }

        CartItem item = itemFor(product)
                .orElseThrow(() -> new IllegalArgumentException("That product is not in the cart."));

        if (quantity == 0) {
            items.remove(item);
        } else {
            item.setQuantity(quantity);
        }
    }

    /**
     * Takes a product out of the cart entirely.
     *
     * @return false if the product was not in the cart
     * @throws IllegalStateException if the cart is checked out
     */
    public boolean removeProduct(Product product) {
        requireOpen();
        return itemFor(product).map(items::remove).orElse(false);
    }

    /** Total units across all lines. */
    public int getItemCount() {
        return items.stream().mapToInt(CartItem::getQuantity).sum();
    }

    /** Sum of every line's subtotal. */
    public double getTotal() {
        return items.stream().mapToDouble(CartItem::getSubtotal).sum();
    }

    /**
     * Closes the cart, so its contents can no longer change and the customer
     * can open a new one. Payment and stock are checkout's concern, not this.
     *
     * @throws IllegalStateException if the cart is already checked out, or empty
     */
    public void checkOut() {
        requireOpen();
        if (items.isEmpty()) {
            throw new IllegalStateException("An empty cart cannot be checked out.");
        }
        status = CartStatus.CHECKED_OUT;
    }

    @PrePersist
    @PreUpdate
    void syncOpenForCustomerId() {
        openForCustomerId = isOpen() && customer != null ? customer.getId() : null;
    }

    /**
     * Matches by id once the product is saved, and by identity before that,
     * so the same product loaded twice still counts as one line.
     */
    private Optional<CartItem> itemFor(Product product) {
        return items.stream()
                .filter(item -> sameProduct(item.getProduct(), product))
                .findFirst();
    }

    private static boolean sameProduct(Product a, Product b) {
        if (a == b) {
            return true;
        }
        return a != null && b != null && a.getId() != null && a.getId().equals(b.getId());
    }

    private void requireOpen() {
        if (!isOpen()) {
            throw new IllegalStateException("This cart has been checked out and can no longer change.");
        }
    }
}
