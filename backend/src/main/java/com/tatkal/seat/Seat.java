package com.tatkal.seat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** One physical seat. Maps to the "seat" table. */
@Entity
@Table(name = "seat")
public class Seat {

    public static final String AVAILABLE = "AVAILABLE";
    public static final String BOOKED = "BOOKED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "train_no")
    private String trainNo;

    @Column(name = "journey_date")
    private LocalDate journeyDate;

    private String coach;

    @Column(name = "seat_no")
    private int seatNo;

    private String status;

    protected Seat() {
        // JPA needs an empty constructor
    }

    public Long getId() { return id; }
    public String getTrainNo() { return trainNo; }
    public LocalDate getJourneyDate() { return journeyDate; }
    public String getCoach() { return coach; }
    public int getSeatNo() { return seatNo; }
    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public boolean isAvailable() {
        return AVAILABLE.equals(status);
    }
}
