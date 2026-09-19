package com.rentmyride.controller;

import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.util.BiharLocations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    // All Bihar locations a customer can pick as pickup/drop point
    @GetMapping("/bihar")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getBiharLocations() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Bihar locations.", BiharLocations.ALL));
    }
}
