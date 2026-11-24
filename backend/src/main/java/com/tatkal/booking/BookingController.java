package com.tatkal.booking;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BookingController {

    private final NaiveBookingService naiveBookingService;
    private final LockedBookingService lockedBookingService;
    private final HoldBookingService holdBookingService;

    public BookingController(NaiveBookingService naiveBookingService,
                             LockedBookingService lockedBookingService,
                             HoldBookingService holdBookingService) {
        this.naiveBookingService = naiveBookingService;
        this.lockedBookingService = lockedBookingService;
        this.holdBookingService = holdBookingService;
    }

    /** Version 1: naive booking (double-books under load). */
    @PostMapping("/v1/book")
    public Booking bookV1(@RequestBody BookRequest request) {
        return naiveBookingService.book(request.seatId(), request.passenger());
    }

    /** Version 2: database row lock (always correct, requests queue up). */
    @PostMapping("/v2/book")
    public Booking bookV2(@RequestBody BookRequest request) {
        return lockedBookingService.book(request.seatId(), request.passenger());
    }

    /** Version 3, step 1: hold the seat for a few minutes. */
    @PostMapping("/v3/hold")
    public Hold holdV3(@RequestBody HoldRequest request) {
        return holdBookingService.hold(request.seatId(), request.userId());
    }

    /** Version 3, step 2: pay and turn the hold into a booking. */
    @PostMapping("/v3/confirm")
    public Booking confirmV3(@RequestBody HoldRequest request) {
        return holdBookingService.confirm(request.seatId(), request.userId());
    }
}
