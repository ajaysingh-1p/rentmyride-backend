package com.rentmyride.security;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks explicitly-revoked JWTs (logout) so a stolen/leaked-but-logged-out token can't keep
 * authenticating for the rest of its natural expiry — fixes issue #8 (no JWT revocation).
 *
 * NOTE: this is an in-memory store, which is fine for a single backend instance. If/when
 * RentMyRide is deployed behind a load balancer with multiple instances, this needs to move to
 * a shared store (e.g. Redis) so a token revoked on one instance is honored by all of them.
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final JwtUtil jwtUtil;

    // token -> its own expiry (ms since epoch), so we know when it's safe to forget about it.
    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    public void revoke(String token) {
        long expiryMillis;
        try {
            expiryMillis = jwtUtil.extractExpiryMillis(token);
        } catch (Exception e) {
            // Malformed token — nothing meaningful to revoke, and it'll fail validation anyway.
            return;
        }
        blacklist.put(token, expiryMillis);
    }

    public boolean isBlacklisted(String token) {
        return blacklist.containsKey(token);
    }

    // Sweep out entries whose token has expired naturally anyway — keeps the map from growing forever.
    @Scheduled(fixedRate = 30 * 60 * 1000) // every 30 minutes
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        blacklist.entrySet().removeIf(entry -> entry.getValue() < now);
    }
}
