package ca.carletonu.models;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Shop {
    private String name;
    private String description;
    @Id
    @GeneratedValue
    private Long id;
    private String type;
    @ManyToOne
    private Seller seller;
    @ManyToMany
    private List<Customer> customers = new ArrayList<>();
    @OneToMany(cascade = CascadeType.ALL)
    private List<Product> products = new ArrayList<>();

    public Shop(String name, String description, String type, Seller seller) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.seller = seller;
    }

    public Shop(){}

    public Long getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public String getDescription() {
        return description;
    }
    public String getType() {
        return type;
    }
    public Seller getSeller() {
        return seller;
    }
    public List<Customer> getCustomers() {
        return customers;
    }
    public List<Product> getProducts() {
        return products;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public void setType(String type) {
        this.type = type;
    }
    public void setSeller(Seller seller) {
        this.seller = seller;
    }
    public void addCustomer(Customer customer){
        this.customers.add(customer);
    }
    public void addProduct(Product product){
        this.products.add(product);
    }
    public void removeCustomer(Customer customer){
        this.customers.remove(customer);
    }
    public void removeProduct(Product product){
        this.products.remove(product);
    }
}
