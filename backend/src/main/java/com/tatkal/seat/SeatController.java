package com.tatkal.seat;

import com.tatkal.booking.HoldBookingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
public class SeatController {

    /** What the React page needs to draw one seat. heldBy is null when nobody holds it. */
    public record SeatView(Long id, int seatNo, String status, String heldBy) {
    }

    private final SeatRepository seatRepository;
    private final HoldBookingService holdBookingService;

    public SeatController(SeatRepository seatRepository, HoldBookingService holdBookingService) {
        this.seatRepository = seatRepository;
        this.holdBookingService = holdBookingService;
    }

    @GetMapping("/seats")
    public List<SeatView> seats() {
        List<SeatView> result = new ArrayList<>();
        for (Seat seat : seatRepository.findAllByOrderBySeatNo()) {
            String heldBy = holdBookingService.holderOf(seat.getId());   // asks Redis
            result.add(new SeatView(seat.getId(), seat.getSeatNo(), seat.getStatus(), heldBy));
        }
        return result;
    }
}
