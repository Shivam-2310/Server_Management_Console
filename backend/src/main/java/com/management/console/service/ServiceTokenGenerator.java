package com.management.console.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates secure authentication tokens for service registration.
 * Tokens are used for service-to-console authentication.
 */
@Component
@Slf4j
public class ServiceTokenGenerator {

    // Maximum token length allowed in database (matches ManagedService entity column length)
    private static final int MAX_TOKEN_LENGTH = 128;
    
    // Base token length: 48 bytes = 64 base64 characters when encoded
    private static final int BASE_TOKEN_BYTES = 48;
    
    // Maximum prefix length (including underscore separator)
    // Reserve space for base token (64 chars) + underscore (1 char) = 65 chars minimum
    // Allow up to 63 chars for prefix to stay within 128 total
    private static final int MAX_PREFIX_LENGTH = MAX_TOKEN_LENGTH - 65; // 63 chars max for prefix
    
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a secure random token for service authentication.
     * Format: Base64 encoded random bytes
     * Result: Exactly 64 characters (48 bytes encoded as base64url without padding)
     */
    public String generateToken() {
        byte[] tokenBytes = new byte[BASE_TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        log.debug("Generated new service authentication token (length: {})", token.length());
        return token;
    }

    /**
     * Generate a token with a service name prefix for easier identification.
     * The prefix is derived from the service name and ensures the total token
     * length never exceeds MAX_TOKEN_LENGTH (128 characters).
     * 
     * Format: {PREFIX}_{BASE64_TOKEN}
     * Example: "AICHAT_5Ovd9RIQuhyq_8NWWyZKo15rY_1RF0-3JHhhDi24FSXvXDdvw-PXfPoJj7C8RGbw"
     * 
     * @param serviceName The service name to create a prefix from
     * @return A token with prefix, guaranteed to be <= MAX_TOKEN_LENGTH characters
     */
    public String generateTokenForService(String serviceName) {
        String baseToken = generateToken(); // Always 64 characters
        
        if (serviceName == null || serviceName.trim().isEmpty()) {
            return baseToken;
        }
        
        // Create prefix from service name, ensuring it doesn't exceed max length
        String normalizedName = serviceName.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        int prefixLength = Math.min(MAX_PREFIX_LENGTH, normalizedName.length());
        
        if (prefixLength == 0) {
            // If no valid characters, just return base token
            return baseToken;
        }
        
        String prefix = normalizedName.substring(0, prefixLength) + "_";
        String fullToken = prefix + baseToken;
        
        // Safety check: ensure we never exceed max length
        if (fullToken.length() > MAX_TOKEN_LENGTH) {
            log.warn("Generated token exceeds max length ({}), truncating prefix", fullToken.length());
            // Truncate prefix to fit exactly
            int availableSpace = MAX_TOKEN_LENGTH - baseToken.length() - 1; // -1 for underscore
            prefix = normalizedName.substring(0, Math.min(availableSpace, normalizedName.length())) + "_";
            fullToken = prefix + baseToken;
        }
        
        log.debug("Generated token for service '{}': length={}, prefix='{}'", 
                serviceName, fullToken.length(), prefix);
        
        return fullToken;
    }
}

