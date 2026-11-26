import { useEffect, useState } from "react";

// Explains the three ways of booking a seat and shows the load-test numbers for each.
// The numbers come from public/results.json, which is produced from
// docs/results/results.csv by: node loadtest/summarize.mjs

const METHODS = [
  {
    strategy: "v1",
    title: "No protection",
    text: "Check that the seat is free, then book it. Other requests slip in between those two steps.",
  },
  {
    strategy: "v2",
    title: "Database row lock",
    text: "The database locks the seat while one request books it. Everyone else waits in a queue.",
  },
  {
    strategy: "v3",
    title: "Redis hold",
    text: "Redis lets exactly one person hold the seat for 5 minutes while they pay. This page books this way.",
  },
];

const SCENARIOS = [
  { id: "same-seat", label: "Everyone wants one seat", seats: 1 },
  { id: "spread", label: "People spread over all seats", seats: 72 },
];

export default function HowItWorks() {
  const [rows, setRows] = useState([]);
  const [scenario, setScenario] = useState("same-seat");

  useEffect(() => {
    fetch("/results.json")
      .then((response) => (response.ok ? response.json() : []))
      .then(setRows)
      .catch(() => setRows([]));
  }, []);

  const seatCount = SCENARIOS.find((item) => item.id === scenario).seats;

  return (
    <section className="how" id="how-it-works">
      <h2>Why two people can't get the same seat</h2>
      <p className="how__intro">
        A load test sent 1,000 people to book at the same moment, once for each method.
        The right result is one ticket per seat.
      </p>

      <div className="switch" role="group" aria-label="Load test scenario">
        {SCENARIOS.map((item) => (
          <button
            key={item.id}
            type="button"
            className="switch__option"
            aria-pressed={scenario === item.id}
            onClick={() => setScenario(item.id)}
          >
            {item.label}
          </button>
        ))}
      </div>

      <div className="methods">
        {METHODS.map((method) => {
          const row = rows.find((r) => r.strategy === method.strategy && r.scenario === scenario);
          const broken = row && row.doubleBooked > 0;
          return (
            <article key={method.strategy} className={`method${method.strategy === "v3" ? " method--used" : ""}`}>
              <h3>{method.title}</h3>
              <p className="method__text">{method.text}</p>

              {row && (
                <>
                  <p className={`method__result ${broken ? "method__result--bad" : "method__result--good"}`}>
                    <strong>{row.bookings}</strong> {row.bookings === 1 ? "ticket" : "tickets"} for {seatCount}{" "}
                    {seatCount === 1 ? "seat" : "seats"}
                    <span>{broken ? "Sold the same seat twice or more" : "No seat sold twice"}</span>
                  </p>
                  <dl className="method__speed">
                    <div>
                      <dt>Requests per second</dt>
                      <dd>
                        {row.reqPerSec} <small>({row.reqPerSecMin} to {row.reqPerSecMax})</small>
                      </dd>
                    </div>
                    <div>
                      <dt>95% answered within</dt>
                      <dd>{(row.p95 / 1000).toFixed(1)} s</dd>
                    </div>
                  </dl>
                </>
              )}
            </article>
          );
        })}
      </div>

      <p className="how__note">
        Middle result of 5 test runs, with the slowest and fastest run in brackets. Everything ran on one laptop, so
        compare the methods with each other, not with a real server. The Redis hold was consistently faster than the row
        lock only when everyone wanted the same seat.
      </p>
    </section>
  );
}
