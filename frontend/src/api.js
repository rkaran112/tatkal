// Small helpers that talk to the Spring Boot backend.
// Every function returns { ok, data } so the page does not have to deal with fetch details.

async function request(url, options) {
  const response = await fetch(url, options);
  const data = await response.json().catch(() => ({}));
  return { ok: response.ok, data };
}

function postJson(url, body) {
  return request(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export const getSeats = () => request("/api/seats");

// Version 3, step 1: hold the seat for a few minutes
export const holdSeat = (seatId, userId) => postJson("/api/v3/hold", { seatId, userId });

// Version 3, step 2: pay, which turns the hold into a booking
export const confirmSeat = (seatId, userId) => postJson("/api/v3/confirm", { seatId, userId });
