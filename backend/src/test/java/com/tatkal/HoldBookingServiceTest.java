package com.tatkal;

import com.tatkal.booking.Booking;
import com.tatkal.booking.HoldBookingService;
import com.tatkal.booking.SeatTakenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for version 3 (Redis hold).
 * The test profile sets the hold time to 2 seconds so the expiry test is quick.
 */
@SpringBootTest
@ActiveProfiles("test")
class HoldBookingServiceTest {

    @Autowired
    HoldBookingService holdBookingService;

    @Autowired
    JdbcTemplate jdbc;

    Long seatId;

    @BeforeEach
    void resetEverything() {
        jdbc.update("DELETE FROM booking");
        jdbc.update("UPDATE seat SET status = 'AVAILABLE'");
        holdBookingService.clearAllHolds();
        seatId = jdbc.queryForObject("SELECT id FROM seat ORDER BY seat_no LIMIT 1", Long.class);
    }

    @Test
    void only_one_of_100_people_can_hold_the_same_seat() throws Exception {
        int people = 100;
        CountDownLatch startGun = new CountDownLatch(1);
        AtomicInteger won = new AtomicInteger();
        AtomicInteger lost = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(people);
        for (int i = 0; i < people; i++) {
            String userId = "user" + i;
            pool.submit(() -> {
                try {
                    startGun.await();
                    holdBookingService.hold(seatId, userId);
                    won.incrementAndGet();
                } catch (SeatTakenException e) {
                    lost.incrementAndGet();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        startGun.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(1, TimeUnit.MINUTES)).isTrue();

        assertThat(won.get()).isEqualTo(1);
        assertThat(lost.get()).isEqualTo(people - 1);
    }

    @Test
    void the_holder_can_confirm_and_gets_the_booking() {
        holdBookingService.hold(seatId, "asha");

        Booking booking = holdBookingService.confirm(seatId, "asha");

        assertThat(booking.getPassenger()).isEqualTo("asha");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking WHERE seat_id = ?", Integer.class, seatId)).isEqualTo(1);
        // once booked, nobody else can hold it
        assertThatThrownBy(() -> holdBookingService.hold(seatId, "ravi")).isInstanceOf(SeatTakenException.class);
    }

    @Test
    void someone_who_does_not_hold_the_seat_cannot_confirm() {
        holdBookingService.hold(seatId, "asha");

        assertThatThrownBy(() -> holdBookingService.confirm(seatId, "ravi")).isInstanceOf(SeatTakenException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking", Integer.class)).isZero();
    }

    @Test
    void an_expired_hold_frees_the_seat() throws Exception {
        holdBookingService.hold(seatId, "asha");
        assertThatThrownBy(() -> holdBookingService.hold(seatId, "ravi")).isInstanceOf(SeatTakenException.class);

        Thread.sleep(2500);   // the test profile holds for 2 seconds

        // Asha can no longer confirm, and Ravi can now hold the seat.
        assertThatThrownBy(() -> holdBookingService.confirm(seatId, "asha")).isInstanceOf(SeatTakenException.class);
        holdBookingService.hold(seatId, "ravi");
        assertThat(holdBookingService.holderOf(seatId)).isEqualTo("ravi");
    }
}
