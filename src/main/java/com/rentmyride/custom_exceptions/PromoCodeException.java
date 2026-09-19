package com.rentmyride.custom_exceptions;

// Issue #45 fix: applyAndConsume() used to compute a specific, useful failure reason in
// checkEligibility() (expired / invalid / below minimum amount) and then just throw it away,
// returning a flat 0.0 — the reservation was created successfully at full price with nothing
// telling the customer their code didn't apply or why. Throwing this (with the real reason)
// instead means a customer who explicitly typed a promo code either gets the discount or gets a
// clear rejection — never a silent full-price charge they didn't expect.
public class PromoCodeException extends RuntimeException {
    public PromoCodeException(String message) { super(message); }
}
