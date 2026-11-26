import { useEffect, useRef, useState } from "react";
import { getSeats, holdSeat, confirmSeat } from "./api.js";
import { getUserId } from "./userId.js";
import TrainBoard from "./components/TrainBoard.jsx";
import CoachMap from "./components/CoachMap.jsx";
import BookingPanel from "./components/BookingPanel.jsx";
import HowItWorks from "./components/HowItWorks.jsx";

const userId = getUserId();
const REPO_URL = "https://github.com/rkaran112/tatkal";
const isPublicSite = window.location.hostname !== "localhost";

export default function App() {
  const [seats, setSeats] = useState([]);          // the 72 seats, refreshed every 2 seconds
  const [offline, setOffline] = useState(false);   // true when the seat map cannot be loaded
  const [myHold, setMyHold] = useState(null);      // { seatId, seatNo, expiresAt } while I hold a seat
  const [secondsLeft, setSecondsLeft] = useState(0);
  const [ticket, setTicket] = useState(null);      // { number, seatNo } after paying
  const [notice, setNotice] = useState(null);      // { tone, text }: a short message that pops up
  const [busySeatId, setBusySeatId] = useState(null); // the seat we are asking the server to hold
  const [paying, setPaying] = useState(false);     // shows "Paying…" on the button

  // True while a hold or payment is on its way to the server. A ref changes immediately (state only
  // changes after the next redraw), so even a very fast double click cannot send a request twice.
  const requestInFlight = useRef(false);

  // Ask the backend for the seat map now, and again every 2 seconds.
  async function refreshSeats() {
    // Nobody is looking at a hidden tab, so do not bother the server for it.
    if (document.hidden) return;
    const { ok, data } = await getSeats();
    if (ok && Array.isArray(data)) {
      setSeats(data);
      setOffline(false);
    } else {
      setOffline(true);
    }
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
        setNotice({ tone: "warning", text: `Time ran out. Seat ${myHold.seatNo} is free for everyone again.` });
        refreshSeats();
      }
    }, 500);
    return () => clearInterval(timer);
  }, [myHold]);

  // A pop-up message disappears by itself after a few seconds.
  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(() => setNotice(null), 6000);
    return () => clearTimeout(timer);
  }, [notice]);

  // Clicking a seat tries to HOLD it (version 3, step 1).
  async function handleSeatClick(seat) {
    if (myHold) {
      const text =
        myHold.seatId === seat.id
          ? `Seat ${seat.seatNo} is already yours. Pay to keep it.`
          : `You are holding seat ${myHold.seatNo}. Pay for it first, or wait for the timer to end.`;
      setNotice({ tone: "warning", text });
      return;
    }
    if (requestInFlight.current) return;

    requestInFlight.current = true;
    setBusySeatId(seat.id);
    const { ok, data } = await holdSeat(seat.id, userId);
    setBusySeatId(null);
    requestInFlight.current = false;

    if (ok) {
      setMyHold({ seatId: seat.id, seatNo: seat.seatNo, expiresAt: Date.now() + data.secondsLeft * 1000 });
      setSecondsLeft(data.secondsLeft);
      setTicket(null);
      setNotice(null);
    } else {
      setNotice({ tone: "error", text: data.error || `Seat ${seat.seatNo} could not be held. Try another one.` });
    }
    refreshSeats();
  }

  // Paying CONFIRMS the booking (version 3, step 2).
  async function handlePay() {
    if (requestInFlight.current) return;

    requestInFlight.current = true;
    setPaying(true);
    const { ok, offline: noConnection, data } = await confirmSeat(myHold.seatId, userId);
    setPaying(false);
    requestInFlight.current = false;

    if (noConnection) {
      // We never reached the server, so keep the hold and let the person try again.
      setNotice({ tone: "error", text: data.error });
      return;
    }
    if (ok) {
      setTicket({ number: data.id, seatNo: myHold.seatNo });
      setNotice({ tone: "success", text: `Seat ${myHold.seatNo} is booked. Ticket number ${data.id}.` });
    } else {
      setNotice({ tone: "error", text: data.error || "The payment did not go through. Choose a seat again." });
    }
    setMyHold(null);
    refreshSeats();
  }

  const booked = seats.filter((seat) => seat.status === "BOOKED").length;
  const held = seats.filter((seat) => seat.status !== "BOOKED" && seat.heldBy).length;
  const free = seats.length - booked - held;

  // After a page reload the server still remembers my hold, but the page does not. Point it out.
  const waitingSeat = seats.find((seat) => seat.status !== "BOOKED" && seat.heldBy === userId);

  return (
    <>
      <header className="topbar">
        <div className="wrap topbar__row">
          <a className="brand" href="#top">
            <span className="brand__mark" aria-hidden="true">T</span>
            Tatkal Booking
          </a>
          <nav>
            <a href="#how-it-works">How it works</a>
            <a href={REPO_URL} target="_blank" rel="noreferrer">Source code</a>
          </nav>
        </div>
      </header>

      <main className="wrap" id="top">
        <TrainBoard free={free} held={held} booked={booked} />

        {isPublicSite && <p className="demo-note">Demo site: all seats are freed again every 15 minutes.</p>}
        {offline && <p className="banner" role="alert">Can't reach the server. Trying again every 2 seconds.</p>}

        <div className={`booking${myHold || ticket ? " booking--active" : ""}`}>
          {seats.length > 0 ? (
            <CoachMap
              seats={seats}
              userId={userId}
              busySeatId={busySeatId}
              onSeatClick={handleSeatClick}
            />
          ) : (
            <div className="coach coach--loading" aria-busy="true">Loading the seat map…</div>
          )}

          <BookingPanel
            userId={userId}
            hold={myHold}
            secondsLeft={secondsLeft}
            ticket={ticket}
            paying={paying}
            waitingSeatNo={waitingSeat?.seatNo}
            onPay={handlePay}
            onDone={() => setTicket(null)}
          />
        </div>

        <HowItWorks />
      </main>

      <footer className="footer">
        <div className="wrap">
          <p>A demo project by R. Karan, built with Java, Spring Boot, PostgreSQL, Redis and React.</p>
          <p>Not a real booking service, and not connected to Indian Railways or IRCTC.</p>
        </div>
      </footer>

      {/* Screen readers announce messages that appear in this region */}
      <div className="toast-region" aria-live="polite">
        {notice && (
          <div className={`toast toast--${notice.tone}`}>
            <p>{notice.text}</p>
            <button type="button" onClick={() => setNotice(null)} aria-label="Close message">×</button>
          </div>
        )}
      </div>
    </>
  );
}
