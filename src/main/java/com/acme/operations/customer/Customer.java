package com.acme.operations.customer;

import jakarta.persistence.*;

@Entity
@Table(name = "customer")
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 32)
    private String taxId;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(nullable = false)
    private boolean active = true;

    @Version private long version;

    protected Customer() {}

    public Customer(CustomerDtos.Input input) {
        update(input);
    }

    public void update(CustomerDtos.Input input) {
        this.name = input.name().strip();
        this.taxId = input.taxId().strip().toUpperCase(java.util.Locale.ROOT);
        this.email = input.email().strip();
        this.address = input.address().strip();
    }

    public void deactivate() {
        active = false;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTaxId() {
        return taxId;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
