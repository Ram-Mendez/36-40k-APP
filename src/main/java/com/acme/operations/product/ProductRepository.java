package com.acme.operations.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// Spring Data genera la implementación: JpaRepository aporta CRUD y JpaSpecificationExecutor búsquedas con
// filtros.
public interface ProductRepository
        extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {}
