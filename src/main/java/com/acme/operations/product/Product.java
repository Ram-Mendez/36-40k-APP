package com.acme.operations.product;

import jakarta.persistence.*;

import java.math.BigDecimal;

// JPA trata esta clase como una entidad persistente y reconstruye objetos desde sus filas.
@Entity
// Indica la tabla que mapea JPA; su estructura se mantiene mediante las migraciones.
@Table(name = "product")
// Entidad del catálogo: JPA vincula sus campos con una fila de product.
public class Product {
    // Marca la clave primaria que JPA utiliza para identificar esta entidad.
    @Id
    // La base genera el id al insertar; JPA recoge el valor, no lo envía el cliente.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // Identificador del producto generado al insertar; todavía puede ser null antes del alta.
    private Long id;

    // Describe el mapeo y límites de la columna; no sustituye la validación del DTO ni las restricciones SQL.
    @Column(nullable = false, length = 64)
    // Código de catálogo que identifica el producto de forma única según la restricción de la tabla.
    private String sku;

    // Describe el mapeo y límites de la columna; no sustituye la validación del DTO ni las restricciones SQL.
    @Column(nullable = false, length = 200)
    // Nombre descriptivo que se muestra al consultar el producto.
    private String name;

    // Describe el mapeo y límites de la columna; no sustituye la validación del DTO ni las restricciones SQL.
    @Column(nullable = false, precision = 14, scale = 2)
    // Precio monetario con dos decimales; BigDecimal evita errores de coma flotante.
    private BigDecimal unitPrice;

    // Describe el mapeo y límites de la columna; no sustituye la validación del DTO ni las restricciones SQL.
    @Column(nullable = false)
    // Un producto nuevo empieza activo; desactivarlo no lo elimina.
    private boolean active = true;

    // JPA comprueba e incrementa esta versión al actualizar para detectar escrituras concurrentes.
    @Version private long version;

    // JPA necesita este constructor sin parámetros para reconstruir entidades desde la base.
    protected Product() {}

    // Construye el producto con la entrada validada; no lo guarda por sí solo.
    public Product(ProductDtos.Input input) {
        // Reutiliza las mismas reglas de normalización que una actualización.
        update(input);
    }

    // Aplica los datos editables; no permite cambiar id, active ni version desde el DTO.
    public void update(ProductDtos.Input input) {
        // Normaliza SKU quitando espacios exteriores y usando mayúsculas independientes del idioma.
        this.sku = input.sku().strip().toUpperCase(java.util.Locale.ROOT);
        // Quita espacios exteriores al nombre, conservando su contenido.
        this.name = input.name().strip();
        // Fija dos decimales; no aplica un redondeo silencioso si el valor no cabe exactamente.
        this.unitPrice = input.unitPrice().setScale(2);
    }

    // Marca una baja lógica para conservar referencias de pedidos y movimientos.
    public void deactivate() {
        // JPA persistirá este cambio al sincronizar la entidad gestionada en una transacción.
        active = false;
    }

    // Expone el valor de la entidad en memoria; este getter no modifica ni guarda datos.
    public Long getId() {
        // Entrega el valor almacenado en este objeto al código que lo solicitó.
        return id;
    }

    // Expone el valor de la entidad en memoria; este getter no modifica ni guarda datos.
    public String getSku() {
        // Entrega el valor almacenado en este objeto al código que lo solicitó.
        return sku;
    }

    // Expone el valor de la entidad en memoria; este getter no modifica ni guarda datos.
    public String getName() {
        // Entrega el valor almacenado en este objeto al código que lo solicitó.
        return name;
    }

    // Expone el valor de la entidad en memoria; este getter no modifica ni guarda datos.
    public BigDecimal getUnitPrice() {
        // Entrega el valor almacenado en este objeto al código que lo solicitó.
        return unitPrice;
    }

    // Expone el valor de la entidad en memoria; este getter no modifica ni guarda datos.
    public boolean isActive() {
        // Entrega el valor almacenado en este objeto al código que lo solicitó.
        return active;
    }

    // Expone el valor de la entidad en memoria; este getter no modifica ni guarda datos.
    public long getVersion() {
        // Entrega el valor almacenado en este objeto al código que lo solicitó.
        return version;
    }
}
