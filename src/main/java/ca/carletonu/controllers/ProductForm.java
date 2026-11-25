package ca.carletonu.controllers;

import ca.carletonu.events.ProductKind;

/**
 * What the add-product form submits. One form covers both kinds of product,
 * so it holds the fields of both; the kind decides which ones are used.
 *
 * The numbers are wrappers so a field left empty binds to null (and the
 * handler can say which one is missing) instead of failing to bind.
 */
public class ProductForm {

    private ProductKind kind = ProductKind.ITEM;
    private String name;
    private String description;
    private Double price;
    private Integer quantity;
    private String productType;
    private Integer duration;

    public ProductKind getKind() {
        return kind;
    }

    public void setKind(ProductKind kind) {
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }
}
