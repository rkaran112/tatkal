import { useEffect, useState } from "react";

// Shows the load-test numbers. They come from public/results.json,
// which is produced from docs/results/results.csv by: node loadtest/summarize.mjs
export default function Results() {
  const [rows, setRows] = useState([]);

  useEffect(() => {
    fetch("/results.json")
      .then((response) => (response.ok ? response.json() : []))
      .then(setRows)
      .catch(() => setRows([]));
  }, []);

  if (rows.length === 0) return null;

  return (
    <section>
      <h2>The race: 1,000 people, same moment</h2>
      <table>
        <thead>
          <tr>
            <th>Version</th>
            <th>Scenario</th>
            <th>Seats sold twice</th>
            <th>Bookings</th>
            <th>p95 ms</th>
            <th>Requests/s</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.strategy + row.scenario}>
              <td>{row.name}</td>
              <td>{row.scenario}</td>
              <td className={row.doubleBooked > 0 ? "bad" : "good"}>{row.doubleBooked}</td>
              <td>{row.bookings}</td>
              <td>{row.p95}</td>
              <td>
                {row.reqPerSec} ({row.reqPerSecMin}-{row.reqPerSecMax})
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="small">Median of 5 races each; requests/s also shows the slowest and fastest race. All run on one laptop, so compare the rows with each other, not with a real server. When everyone wants the same seat the Redis hold is consistently faster than the row lock; when people spread out, the order is not stable.</p>
    </section>
  );
}
