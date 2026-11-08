package com.acme.operations.customer;

import jakarta.persistence.*;

// JPA trata esta clase como una entidad persistente y reconstruye objetos desde sus filas.
@Entity
// Indica la tabla que mapea JPA; su estructura se mantiene mediante las migraciones.
@Table(name = "customer")
// Entidad del cliente al que pertenecen los pedidos; no es el usuario que inicia sesión.
public class Customer {
    // Marca la clave primaria que JPA utiliza para identificar esta entidad.
    @Id
    // La base genera el id al insertar; JPA recoge el valor, no lo envía el cliente.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // Clave primaria generada por la base al insertar el cliente.
    private Long id;

    // Describe el mapeo y límites de la columna; no sustituye la validación del DTO ni las restricciones SQL.
    @Column(nullable = false, length = 200)
    // Nombre que se muestra también al consultar sus pedidos.
    private String name;

    // Describe el mapeo y límites de la columna; no sustituye la validación del DTO ni las restricciones SQL.
    @Column(nullable = false, length = 32)
    // Identificador fiscal del cliente, con unicidad definida en la base de datos.
    private String taxId;

    // Describe el mapeo y límites de la columna; no sustituye la validación del DTO ni las restricciones SQL.
    @Column(nullable = false, length = 254)
    // Correo de contacto; el formato se valida en el DTO de entrada.
    private String email;

    // Describe el mapeo y límites de la columna; no sustituye la validación del DTO ni las restricciones SQL.
    @Column(nullable = false, length = 500)
    // Dirección de contacto del cliente.
    private String address;

    // Describe el mapeo y límites de la columna; no sustituye la validación del DTO ni las restricciones SQL.
    @Column(nullable = false)
    // La baja es lógica: active=false conserva las relaciones con pedidos existentes.
    private boolean active = true;

    // JPA comprueba e incrementa esta versión al actualizar para detectar escrituras concurrentes.
    @Version private long version;

    // JPA utiliza el constructor vacío al reconstruir una fila como objeto.
    protected Customer() {}

    // Inicializa los campos editables desde el DTO recibido; no ejecuta un INSERT.
    public Customer(CustomerDtos.Input input) {
        // Reutiliza la normalización aplicada también al editar.
        update(input);
    }

    // Modifica datos de contacto; id y version permanecen bajo control de persistencia.
    public void update(CustomerDtos.Input input) {
        // Elimina espacios exteriores del nombre.
        this.name = input.name().strip();
        // Normaliza el identificador fiscal para comparar valores de forma consistente.
        this.taxId = input.taxId().strip().toUpperCase(java.util.Locale.ROOT);
        // Limpia espacios exteriores del correo y la dirección.
        this.email = input.email().strip();
        this.address = input.address().strip();
    }

    // Desactiva el cliente conservando su fila y los pedidos que lo referencian.
    public void deactivate() {
        // La entidad gestionada guardará el cambio al sincronizarse en la transacción.
        active = false;
    }

    // Expone el valor de la entidad en memoria; este getter no modifica ni guarda datos.
    public Long getId() {
        // Entrega el valor almacenado en este objeto al código que lo solicitó.
        return id;
    }

    // Expone el valor de la entidad en memoria; este getter no modifica ni guarda datos.
    public String getName() {
        // Entrega el valor almacenado en este objeto al código que lo solicitó.
        return name;
    }

    // Expone el valor de la entidad en memoria; este getter no modifica ni guarda datos.
    public String getTaxId() {
        // Entrega el valor almacenado en este objeto al código que lo solicitó.
        return taxId;
    }

    // Expone el valor de la entidad en memoria; este getter no modifica ni guarda datos.
    public String getEmail() {
        // Entrega el valor almacenado en este objeto al código que lo solicitó.
        return email;
    }

    // Expone el valor de la entidad en memoria; este getter no modifica ni guarda datos.
    public String getAddress() {
        // Entrega el valor almacenado en este objeto al código que lo solicitó.
        return address;
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
