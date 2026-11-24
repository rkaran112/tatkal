package com.tatkal;

import com.tatkal.booking.LockedBookingService;
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

/** 100 people try to book the same seat at the same moment. Exactly one must win. */
@SpringBootTest
@ActiveProfiles("test")
class LockedBookingConcurrencyTest {

    @Autowired
    LockedBookingService lockedBookingService;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("DELETE FROM booking");
        jdbc.update("UPDATE seat SET status = 'AVAILABLE'");
    }

    @Test
    void onlyOnePersonGetsTheSeat() throws Exception {
        Long seatId = jdbc.queryForObject("SELECT id FROM seat ORDER BY seat_no LIMIT 1", Long.class);
        int people = 100;

        CountDownLatch startGun = new CountDownLatch(1);
        AtomicInteger won = new AtomicInteger();
        AtomicInteger lost = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(people);
        for (int i = 0; i < people; i++) {
            String passenger = "user" + i;
            pool.submit(() -> {
                try {
                    startGun.await();
                    lockedBookingService.book(seatId, passenger);
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

        Integer bookingRows = jdbc.queryForObject("SELECT count(*) FROM booking WHERE seat_id = ?", Integer.class, seatId);
        assertThat(won.get()).isEqualTo(1);
        assertThat(lost.get()).isEqualTo(people - 1);
        assertThat(bookingRows).isEqualTo(1);
    }
}
