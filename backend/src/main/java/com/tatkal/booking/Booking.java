package com.tatkal.booking;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** One ticket sold. Maps to the "booking" table. */
@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long seatId;
    private String passenger;
    private String strategy;     // "v1", "v2" or "v3": which booking method sold this ticket
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Booking() {
        // JPA needs an empty constructor
    }

    public Booking(Long seatId, String passenger, String strategy) {
        this.seatId = seatId;
        this.passenger = passenger;
        this.strategy = strategy;
    }

    public Long getId() { return id; }
    public Long getSeatId() { return seatId; }
    public String getPassenger() { return passenger; }
    public String getStrategy() { return strategy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
