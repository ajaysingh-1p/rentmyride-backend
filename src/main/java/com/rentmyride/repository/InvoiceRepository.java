package com.rentmyride.repository;

import com.rentmyride.entities.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // Issue #24/#25 fixes — same pattern as ReservationRepository.
    @EntityGraph(attributePaths = {"rental", "customer", "car"})
    @Query("SELECT i FROM Invoice i")
    List<Invoice> findAllWithDetails();

    @EntityGraph(attributePaths = {"rental", "customer", "car"})
    @Query(value = "SELECT i FROM Invoice i", countQuery = "SELECT COUNT(i) FROM Invoice i")
    Page<Invoice> findAllWithDetails(Pageable pageable);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    Optional<Invoice> findByRental_RentalId(Long rentalId);
    List<Invoice> findByCustomer_CustomerId(Long customerId);
    List<Invoice> findByInvoiceStatus(Invoice.InvoiceStatus status);
    List<Invoice> findByCar_CarId(Long carId);

    @Query("SELECT SUM(i.grandTotal) FROM Invoice i WHERE i.invoiceStatus = 'PAID'")
    Double getTotalInvoiceRevenue();

    @Query("SELECT i FROM Invoice i WHERE i.customer.customerId = :customerId ORDER BY i.invoiceDate DESC")
    List<Invoice> findCustomerInvoicesSorted(@Param("customerId") Long customerId);

    boolean existsByInvoiceNumber(String invoiceNumber);

    long countByInvoiceStatus(Invoice.InvoiceStatus status);
}
