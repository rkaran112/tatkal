package com.tatkal.booking;

/** The JSON body of a booking request: {"seatId": 5, "passenger": "asha"} */
public record BookRequest(Long seatId, String passenger) {
}
