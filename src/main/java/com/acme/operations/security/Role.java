package com.acme.operations.security;

// Conjunto cerrado de permisos funcionales; Spring los expresa como autoridades ROLE_...
public enum Role {
    // Administración de usuarios y acceso a operaciones de negocio.
    ADMIN,
    // Operaciones de catálogo, inventario y pedidos sin administrar cuentas.
    OPERATIONS,
    // Acceso a consultas permitidas sin modificar datos.
    READONLY
}
