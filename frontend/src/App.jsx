import { useEffect, useState } from "react";
import { getSeats, holdSeat, confirmSeat } from "./api.js";
import { getUserId } from "./userId.js";
import Results from "./Results.jsx";

const userId = getUserId();

// Turns 287 seconds into "4:47"
function formatTime(totalSeconds) {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = String(totalSeconds % 60).padStart(2, "0");
  return `${minutes}:${seconds}`;
}

export default function App() {
  const [seats, setSeats] = useState([]);        // the 72 seats, refreshed every 2 seconds
  const [myHold, setMyHold] = useState(null);    // { seatId, expiresAt } while I am holding a seat
  const [secondsLeft, setSecondsLeft] = useState(0);
  const [message, setMessage] = useState("Pick a seat. Open this page in a second tab to compete with yourself.");

  // Ask the backend for the seat map now, and again every 2 seconds.
  async function refreshSeats() {
    // Nobody is looking at a hidden tab, so do not bother the server for it.
    if (document.hidden) return;
    const { ok, data } = await getSeats();
    if (ok) setSeats(data);
  }

  useEffect(() => {
    refreshSeats();
    const timer = setInterval(refreshSeats, 2000);
    return () => clearInterval(timer);
  }, []);

  // While I hold a seat, count down once a second.
  useEffect(() => {
    if (!myHold) return;

    const timer = setInterval(() => {
      const left = Math.max(0, Math.round((myHold.expiresAt - Date.now()) / 1000));
      setSecondsLeft(left);
      if (left === 0) {
        setMyHold(null);
        setMessage("Your hold expired, so the seat is free again for everyone.");
        refreshSeats();
      }
    }, 500);
    return () => clearInterval(timer);
  }, [myHold]);

  // Clicking a seat tries to HOLD it (version 3, step 1).
  async function handleSeatClick(seat) {
    if (myHold) {
      setMessage("You already hold a seat. Pay for it first.");
      return;
    }
    const { ok, data } = await holdSeat(seat.id, userId);
    if (ok) {
      setMyHold({ seatId: seat.id, seatNo: seat.seatNo, expiresAt: Date.now() + data.secondsLeft * 1000 });
      setSecondsLeft(data.secondsLeft);
      setMessage(`Seat ${seat.seatNo} is yours for ${formatTime(data.secondsLeft)}. Pay before the timer runs out.`);
    } else {
      setMessage(data.error || "Could not hold that seat.");
    }
    refreshSeats();
  }

  // Paying CONFIRMS the booking (version 3, step 2).
  async function handlePay() {
    const { ok, data } = await confirmSeat(myHold.seatId, userId);
    if (ok) {
      setMessage(`Booked! Seat ${myHold.seatNo} is yours. Ticket number ${data.id}.`);
    } else {
      setMessage(data.error || "Payment failed.");
    }
    setMyHold(null);
    refreshSeats();
  }

  // Decide the colour (CSS class) of each seat.
  function seatClass(seat) {
    if (seat.status === "BOOKED") return "seat booked";
    if (seat.heldBy === userId) return "seat mine";
    if (seat.heldBy) return "seat held";
    return "seat free";
  }

  return (
    <main>
      <h1>Tatkal Booking</h1>
      <p className="train">12951 · Tatkal Express · 25 Dec 2026 · Coach S1</p>
      <p className="you">
        You are <strong>{userId}</strong>
      </p>

      <p className="message">{message}</p>
      {window.location.hostname !== "localhost" && (
        <p className="small">Demo site: all seats are freed again every 15 minutes.</p>
      )}

      {myHold && (
        <div className="holdBox">
          <span>
            Holding seat {myHold.seatNo}: <strong>{formatTime(secondsLeft)}</strong> left
          </span>
          <button onClick={handlePay}>Pay ₹ 1,250</button>
        </div>
      )}

      <div className="legend">
        <span className="seat free">free</span>
        <span className="seat held">held by someone</span>
        <span className="seat mine">held by you</span>
        <span className="seat booked">booked</span>
      </div>

      <div className="grid">
        {seats.map((seat) => (
          <button
            key={seat.id}
            className={seatClass(seat)}
            disabled={seat.status === "BOOKED" || (seat.heldBy && seat.heldBy !== userId)}
            onClick={() => handleSeatClick(seat)}
          >
            {seat.seatNo}
          </button>
        ))}
      </div>

      <Results />
    </main>
  );
}
