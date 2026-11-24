# Tatkal Booking: no two people get the same seat

At 10 am on Tatkal day, thousands of people click the same seat at the same moment. This project is a small train-seat booking system that answers one question:

> **What happens when two requests for the same seat arrive together?**

It has three versions of "book a seat". A load test sends **1,000 people at one seat at the same instant** to each version and counts what really ends up in the database.

| Version | How it works | What the load test shows |
|---|---|---|
| **v1 Naive** | Read the seat, if it is free, save the booking | **Double-books.** The same seat is sold to many people. |
| **v2 Row lock** | Same, but the database locks the seat row while one person is booking it (`SELECT ... FOR UPDATE`) | Correct every time. Requests for one seat are served one by one. |
| **v3 Redis hold** | Like the real site: *hold* the seat for 5 minutes, then *pay* to confirm. Redis decides who gets the hold. | Correct every time, and the fastest. |

Built with Java 21, Spring Boot, PostgreSQL, Redis, React, Docker and GitHub Actions.

## The results

1,000 users, all fired at the same moment. Each row is the median of 3 races, after one warm-up race.
"Seats sold twice" is the **worst** of the 3 races.

| Version | Scenario | Seats sold twice | Booking rows | p50 ms | p95 ms | p99 ms | Requests/s |
|---|---|---|---|---|---|---|---|
| v1 Naive | same-seat | 1 | 17 | 1496 | 3476 | 3852 | 149 |
| v2 Row lock | same-seat | 0 | 1 | 2133 | 4367 | 4920 | 120 |
| v3 Redis hold | same-seat | 0 | 1 | 1603 | 2115 | 2456 | 298 |
| v1 Naive | spread | 10 | 78 | 1424 | 3983 | 4379 | 147 |
| v2 Row lock | spread | 0 | 72 | 1857 | 4531 | 5954 | 134 |
| v3 Redis hold | spread | 0 | 72 | 1230 | 2576 | 3092 | 211 |

- **same-seat**: all 1,000 people want seat 1. The right answer is **1** booking.
- **spread**: each person picks one of the 72 seats. The right answer is **72** bookings.
- Version 1 sold 17 tickets for one seat, and in the spread race 78 tickets for 72 seats.
- Versions 2 and 3 sold exactly 1 and exactly 72, in every one of their races.
- 0 request errors in all 18 recorded races.

**How to read the speed numbers honestly.** Everything ran on one laptop: the load test, the Spring Boot app, PostgreSQL and Redis (the last two inside WSL). 1,000 simultaneous requests compete for that one machine, so the absolute milliseconds are inflated. Compare the versions with each other, not with a real production server. Raw data: [`docs/results/results.csv`](docs/results/results.csv).

**Why does v1 never sell more than about 20 copies?** The app has 20 database connections. Only requests that are *in the middle of booking at the same moment* can collide, and at most 20 can be at that point at once. The numbers I saw across runs were 4, 11, 17 and 20.

## Read the code in this order

Each file is short and has comments written for a learner.

1. [`V1__schema.sql`](backend/src/main/resources/db/migration/V1__schema.sql): the two tables. Note that `booking.seat_id` is **not** unique, on purpose.
2. [`NaiveBookingService`](backend/src/main/java/com/tatkal/booking/NaiveBookingService.java): version 1, and the comment marking **the gap** where the bug lives.
3. [`LockedBookingService`](backend/src/main/java/com/tatkal/booking/LockedBookingService.java) and [`SeatRepository`](backend/src/main/java/com/tatkal/seat/SeatRepository.java): version 2. The only change is one locked read.
4. [`HoldBookingService`](backend/src/main/java/com/tatkal/booking/HoldBookingService.java): version 3, with the one Redis command that does the job (`setIfAbsent` = `SET key value NX EX 300`).
5. [`LoadTest.java`](loadtest/LoadTest.java): how 1,000 people are fired at the same instant (a "start gun" latch plus virtual threads).
6. Tests: [`LockedBookingConcurrencyTest`](backend/src/test/java/com/tatkal/LockedBookingConcurrencyTest.java) and [`HoldBookingServiceTest`](backend/src/test/java/com/tatkal/HoldBookingServiceTest.java).
7. [`frontend/src/App.jsx`](frontend/src/App.jsx): the seat map, the hold countdown and the pay button.

## How version 3 works

