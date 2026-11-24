-- One row per physical seat on one train, on one date.
CREATE TABLE seat (
    id           BIGSERIAL PRIMARY KEY,
    train_no     VARCHAR(10) NOT NULL,
    journey_date DATE        NOT NULL,
    coach        VARCHAR(5)  NOT NULL,
    seat_no      INT         NOT NULL,
    status       VARCHAR(10) NOT NULL DEFAULT 'AVAILABLE',  -- AVAILABLE or BOOKED
    UNIQUE (train_no, journey_date, coach, seat_no)
);

-- One row per ticket sold.
-- seat_id is deliberately NOT unique. That is what lets the naive version
-- double-book and lets us count the damage afterwards. A real system would
-- add UNIQUE (seat_id) as a last safety net (see the README).
CREATE TABLE booking (
    id         BIGSERIAL PRIMARY KEY,
    seat_id    BIGINT      NOT NULL REFERENCES seat (id),
    passenger  VARCHAR(50) NOT NULL,
    strategy   VARCHAR(10) NOT NULL,   -- v1, v2 or v3
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX booking_seat_id_idx ON booking (seat_id);
