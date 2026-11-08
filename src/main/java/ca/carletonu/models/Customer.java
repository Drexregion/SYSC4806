package ca.carletonu.models;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Customer extends Person{
//    @ManyToMany
//    private List<Shop> favoriteShops = new ArrayList<>();
    private String address;
    @ElementCollection
    private List<String> transactionHistory = new ArrayList<>();
    public Customer(String name, String email, String password, String phone,  String address)
    {
        super(name, email, password, phone);
        this.address = address;
    }

    public Customer(){}

//    public List<Shop> getFavoriteShops() {
//        return favoriteShops;
//    }
//    public void addShop(Shop shop)
//    {
//        favoriteShops.add(shop);
//    }
//
//    public void removeShop(Shop shop)
//    {
//        favoriteShops.remove(shop);
//    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public List<String> getTransactionHistory() {
        return transactionHistory;
    }
}
