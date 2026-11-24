package com.tatkal.admin;

import com.tatkal.booking.HoldBookingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Two helper endpoints used only by the load test:
 *   POST /api/admin/reset  -> every seat free again, all bookings deleted
 *   GET  /api/admin/stats  -> how many bookings, and how many seats were sold more than once
 *
 * Switched off with tatkal.admin-enabled=false, because a public server
 * should not let strangers reset everything.
 */
@RestController
@RequestMapping("/api/admin")
@ConditionalOnProperty(name = "tatkal.admin-enabled", havingValue = "true", matchIfMissing = true)
public class AdminController {

    private final JdbcTemplate jdbc;
    private final HoldBookingService holdBookingService;

    public AdminController(JdbcTemplate jdbc, HoldBookingService holdBookingService) {
        this.jdbc = jdbc;
        this.holdBookingService = holdBookingService;
    }

    @PostMapping("/reset")
    public Map<String, String> reset() {
        jdbc.update("DELETE FROM booking");
        jdbc.update("UPDATE seat SET status = 'AVAILABLE'");
        holdBookingService.clearAllHolds();      // also forget every Redis hold
        return Map.of("status", "reset");
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Integer bookings = jdbc.queryForObject("SELECT count(*) FROM booking", Integer.class);

        // A seat sold to 2 or more people is a double-booking.
        Integer doubleBooked = jdbc.queryForObject(
                "SELECT count(*) FROM (SELECT seat_id FROM booking GROUP BY seat_id HAVING count(*) > 1) t",
                Integer.class);

        return Map.of("bookings", bookings, "doubleBookedSeats", doubleBooked);
    }
}
