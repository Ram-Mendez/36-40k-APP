package com.acme.operations.customer;

import jakarta.validation.constraints.*;

public final class CustomerDtos {
    private CustomerDtos() {}

    public record Input(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{5,32}") String taxId,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 500) String address,
            @PositiveOrZero Long version) {}

    public record View(
            long id,
            String name,
            String taxId,
            String email,
            String address,
            boolean active,
            long version) {
        public static View of(Customer entity) {
            return new View(
                    entity.getId(),
                    entity.getName(),
                    entity.getTaxId(),
                    entity.getEmail(),
                    entity.getAddress(),
                    entity.isActive(),
                    entity.getVersion());
        }
    }
}
