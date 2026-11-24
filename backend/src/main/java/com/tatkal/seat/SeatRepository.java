package com.tatkal.seat;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findAllByOrderBySeatNo();

    /**
     * Reads the seat AND locks its row until the transaction ends (used by version 2).
     * PESSIMISTIC_WRITE makes PostgreSQL run: SELECT ... FOR UPDATE.
     * Anyone else who asks for the same row has to wait in line.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id = :id")
    Optional<Seat> findByIdForUpdate(@Param("id") Long id);

    /**
     * Check and change in ONE statement (used by version 3 as a last safety net).
     * Returns 1 if this call booked the seat, 0 if the seat was not available.
     * The database does the check and the change together, so no gap is possible.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Seat s set s.status = 'BOOKED' where s.id = :id and s.status = 'AVAILABLE'")
    int markBookedIfAvailable(@Param("id") Long id);
}
