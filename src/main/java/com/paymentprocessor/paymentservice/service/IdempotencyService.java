package com.paymentprocessor.paymentservice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.paymentprocessor.paymentservice.entity.IdempotencyKey;
import com.paymentprocessor.paymentservice.entity.IdempotencyKeyId;
import com.paymentprocessor.paymentservice.exception.IdempotencyConflictException;
import com.paymentprocessor.paymentservice.repository.IdempotencyKeyRepository;

/**
 * Guarantees that a mutating request carrying an {@code Idempotency-Key} executes
 * at most once per merchant. A completed key replays the stored response; a key
 * reused with a different payload is rejected; an in-flight key is rejected.
 */
@Service
public class IdempotencyService {

    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String COMPLETED = "COMPLETED";

    private final IdempotencyKeyRepository repository;

    public IdempotencyService(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    /**
     * Reserves the key. Returns the previously stored response body if this exact
     * request already completed; otherwise locks the key and returns empty so the
     * caller proceeds. Called inside the caller's transaction.
     */
    public Optional<String> begin(String merchantId, String key, String requestBody) {
        byte[] hash = sha256(requestBody);
        IdempotencyKeyId id = new IdempotencyKeyId(merchantId, key);
        Optional<IdempotencyKey> existing = repository.findById(id);
        if (existing.isPresent()) {
            IdempotencyKey ik = existing.get();
            if (!Arrays.equals(ik.getRequestHash(), hash)) {
                throw new IdempotencyConflictException(
                        "Idempotency-Key reused with a different request payload");
            }
            if (COMPLETED.equals(ik.getStatus())) {
                return Optional.ofNullable(ik.getResponseBody());
            }
            throw new IdempotencyConflictException("A request with this Idempotency-Key is still in progress");
        }
        IdempotencyKey ik = new IdempotencyKey();
        ik.setMerchantId(merchantId);
        ik.setKey(key);
        ik.setRequestHash(hash);
        ik.setStatus(IN_PROGRESS);
        ik.setLockedAt(Instant.now());
        ik.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        repository.save(ik);
        return Optional.empty();
    }

    public void complete(String merchantId, String key, int responseCode, String responseBody) {
        IdempotencyKeyId id = new IdempotencyKeyId(merchantId, key);
        repository.findById(id).ifPresent(ik -> {
            ik.setStatus(COMPLETED);
            ik.setResponseCode(responseCode);
            ik.setResponseBody(responseBody);
            repository.save(ik);
        });
    }

    private byte[] sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
