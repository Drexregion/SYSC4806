package ca.carletonu.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class Item extends Product{
    private int quantity;
    private String productType;

    public Item(String name, String description, Double price, int quantity,  String productType) {
        super(name, description, price);
        this.quantity = quantity;
        this.productType = productType;
    }
    public Item(){}
    public int getQuantity() {
        return quantity;
    }
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
    public String getProductType() {
        return productType;
    }
    public void setProductType(String productType) {
        this.productType = productType;
    }
}
