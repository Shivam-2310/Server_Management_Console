package com.management.console.domain.enums;

public enum UserRole {
    VIEWER,     // Read-only access
    OPERATOR,   // Can restart, scale services
    ADMIN       // Full control including configuration
}

