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

    public Transaction() {}

    public boolean buy(Product product) {
        if (this.shop.getProducts().contains(product)) {
            this.shop.getProducts().remove(product);
            // add some dummy payment logic
            date = new Date();
            this.customer.getTransactionHistory().add("Bought "
                    + product.getName() + " from " + shop.getName() + " for " + product.getPrice()
                    + " at " + date.toString());
            return true;
        }
        return false;
    }

    public Long getId() {
        return id;
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
