package com.tatkal.booking;

/** Thrown when someone else already has the seat. Becomes an HTTP 409 (see ApiExceptionHandler). */
public class SeatTakenException extends RuntimeException {

    public SeatTakenException(String message) {
        super(message);
    }
}
