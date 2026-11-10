CREATE TABLE replenishment_policy (
    product_id bigint PRIMARY KEY REFERENCES product(id),
    minimum_stock bigint NOT NULL CHECK (minimum_stock BETWEEN 0 AND 1000000000),
    target_stock bigint NOT NULL CHECK (target_stock BETWEEN 0 AND 1000000000),
    preferred_supplier_id bigint REFERENCES supplier(id),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (target_stock >= minimum_stock)
);
CREATE INDEX ix_replenishment_supplier_product
    ON replenishment_policy(preferred_supplier_id, product_id);
