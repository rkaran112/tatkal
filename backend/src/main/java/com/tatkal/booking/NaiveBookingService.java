package com.tatkal.booking;

import com.tatkal.seat.Seat;
import com.tatkal.seat.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * VERSION 1: the naive booking. It has a bug on purpose.
 *
 * The steps look fine when read one after the other:
 *   1. read the seat
 *   2. if it is free, save a booking and mark the seat BOOKED
 *
 * But 1,000 requests do step 1 at the same moment. All of them see AVAILABLE,
 * because nobody has finished step 2 yet. So all of them go on to book the seat.
 * This is called a check-then-act race condition.
 */
@Service
public class NaiveBookingService {

    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;

    public NaiveBookingService(SeatRepository seatRepository, BookingRepository bookingRepository) {
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public Booking book(Long seatId, String passenger) {
        // Step 1: read the seat. A plain read: nothing stops other requests reading it too.
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new NoSuchElementException("No seat with id " + seatId));

        if (!seat.isAvailable()) {
            throw new SeatTakenException("Seat " + seat.getSeatNo() + " is already booked");
        }

        // THE GAP: many requests can be standing right here at the same moment.

        // Step 2: sell the ticket and mark the seat as booked.
        Booking booking = bookingRepository.save(new Booking(seatId, passenger, "v1"));
        seat.setStatus(Seat.BOOKED);
        return booking;
    }
}
