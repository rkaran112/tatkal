-- One train, one date, one coach with 72 seats.
INSERT INTO seat (train_no, journey_date, coach, seat_no, status)
SELECT '12951', DATE '2026-12-25', 'S1', n, 'AVAILABLE'
FROM generate_series(1, 72) AS n;
