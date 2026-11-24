package com.tatkal.booking;

import com.tatkal.seat.Seat;
import com.tatkal.seat.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * VERSION 2: database row lock (pessimistic locking).
 *
 * Same steps as version 1, with ONE change: the read locks the seat row
 * (SELECT ... FOR UPDATE). Think of a single-person toilet with a key:
 *   - the first request takes the key (the lock) and reads the seat
 *   - everybody else waits outside
 *   - when the first request commits, the lock is released and the next one goes in
 *   - the next one now reads BOOKED and gets a 409
 *
 * Correct every time, because the "gap" from version 1 no longer exists.
 * The price: the requests for one seat are handled one by one, so they queue up.
 */
@Service
public class LockedBookingService {

    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;

    public LockedBookingService(SeatRepository seatRepository, BookingRepository bookingRepository) {
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public Booking book(Long seatId, String passenger) {
        // The only difference from version 1: findByIdForUpdate locks the row.
        Seat seat = seatRepository.findByIdForUpdate(seatId)
                .orElseThrow(() -> new NoSuchElementException("No seat with id " + seatId));

        if (!seat.isAvailable()) {
            throw new SeatTakenException("Seat " + seat.getSeatNo() + " is already booked");
        }

        Booking booking = bookingRepository.save(new Booking(seatId, passenger, "v2"));
        seat.setStatus(Seat.BOOKED);
        return booking;
    }
}
