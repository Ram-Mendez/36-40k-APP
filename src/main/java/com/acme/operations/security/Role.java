package com.acme.operations.security;

// Closed set of functional permissions; Spring expresses them as ROLE authorities _...
public enum Role {
    // User management and access to business operations.
    ADMIN,
    // Catalogue, inventory and order operations without account management.
    OPERATIONS,
    // Access to queries allowed without changing data.
    READONLY
}
