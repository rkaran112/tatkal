package com.tatkal.booking;

import com.tatkal.seat.Seat;
import com.tatkal.seat.SeatRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * VERSION 3: Redis hold, like the real IRCTC site.
 *
 * Booking is two steps, because paying takes time:
 *   1. HOLD:    claim the seat for 5 minutes. Fast, and only ONE person can win it.
 *   2. CONFIRM: after "payment", turn the hold into a real booking.
 *
 * Why Redis? It has one command that does "create this key only if it does not
 * exist yet" as a single step: SET key value NX EX 300
 *   NX = only if the key is absent      EX = delete it automatically after 300 seconds
 * Redis handles commands one at a time, so out of 1,000 simultaneous attempts
 * exactly one gets "OK" and the other 999 are told "no". Nobody touches the
 * database until they hold the seat, so the database is not hammered.
 *
 * If the person never pays, Redis deletes the key after 5 minutes and the seat is free again.
 */
@Service
public class HoldBookingService {

    private static final String KEY_PREFIX = "hold:seat:";

    private final StringRedisTemplate redis;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final Duration holdDuration;

    public HoldBookingService(StringRedisTemplate redis,
                              SeatRepository seatRepository,
                              BookingRepository bookingRepository,
                              @Value("${tatkal.hold-duration}") Duration holdDuration) {
        this.redis = redis;
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.holdDuration = holdDuration;
    }

    /** Step 1: try to hold the seat. */
    public Hold hold(Long seatId, String userId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new NoSuchElementException("No seat with id " + seatId));

        if (!seat.isAvailable()) {
            throw new SeatTakenException("Seat " + seat.getSeatNo() + " is already booked");
        }

        // The key line of the whole project: set the key only if nobody has it.
        Boolean won = redis.opsForValue().setIfAbsent(keyFor(seatId), userId, holdDuration);

        if (Boolean.TRUE.equals(won)) {
            return new Hold(seatId, userId, holdDuration.toSeconds());
        }

        // Somebody already holds it. If it is this same user asking again, that is fine.
        if (userId.equals(holderOf(seatId))) {
            return new Hold(seatId, userId, secondsLeft(seatId));
        }
        throw new SeatTakenException("Seat " + seat.getSeatNo() + " is being held by someone else");
    }

    /** Step 2: after "payment", turn the hold into a real booking. */
    @Transactional
    public Booking confirm(Long seatId, String userId) {
        // Only the person holding the seat may confirm, and only before the hold expires.
        if (!userId.equals(holderOf(seatId))) {
            throw new SeatTakenException("You do not hold this seat (the hold may have expired)");
        }

        // Safety net: the database also checks "still available?" in the same statement that changes it.
        int booked = seatRepository.markBookedIfAvailable(seatId);
        if (booked == 0) {
            throw new SeatTakenException("Seat is already booked");
        }

        Booking booking = bookingRepository.save(new Booking(seatId, userId, "v3"));

        // Clean up: the hold is no longer needed. (The seat is BOOKED now, so nobody can hold it again.)
        redis.delete(keyFor(seatId));
        return booking;
    }

    /** Who is holding this seat right now? null if nobody. Used by the seat map too. */
    public String holderOf(Long seatId) {
        return redis.opsForValue().get(keyFor(seatId));
    }

    /** Admin reset: drop every hold. */
    public void clearAllHolds() {
        Set<String> keys = redis.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private long secondsLeft(Long seatId) {
        Long seconds = redis.getExpire(keyFor(seatId), TimeUnit.SECONDS);
        return seconds == null ? 0 : seconds;
    }

    private String keyFor(Long seatId) {
        return KEY_PREFIX + seatId;
    }
}
