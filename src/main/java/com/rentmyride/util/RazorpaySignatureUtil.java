package com.rentmyride.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class RazorpaySignatureUtil {

    /**
     * Verifies a Razorpay checkout payment signature.
     * Per Razorpay docs: expected_signature = HMAC_SHA256(order_id + "|" + payment_id, key_secret)
     */
    public static boolean verify(String orderId, String paymentId, String signature, String keySecret) {
        try {
            String payload = orderId + "|" + paymentId;
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            sha256Hmac.init(new SecretKeySpec(keySecret.getBytes(), "HmacSHA256"));
            byte[] hash = sha256Hmac.doFinal(payload.getBytes());

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));

            return hex.toString().equals(signature);
        } catch (Exception e) {
            return false;
        }
    }
}
