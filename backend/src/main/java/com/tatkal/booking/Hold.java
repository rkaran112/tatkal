package com.tatkal.booking;

/** The answer to a successful hold: whose it is and how long they have left to pay. */
public record Hold(Long seatId, String userId, long secondsLeft) {
}
