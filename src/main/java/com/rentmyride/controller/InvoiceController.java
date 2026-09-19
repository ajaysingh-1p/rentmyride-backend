package com.rentmyride.controller;

import com.rentmyride.dtos.AuthResponseDTO;
import com.rentmyride.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {
    private final InvoiceService invoiceService;

    @PostMapping("/generate/{rentalId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> generate(@PathVariable Long rentalId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Invoice generated.", invoiceService.generateInvoice(rentalId)));
    }

    @GetMapping("/{invoiceId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getById(@PathVariable Long invoiceId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Invoice fetched.", invoiceService.getInvoiceById(invoiceId)));
    }

    @GetMapping("/number/{invoiceNumber}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getByNumber(@PathVariable String invoiceNumber) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Invoice fetched.", invoiceService.getInvoiceByNumber(invoiceNumber)));
    }

    @GetMapping("/rental/{rentalId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER','DRIVER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getByRental(@PathVariable Long rentalId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Invoice by rental.", invoiceService.getInvoiceByRentalId(rentalId)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAll() {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("All invoices.", invoiceService.getAllInvoices()));
    }

    // Issue #25 — paginated version.
    @GetMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getAllPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Invoices page.",
                invoiceService.getAllInvoicesPaged(page, Math.min(size, 100))));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<AuthResponseDTO.ApiResponse> getByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(AuthResponseDTO.ApiResponse.success("Customer invoices.", invoiceService.getInvoicesByCustomer(customerId)));
    }

    @GetMapping("/{invoiceId}/download")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long invoiceId) {
        byte[] pdf = invoiceService.downloadInvoicePdf(invoiceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoice-" + invoiceId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
