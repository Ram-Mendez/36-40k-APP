package com.acme.operations.product;

import jakarta.persistence.*;

import java.math.BigDecimal;

// JPA maps rows from the database to instances of this entity.
@Entity
// Map the entity to the table managed by database migrations.
@Table(name = "product")
// Catalogue entity: JPA links its fields to a product row.
public class Product {
    // Identify the primary key used by JPA.
    @Id
    // The database generates the ID on insert. JPA reads the generated value.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // The database assigns the product ID on insert. It can be null before the product is saved.
    private Long id;

    // Map this field to a database column. The DTO and the database also validate input.
    @Column(nullable = false, length = 64)
    // Product SKU.
    private String sku;

    // Map this field to a database column. The DTO and the database also validate input.
    @Column(nullable = false, length = 200)
    // Descriptive name shown when looking at the product.
    private String name;

    // Map this field to a database column. The DTO and the database also validate input.
    @Column(nullable = false, precision = 14, scale = 2)
    // Monetary price with two decimals; BigDecimal avoids floating coma errors.
    private BigDecimal unitPrice;

    // Map this field to a database column. The DTO and the database also validate input.
    @Column(nullable = false)
    // A new product starts active; disable it does not eliminate it.
    private boolean active = true;

    // JPA checks and increments the version on update to detect concurrent changes.
    @Version private long version;

    // JPA needs this non-parameter builder to rebuild entities from the base.
    protected Product() {}

    // Build the product with the validated input; it does not keep it on its own.
    public Product(ProductDtos.Input input) {
        // It reuses the same standards of normalization as an update.
        update(input);
    }

    // It applies the editable data; it does not allow to change id, active or version from the DTO.
    public void update(ProductDtos.Input input) {
        // It normalizes SKU by removing outer spaces and using language-independent capital letters.
        this.sku = input.sku().strip().toUpperCase(java.util.Locale.ROOT);
        // Remove outer spaces from the name, keeping its content.
        this.name = input.name().strip();
        // Set two decimals; do not apply a silent rounding if the value does not exactly fit.
        this.unitPrice = input.unitPrice().setScale(2);
    }

    // Deactivate the record to keep references from orders and stock movements valid.
    public void deactivate() {
        // JPA will continue this change by synchronizing the entity managed in a transaction.
        active = false;
    }

    // Return the current field value from the entity in memory.
    public Long getId() {
        // Return the current value.
        return id;
    }

    // Return the current field value from the entity in memory.
    public String getSku() {
        // Return the current value.
        return sku;
    }

    // Return the current field value from the entity in memory.
    public String getName() {
        // Return the current value.
        return name;
    }

    // Return the current field value from the entity in memory.
    public BigDecimal getUnitPrice() {
        // Return the current value.
        return unitPrice;
    }

    // Return the current field value from the entity in memory.
    public boolean isActive() {
        // Return the current value.
        return active;
    }

    // Return the current field value from the entity in memory.
    public long getVersion() {
        // Return the current value.
        return version;
    }
}
