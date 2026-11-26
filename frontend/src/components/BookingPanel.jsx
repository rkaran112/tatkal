import { berthName, formatTime, FARE } from "../format.js";

const HOLD_SECONDS = 300; // the server holds a seat for 5 minutes

// The three steps of a booking, with the current one highlighted.
function Steps({ current }) {
  const steps = ["Choose", "Hold", "Pay"];
  return (
    <ol className="steps">
      {steps.map((step, index) => {
        const number = index + 1;
        const status = number < current ? "done" : number === current ? "now" : "next";
        return (
          <li key={step} className={`steps__item steps__item--${status}`} aria-current={status === "now" ? "step" : undefined}>
            <span className="steps__number">{number}</span>
            {step}
          </li>
        );
      })}
    </ol>
  );
}

// Shows one of three things: nothing chosen yet, a seat on hold, or the finished ticket.
export default function BookingPanel({ userId, hold, secondsLeft, ticket, paying, waitingSeatNo, onPay, onDone }) {
  const currentStep = ticket ? 4 : hold ? 3 : 1;
  const active = Boolean(hold || ticket); // on phones the panel sticks to the bottom while active

  return (
    <aside className={`panel${active ? " panel--active" : ""}`} aria-label="Your booking">
      <Steps current={currentStep} />

      {!hold && !ticket && !waitingSeatNo && (
        <div className="panel__body">
          <h2>Choose a free seat</h2>
          <p>
            Tap a green seat on the coach map. It stays yours for 5 minutes while you pay, and nobody else can take it.
          </p>
        </div>
      )}

      {!hold && !ticket && waitingSeatNo && (
        <div className="panel__body">
          <h2>Seat {waitingSeatNo} is still held for you</h2>
          <p>Tap the blue seat on the coach map to see the time left and pay.</p>
        </div>
      )}

      {hold && (
        <div className="panel__body">
          <h2>Seat {hold.seatNo} is on hold</h2>
          <p className="panel__berth">{berthName(hold.seatNo)}, coach S1</p>

          <div className={`timer${secondsLeft <= 60 ? " timer--low" : ""}`}>
            <p className="timer__clock" role="timer">{formatTime(secondsLeft)}</p>
            <p className="timer__text">left to pay</p>
            <div className="timer__track">
              <div className="timer__fill" style={{ width: `${Math.min(100, (secondsLeft / HOLD_SECONDS) * 100)}%` }} />
            </div>
          </div>

          <div className="fare">
            <span>Fare</span>
            <strong>{FARE}</strong>
          </div>
          <button type="button" className="button button--pay" onClick={onPay} disabled={paying}>
            {paying ? "Paying…" : `Pay ${FARE}`}
          </button>
          <p className="panel__note">This is a demo. No money is taken.</p>
        </div>
      )}

      {ticket && !hold && (
        <div className="panel__body">
          <div className="ticket">
            <h2>Seat {ticket.seatNo} is booked</h2>
            <dl>
              <div><dt>Ticket number</dt><dd>{ticket.number}</dd></div>
              <div><dt>Berth</dt><dd>{berthName(ticket.seatNo)}</dd></div>
              <div><dt>Train</dt><dd>12951, coach S1</dd></div>
              <div><dt>Passenger</dt><dd>{userId}</dd></div>
              <div><dt>Paid</dt><dd>{FARE}</dd></div>
            </dl>
          </div>
          <button type="button" className="button button--quiet" onClick={onDone}>
            Book another seat
          </button>
        </div>
      )}

      <div className="passenger">
        <p>
          You are <strong>{userId}</strong>
        </p>
        <p>Open this page in a second tab to be another passenger and race for the same seat.</p>
      </div>
    </aside>
  );
}
