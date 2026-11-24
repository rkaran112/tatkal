package com.tatkal.booking;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

/** Turns our exceptions into proper HTTP status codes, in one place. */
@RestControllerAdvice
public class ApiExceptionHandler {

    // 409 Conflict: someone else got there first
    @ExceptionHandler(SeatTakenException.class)
    public ResponseEntity<Map<String, String>> seatTaken(SeatTakenException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    // 404 Not Found: the seat id does not exist
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }
}
