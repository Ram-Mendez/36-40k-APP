package com.acme.operations.product;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "product")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private boolean active = true;

    @Version private long version;

    protected Product() {}

    public Product(ProductDtos.Input input) {
        update(input);
    }

    public void update(ProductDtos.Input input) {
        this.sku = input.sku().strip().toUpperCase(java.util.Locale.ROOT);
        this.name = input.name().strip();
        this.unitPrice = input.unitPrice().setScale(2);
    }

    public void deactivate() {
        active = false;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
