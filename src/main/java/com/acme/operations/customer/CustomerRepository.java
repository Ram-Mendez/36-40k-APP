package com.acme.operations.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// Spring Data genera la implementación: JpaRepository aporta CRUD y JpaSpecificationExecutor búsquedas con
// filtros.
public interface CustomerRepository
        extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {}
