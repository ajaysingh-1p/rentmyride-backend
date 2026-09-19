package com.rentmyride.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedAddressDTO {

    private Long savedAddressId;
    private Long customerId;
    private String label;
    private String address;
    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaveRequest {
        @NotBlank(message = "Label is required.")
        private String label;

        @NotBlank(message = "Address is required.")
        private String address;
    }
}
