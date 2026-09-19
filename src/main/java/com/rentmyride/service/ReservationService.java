package com.rentmyride.service;

import com.rentmyride.dtos.ReservationDTO;
import com.rentmyride.entities.Reservation;
import java.util.List;

public interface ReservationService {
    ReservationDTO createReservation(Long customerId, ReservationDTO.CreateRequest request);
    // New feature: "create only after payment" — see ReservationServiceImpl for how this
    // guarantees the reservation is never visible to anyone else until it's already paid.
    ReservationDTO createReservationWithPayment(Long customerId, ReservationDTO.CreateRequest request,
            com.rentmyride.entities.Reservation.PaymentType paymentType, Double amount, String razorpayPaymentId);
    ReservationDTO.EstimateResponse estimatePrice(ReservationDTO.EstimateRequest request);

    /**
     * Same as estimatePrice(request), but resolves the loyalty-tier discount for THIS customer.
     * PaymentServiceImpl.createNewBookingOrder() uses this so the Razorpay order amount and the
     * total the booking page displays come out of one single calculation instead of two.
     */
    ReservationDTO.EstimateResponse estimatePrice(ReservationDTO.EstimateRequest request, Long customerId);
    ReservationDTO payForReservation(Long reservationId, ReservationDTO.PayRequest request);
    ReservationDTO.RescheduleResponse rescheduleReservation(Long reservationId, ReservationDTO.RescheduleRequest request);
    ReservationDTO getReservationById(Long reservationId);
    List<ReservationDTO> getAllReservations();
    com.rentmyride.dtos.PageResponse<ReservationDTO> getAllReservationsPaged(int page, int size);
    List<ReservationDTO> getReservationsByCustomer(Long customerId);
    List<ReservationDTO> getReservationsByStatus(Reservation.ReservationStatus status);
    ReservationDTO updateReservationStatus(Long reservationId, ReservationDTO.StatusUpdateRequest request);
    ReservationDTO.CancelResponse cancelReservation(Long reservationId);
    long countByStatus(Reservation.ReservationStatus status);
    List<ReservationDTO.BookedRange> getBookedDateRanges(Long carId);

    // Admin assigns a driver — notifies + emails the customer with driver & car details
    ReservationDTO assignDriver(Long reservationId, Long driverId);
    // New feature: Driver Trip Accept/Reject — driver confirms/declines an admin-made assignment
    // before the customer is told a specific driver is locked in.
    ReservationDTO acceptTripAssignment(Long reservationId, Long driverId);
    ReservationDTO rejectTripAssignment(Long reservationId, Long driverId, String reason);
    List<ReservationDTO> getPendingTripRequestsForDriver(Long driverId);

    // Driver's own pickup-form dropdown — only their assigned, not-yet-picked-up reservations
    List<ReservationDTO> getPendingPickupsForDriver(Long driverId);

    // New — "you have a due amount" login popup.
    List<ReservationDTO> getDueBalanceReservations(Long customerId);
}
