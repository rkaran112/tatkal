// Turns docs/results/results.csv (one line per race) into:
//   frontend/public/results.json   <- the table shown on the web page
//   docs/results/results.md        <- the table pasted into the README
//
// Run from the repo root:  node loadtest/summarize.mjs
//
// Each configuration was raced several times (5 by default). We report the MEDIAN of the speed numbers,
// but the WORST case for "seats sold twice" (one bad run is the one that matters).

import { readFileSync, writeFileSync } from "node:fs";

const NAMES = { v1: "v1 Naive", v2: "v2 Row lock", v3: "v3 Redis hold" };

function median(numbers) {
  const sorted = [...numbers].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 1 ? sorted[middle] : Math.round((sorted[middle - 1] + sorted[middle]) / 2);
}

// 1. Read the CSV into a list of objects: { strategy: "v1", p95_ms: "4387", ... }
const lines = readFileSync("docs/results/results.csv", "utf8").trim().split("\n");
const header = lines[0].split(",");
const races = lines.slice(1).map((line) => {
  const values = line.split(",");
  const race = {};
  header.forEach((column, i) => (race[column] = values[i]));
  return race;
});

// 2. Group the races by strategy + scenario.
const groups = {};
for (const race of races) {
  const key = race.strategy + "|" + race.scenario;
  if (!groups[key]) groups[key] = [];
  groups[key].push(race);
}

// 3. One summary row per group.
const rows = [];
for (const key of Object.keys(groups)) {
  const group = groups[key];
  const column = (name) => group.map((race) => Number(race[name]));
  rows.push({
    strategy: group[0].strategy,
    name: NAMES[group[0].strategy],
    scenario: group[0].scenario,
    runs: group.length,
    users: Number(group[0].users),
    bookings: median(column("booking_rows")),
    doubleBooked: Math.max(...column("double_booked_seats")),
    errors: Math.max(...column("errors")),
    p50: median(column("p50_ms")),
    p95: median(column("p95_ms")),
    p99: median(column("p99_ms")),
    reqPerSec: median(column("req_per_sec")),
    reqPerSecMin: Math.min(...column("req_per_sec")),
    reqPerSecMax: Math.max(...column("req_per_sec")),
  });
}

// Show same-seat first, then spread; v1, v2, v3 inside each.
rows.sort((a, b) => a.scenario.localeCompare(b.scenario) || a.strategy.localeCompare(b.strategy));
// (alphabetically "same-seat" comes before "spread")

writeFileSync("frontend/public/results.json", JSON.stringify(rows, null, 2));

// 4. The Markdown table.
let md = "| Version | Scenario | Seats sold twice | Booking rows | p50 ms | p95 ms | p99 ms | Requests/s (min-max) |\n";
md += "|---|---|---|---|---|---|---|---|\n";
for (const r of rows) {
  md += `| ${r.name} | ${r.scenario} | ${r.doubleBooked} | ${r.bookings} | ${r.p50} | ${r.p95} | ${r.p99} | ${r.reqPerSec} (${r.reqPerSecMin}-${r.reqPerSecMax}) |\n`;
}
writeFileSync("docs/results/results.md", md);

console.log(md);
