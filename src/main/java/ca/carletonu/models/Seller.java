package ca.carletonu.models;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Seller extends Person{
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "seller")
    private List<Shop> shops = new ArrayList<>();
    public Seller(String name, String email, String password, String phone){
        super(name, email, password, phone);
    }

    public Seller(){}

    public List<Shop> getShops()
    {
        return shops;
    }
    public void addShop(Shop shop)
    {
        shops.add(shop);
        shop.setSeller(this);
    }

    public void removeShop(Shop shop)
    {
        shops.remove(shop);
        shop.setSeller(null);
    }
}
