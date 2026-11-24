| Version | Scenario | Seats sold twice | Booking rows | p50 ms | p95 ms | p99 ms | Requests/s (min-max) |
|---|---|---|---|---|---|---|---|
| v1 Naive | same-seat | 1 | 18 | 798 | 1351 | 1503 | 499 (439-637) |
| v2 Row lock | same-seat | 0 | 1 | 1226 | 2597 | 2743 | 350 (269-450) |
| v3 Redis hold | same-seat | 0 | 1 | 513 | 1309 | 1405 | 575 (545-667) |
| v1 Naive | spread | 22 | 99 | 762 | 1629 | 1806 | 541 (465-588) |
| v2 Row lock | spread | 0 | 72 | 601 | 1289 | 1450 | 523 (455-572) |
| v3 Redis hold | spread | 0 | 72 | 1035 | 1757 | 2115 | 420 (329-475) |
