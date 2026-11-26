import { berthName, BERTH_TYPES } from "../format.js";

// Draws the 72 seats the way a real sleeper coach is laid out:
// 9 bays, each with 6 berths on one side of the aisle and 2 "side" berths on the other.

// What state is this seat in, from this user's point of view?
function seatState(seat, userId) {
  if (seat.status === "BOOKED") return "booked";
  if (seat.heldBy === userId) return "mine";
  if (seat.heldBy) return "held";
  return "free";
}

const STATE_WORDS = {
  free: "free",
  mine: "held by you",
  held: "held by someone else",
  booked: "booked",
};

// Cut the list of seats into groups of 8 (one group per bay).
function intoBays(seats) {
  const bays = [];
  for (let start = 0; start < seats.length; start += 8) {
    bays.push(seats.slice(start, start + 8));
  }
  return bays;
}

export default function CoachMap({ seats, userId, busySeatId, onSeatClick }) {
  function renderSeat(seat) {
    const state = seatState(seat, userId);
    const label = `Seat ${seat.seatNo}, ${berthName(seat.seatNo).toLowerCase()}, ${STATE_WORDS[state]}`;
    return (
      <button
        key={seat.id}
        type="button"
        className={`seat seat--${state}${busySeatId === seat.id ? " seat--busy" : ""}`}
        disabled={state === "booked" || state === "held"}
        aria-label={label}
        title={label}
        onClick={() => onSeatClick(seat)}
      >
        {seat.seatNo}
      </button>
    );
  }

  return (
    <section className="coach" aria-label="Seat map of coach S1">
      <ul className="legend">
        <li><span className="swatch seat--free" />Free</li>
        <li><span className="swatch seat--mine" />Held by you</li>
        <li><span className="swatch seat--held" />Held by someone</li>
        <li><span className="swatch seat--booked" />Booked</li>
      </ul>

      <div className="coach__body">
        {/* Column headings: which type of berth is in each column */}
        <div className="bay bay--heading" aria-hidden="true">
          {BERTH_TYPES.slice(0, 3).map((type, i) => <span key={i}>{type.short}</span>)}
          <span className="bay__gap" />
          {BERTH_TYPES.slice(3, 6).map((type, i) => <span key={i}>{type.short}</span>)}
          <span className="bay__aisle" />
          {BERTH_TYPES.slice(6).map((type, i) => <span key={i}>{type.short}</span>)}
        </div>

        {intoBays(seats).map((bay, index) => (
          <div className="bay" key={index} role="group" aria-label={`Bay ${index + 1}`}>
            {bay.slice(0, 3).map(renderSeat)}
            <span className="bay__gap" />
            {bay.slice(3, 6).map(renderSeat)}
            <span className="bay__aisle" />
            {bay.slice(6).map(renderSeat)}
          </div>
        ))}
      </div>

      <p className="coach__key">
        LB lower, MB middle, UB upper berth. SL side lower, SU side upper, across the aisle.
      </p>
    </section>
  );
}
