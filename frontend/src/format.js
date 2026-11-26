// Small helpers for showing things to people.

export const FARE = "₹1,250";

// Turns 287 seconds into "4:47"
export function formatTime(totalSeconds) {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = String(totalSeconds % 60).padStart(2, "0");
  return `${minutes}:${seconds}`;
}

// An Indian sleeper coach is built from "bays" of 8 berths, always in this order.
// So the berth type follows directly from the seat number.
export const BERTH_TYPES = [
  { short: "LB", name: "Lower berth" },
  { short: "MB", name: "Middle berth" },
  { short: "UB", name: "Upper berth" },
  { short: "LB", name: "Lower berth" },
  { short: "MB", name: "Middle berth" },
  { short: "UB", name: "Upper berth" },
  { short: "SL", name: "Side lower berth" },
  { short: "SU", name: "Side upper berth" },
];

export function berthName(seatNo) {
  return BERTH_TYPES[(seatNo - 1) % 8].name;
}
