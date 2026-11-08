package ca.carletonu.models;

import jakarta.persistence.Entity;

@Entity
public class Service extends Product{
    private int duration;
    public Service(String name, String description, Double price, int duration) {
        super(name, description, price);
        this.duration = duration;
    }
    public Service(){}

    public int getDuration() {
        return duration;
    }
    public void setDuration(int duration) {
        this.duration = duration;
    }
}
