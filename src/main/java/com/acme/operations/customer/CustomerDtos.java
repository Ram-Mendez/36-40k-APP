package com.acme.operations.customer;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;

// These request and response records carry data between the API and the service.
public final class CustomerDtos {
    // This class groups related types or utilities and does not need an instance.
    private CustomerDtos() {}

    // Customer entry; restrictions are evaluated when receiving a body with @Valid.
    public record Input(
            // Mandatory name limited to the size admitted.
            @NotBlank @Size(max = 200) String name,
            // The tax identifier must meet the pattern, it is not enough for it to be a text.
            @NotBlank @Pattern(regexp = "[A-Za-z0-9<>-]{5,32}") String taxId,
            // It requires a non-empty mail, with email format and limited length.
            @NotBlank @Email @Size(max = 254) String email,
            // Mandatory address with a maximum of 500 characters.
            @NotBlank @Size(max = 500) String address,
            // The version detects concurrent edits. A new record may omit it.
            @PositiveOrZero @Schema(description = "Current record version used to detect concurrent changes.") Long version) {}

    // Public customer's DTO; it avoids directly returning the JPA entity.
    public record View(
            // Unique ID of this record.
            long id,
            // Description name included in the response view.
            String name,
            // Customer's tax identifier.
            String taxId,
            // Customer contact mail.
            String email,
            // Customer address.
            String address,
            // It indicates whether the appeal is still enabled without having erased your history.
            boolean active,
            // Version read by the caller. It is used to detect later changes.
            @Schema(description = "Current record version used to detect concurrent changes.") long version) {
        // Copy the entity already obtained to its API representation.
        public static View of(Customer entity) {
            // The geters read values of the object; this builder does not create another client at the base.
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
