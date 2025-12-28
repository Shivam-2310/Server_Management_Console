package com.management.console.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class LifecycleActionException extends RuntimeException {
    
    public LifecycleActionException(String message) {
        super(message);
    }
    
    public LifecycleActionException(String message, Throwable cause) {
        super(message, cause);
    }
}