```mermaid
sequenceDiagram
    participant A as Asha
    participant B as Ravi
    participant API as Spring Boot
    participant R as Redis
    participant DB as PostgreSQL
    A->>API: hold seat 10
    B->>API: hold seat 10
    API->>R: SET hold:seat:10 asha NX EX 300
    R-->>API: OK (Asha won)
    API->>R: SET hold:seat:10 ravi NX EX 300
    R-->>API: nothing (key exists)
    API-->>A: 200, you have 5:00
    API-->>B: 409, held by someone else
    A->>API: pay (confirm seat 10)
    API->>R: is Asha the holder?
    API->>DB: UPDATE seat SET status=BOOKED WHERE id=10 AND status=AVAILABLE
    DB-->>API: 1 row changed
    API->>DB: INSERT booking
    API->>R: delete the hold
    API-->>A: 200, ticket number
```

If Asha never pays, Redis deletes the key after 5 minutes and the seat is free for everyone again.

## Run it yourself

You need Java 21, Node 22, PostgreSQL 16 and Redis 7. The simplest way is Docker:

```
docker compose up --build
```

Then open http://localhost. Open the page in two browser tabs: each tab is a different person, so you can watch one hold a seat while the other sees it as taken.

Without Docker (what I did on Windows, with PostgreSQL and Redis inside WSL):

```
# one-time: create the database user and databases
sudo -u postgres psql -c "CREATE USER tatkal WITH PASSWORD 'tatkal' CREATEDB;" -c "CREATE DATABASE tatkal OWNER tatkal;" -c "CREATE DATABASE tatkal_test OWNER tatkal;"

cd backend && ./mvnw spring-boot:run        # API on http://localhost:8080 (Swagger at /swagger-ui.html)
cd frontend && npm install && npm run dev   # page on http://localhost:5173
```

Run the tests with `cd backend && ./mvnw verify`.

Run the race (with the backend running):

```
java loadtest/LoadTest.java http://localhost:8080 v1 1000 same-seat
java loadtest/LoadTest.java http://localhost:8080 v2 1000 same-seat
java loadtest/LoadTest.java http://localhost:8080 v3 1000 same-seat
```

Or the whole experiment (1 warm-up + 3 recorded races for every version and scenario), then the tables:

```
bash loadtest/run-all.sh
node loadtest/summarize.mjs
```

## CI

GitHub Actions runs three jobs on every push: backend tests against real PostgreSQL and Redis, the frontend build, and a docker-compose smoke test that starts the whole system and races 200 users. **The build fails if version 2 or version 3 ever sells a seat twice.**

## Interview talking points

**"How do you stop two people booking the same seat?"** Walk through the three versions in the table. The answer is: make "check if free" and "mark as taken" one indivisible step.

- **The bug has a name.** v1 is a *check-then-act race condition*. Between "is it free?" and "mark it booked", other requests slip in.
- **Why not `synchronized` in Java?** It only protects one running copy of the app. With two servers behind a load balancer, each has its own lock and both sell the seat. The lock has to live somewhere shared: the database (v2) or Redis (v3).
- **v2 trade-off.** Correct and simple, but everyone wanting the same seat waits in a queue, holding a database connection while they wait. That is a risk when thousands arrive.
- **v3 trade-off.** Losers are turned away in one fast Redis call without touching the database. The cost is an extra moving part, and a hold that can expire.
- **Redis is atomic.** It runs one command at a time, so `SET NX` has exactly one winner out of 1,000.
- **What if Redis loses the holds (restart or crash)?** Confirming still runs `UPDATE ... WHERE status = 'AVAILABLE'`, so the database refuses a second booking of the same seat. Redis is the fast front door; PostgreSQL stays the final judge.
- **The pool-size observation.** v1 never sold more than about 20 copies because the pool has 20 connections. A good example of reading your own results.

**What I would add in production** (deliberately left out to keep this readable):
- `UNIQUE (seat_id)` on `booking`, so even v1 would fail loudly instead of silently double-booking.
- Deleting the hold only if it still belongs to the confirming user, as one atomic Redis step (a small Lua script). Today `confirm` checks the holder and then deletes in two steps.
- Optimistic locking (`@Version` on `Seat`) as a fourth strategy: no lock, but losers must retry.
- Real payment, login, many trains and dates, and a way for a user to release a hold early.

**Known limits.** One train, one coach, one date. The seat map refreshes by polling every 2 seconds, not live push. Latency figures come from a single laptop (see above).
