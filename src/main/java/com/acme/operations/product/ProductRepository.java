package com.acme.operations.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// Spring Data provides CRUD operations through JpaRepository and filtered queries through
// JpaSpecificationExecutor.
public interface ProductRepository
        extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {}
