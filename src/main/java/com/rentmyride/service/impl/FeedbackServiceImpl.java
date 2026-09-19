package com.rentmyride.service.impl;

import com.rentmyride.custom_exceptions.CustomerNotFoundException;
import com.rentmyride.custom_exceptions.RentalNotFoundException;
import com.rentmyride.custom_exceptions.UnauthorizedAccessException;
import com.rentmyride.dtos.FeedbackDTO;
import com.rentmyride.entities.Customer;
import com.rentmyride.entities.Feedback;
import com.rentmyride.entities.Rental;
import com.rentmyride.repository.CustomerRepository;
import com.rentmyride.repository.FeedbackRepository;
import com.rentmyride.repository.RentalRepository;
import com.rentmyride.service.CustomerService;
import com.rentmyride.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final RentalRepository rentalRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;

    @Override
    @Transactional
    public FeedbackDTO.Response submitFeedback(Long customerId, FeedbackDTO.SubmitRequest request) {
        // IDOR fix: the "rental belongs to customerId" check below only protects the rental if the
        // attacker doesn't know the real owner's customerId — this confirms the CALLER is that customer.
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        Rental rental = rentalRepository.findById(request.getRentalId())
                .orElseThrow(() -> new RentalNotFoundException(request.getRentalId()));

        // A customer can only give feedback for their own rental
        if (!rental.getCustomer().getCustomerId().equals(customerId)) {
            throw new UnauthorizedAccessException("This rental does not belong to you.");
        }

        if (feedbackRepository.existsByRental_RentalId(request.getRentalId())) {
            throw new UnauthorizedAccessException("Feedback has already been submitted for this rental.");
        }

        Feedback feedback = Feedback.builder()
                .rental(rental)
                .customer(customer)
                .carCondition(clamp(request.getCarCondition()))
                .staffBehavior(clamp(request.getStaffBehavior()))
                .valueForMoney(clamp(request.getValueForMoney()))
                .bookingProcess(clamp(request.getBookingProcess()))
                .overallService(clamp(request.getOverallService()))
                .driverRating(rental.getDriver() != null && request.getDriverRating() != null
                        ? clamp(request.getDriverRating()) : null)
                .comments(request.getComments())
                .build();

        customerService.adjustTrustScore(customerId, 1); // small reward for engaging/leaving feedback

        try {
            return mapToDTO(feedbackRepository.save(feedback));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Belt-and-suspenders for the check above: if two submissions for the same rental
            // landed at almost the exact same moment, the existsByRental_RentalId() check alone
            // wouldn't have caught the second one — the unique constraint on rental_id does.
            throw new UnauthorizedAccessException("Feedback has already been submitted for this rental.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackDTO.Response> getAllFeedback() {
        return feedbackRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackDTO.Response> getFeedbackForCustomer(Long customerId) {
        com.rentmyride.security.SecurityUtils.assertOwnsAsCustomer(customerId);
        return feedbackRepository.findByCustomer_CustomerIdOrderByCreatedAtDesc(customerId)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasFeedbackForRental(Long rentalId) {
        return feedbackRepository.existsByRental_RentalId(rentalId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, FeedbackDTO.CarRatingSummary> getRatingSummaryForAllCars() {
        Map<Long, FeedbackDTO.CarRatingSummary> result = new HashMap<>();
        for (Object[] row : feedbackRepository.findRatingSummaryPerCar()) {
            Long carId = (Long) row[0];
            Double avg = (Double) row[1];
            Long count = (Long) row[2];
            result.put(carId, FeedbackDTO.CarRatingSummary.builder()
                    .carId(carId)
                    .averageRating(Math.round(avg * 10) / 10.0)
                    .totalReviews(count)
                    .build());
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackDTO.Response> getFeedbackForCar(Long carId) {
        return feedbackRepository.findByRental_Car_CarIdOrderByCreatedAtDesc(carId)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    private int clamp(Integer value) {
        if (value == null) return 1;
        return Math.max(1, Math.min(5, value));
    }

    private FeedbackDTO.Response mapToDTO(Feedback f) {
        double avg = (f.getCarCondition() + f.getStaffBehavior() + f.getValueForMoney()
                + f.getBookingProcess() + f.getOverallService()) / 5.0;

        return FeedbackDTO.Response.builder()
                .feedbackId(f.getFeedbackId())
                .rentalId(f.getRental().getRentalId())
                .customerId(f.getCustomer().getCustomerId())
                .customerName(f.getCustomer().getFirstName() + " " + f.getCustomer().getLastName())
                .carLabel(f.getRental().getCar().getBrand() + " " + f.getRental().getCar().getModel())
                .carCondition(f.getCarCondition())
                .staffBehavior(f.getStaffBehavior())
                .valueForMoney(f.getValueForMoney())
                .bookingProcess(f.getBookingProcess())
                .overallService(f.getOverallService())
                .driverRating(f.getDriverRating())
                .averageRating(Math.round(avg * 10) / 10.0)
                .comments(f.getComments())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
