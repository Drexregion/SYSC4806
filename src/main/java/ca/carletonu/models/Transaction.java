package ca.carletonu.models;

import jakarta.persistence.*;

import java.util.Date;

@Entity
public class Transaction {
    @ManyToOne
    @JoinColumn(name = "shop_id")
    private Shop shop;
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;
    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;
    private Date date;
    @GeneratedValue
    @Id
    private Long id;

    /**
     * The checked-out cart this purchase came from, so one order's rows can be
     * found together. Null for rows not written by checkout.
     */
    @Column(name = "cart_id")
    private Long cartId;

    /**
     * How many were bought. A wrapper, not an int: an int maps to a NOT NULL
     * column, which Postgres refuses to add to a table that already has rows
     * when the schema is updated in place on Azure.
     */
    private Integer quantity;

    /** The price paid for one, kept so a later price change does not rewrite history. */
    private Double unitPrice;

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Shop getShop() {
        return shop;
    }

    public void setShop(Shop shop) {
        this.shop = shop;
    }

    public Transaction(Shop shop, Product product, Customer customer) {
        this.shop = shop;
        this.product = product;
        this.customer = customer;
    }

    /**
     * A purchase made at checkout: one cart line, recorded when it was bought.
     */
    public Transaction(Long cartId, Shop shop, Product product, Customer customer,
                       int quantity, Double unitPrice) {
        this(shop, product, customer);
        this.cartId = cartId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.date = new Date();
    }

    public Transaction() {}

    /**
     * Checkout does not use this. It removes the product from the shop's
     * product list, which would delist the product after one sale, and it
     * does not touch stock. Checkout builds its Transactions directly.
     */
    public boolean buy(Product product) {
        if (this.shop.getProducts().contains(product)) {
            this.shop.getProducts().remove(product);
            // add some dummy payment logic
            date = new Date();
            return true;
        }
        return false;
    }

    public Long getId() {
        return id;
    }

    /**
     * When the purchase went through, or null if buy() has not succeeded yet.
     */
    public Date getDate() {
        return date;
    }

    public Long getCartId() {
        return cartId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Double getUnitPrice() {
        return unitPrice;
    }

    /** Unit price times quantity, or zero when either is missing, like CartItem. */
    public double getSubtotal() {
        return unitPrice == null || quantity == null ? 0 : unitPrice * quantity;
    }

//    public boolean refund(Product product) {
//        if (this.shop.getProducts().contains(product)) {
//            this.shop.getProducts().remove(product);
//            // add some dummy payment logic
//            this.customer.getTransactionHistory().add("Bought "
//                    + product.getName() + "from " + shop.getName() + "for " + product.getPrice());
//            return true;
//        }
//        return false;
//    }
}
