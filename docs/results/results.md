| Version | Scenario | Seats sold twice | Booking rows | p50 ms | p95 ms | p99 ms | Requests/s |
|---|---|---|---|---|---|---|---|
| v1 Naive | same-seat | 1 | 17 | 1496 | 3476 | 3852 | 149 |
| v2 Row lock | same-seat | 0 | 1 | 2133 | 4367 | 4920 | 120 |
| v3 Redis hold | same-seat | 0 | 1 | 1603 | 2115 | 2456 | 298 |
| v1 Naive | spread | 10 | 78 | 1424 | 3983 | 4379 | 147 |
| v2 Row lock | spread | 0 | 72 | 1857 | 4531 | 5954 | 134 |
| v3 Redis hold | spread | 0 | 72 | 1230 | 2576 | 3092 | 211 |
