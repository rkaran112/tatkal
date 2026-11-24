package com.tatkal.booking;

/** The JSON body for hold and confirm: {"seatId": 5, "userId": "asha"} */
public record HoldRequest(Long seatId, String userId) {
}
