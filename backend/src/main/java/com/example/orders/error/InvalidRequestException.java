package com.example.orders.error;

/** A request parameter is not acceptable (for example an unknown sort field). Results in 400. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
