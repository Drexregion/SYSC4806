package ca.carletonu.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class Person {
    private String name;
    private String email;
    private String password;
    private String phone;
    @Id
    @GeneratedValue
    private Long id;
    public Person(String name, String email, String password, String phone)
    {
        this.name = name;
        this.email = email;
        this.password = password;
        this.phone = phone;
    }

    public Person(){}

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public Long  getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }
    public void setPhone(String phone) {
        this.phone = phone;
    }
}
