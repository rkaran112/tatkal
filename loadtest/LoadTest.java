import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fires many "people" at the booking API at the same moment and counts what happened.
 *
 * Run it from the repo root:
 *   java loadtest/LoadTest.java http://localhost:8080 v1 1000 same-seat
 *
 * Arguments:
 *   1. base URL of the backend
 *   2. strategy: v1 (naive), v2 (row lock), v3 (redis hold)
 *   3. number of users (default 1000)
 *   4. scenario: same-seat (everyone wants seat 1) or spread (everyone picks from all 72 seats)
 *   5. optional: no-csv = warm-up run, do not record it in results.csv
 *
 * The result is printed and one line is appended to docs/results/results.csv.
 * The exit code is 1 if v2 or v3 double-booked a seat (so CI can fail the build).
 */
public class LoadTest {

    public static void main(String[] args) throws Exception {
        String baseUrl = args.length > 0 ? args[0] : "http://localhost:8080";
        String strategy = args.length > 1 ? args[1] : "v1";
        int users = args.length > 2 ? Integer.parseInt(args[2]) : 1000;
        String scenario = args.length > 3 ? args[3] : "same-seat";
        // 5th argument "no-csv" = a warm-up run: do the race but do not record it in results.csv
        boolean record = !(args.length > 4 && args[4].equals("no-csv"));

        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        // 1. Start from a clean slate: all seats free, no bookings.
        send(client, HttpRequest.newBuilder(URI.create(baseUrl + "/api/admin/reset"))
                .POST(HttpRequest.BodyPublishers.noBody()).build());

        // 2. Find out which seat ids exist.
        String seatsJson = send(client, HttpRequest.newBuilder(URI.create(baseUrl + "/api/seats")).build()).body();
        List<Long> seatIds = readSeatIds(seatsJson);

        // 3. Get every user ready. Each one waits at the "start gun" (a latch).
        CountDownLatch ready = new CountDownLatch(users);
        CountDownLatch startGun = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        Map<String, AtomicInteger> errorKinds = new ConcurrentHashMap<>();   // what kind of error, and how many
        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        for (int i = 0; i < users; i++) {
            final int userNumber = i;
            // same-seat: everybody wants the first seat. spread: user i picks seat (i mod 72).
            final Long seatId = scenario.equals("same-seat") ? seatIds.get(0) : seatIds.get(i % seatIds.size());

            pool.submit(() -> {
                try {
                    HttpRequest firstRequest;
                    if (strategy.equals("v3")) {
                        // v3 is two steps: hold the seat, then confirm. The first request is the hold.
                        firstRequest = postJson(baseUrl + "/api/v3/hold",
                                "{\"seatId\":" + seatId + ",\"userId\":\"user" + userNumber + "\"}");
                    } else {
                        firstRequest = postJson(baseUrl + "/api/" + strategy + "/book",
                                "{\"seatId\":" + seatId + ",\"passenger\":\"user" + userNumber + "\"}");
                    }

                    ready.countDown();
                    startGun.await();                      // everyone fires together

                    long started = System.nanoTime();
                    HttpResponse<String> response = client.send(firstRequest, HttpResponse.BodyHandlers.ofString());

                    // v3 only: whoever won the hold now "pays" (confirm). The latency includes both steps.
                    if (strategy.equals("v3") && response.statusCode() == 200) {
                        HttpRequest confirmRequest = postJson(baseUrl + "/api/v3/confirm",
                                "{\"seatId\":" + seatId + ",\"userId\":\"user" + userNumber + "\"}");
                        response = client.send(confirmRequest, HttpResponse.BodyHandlers.ofString());
                    }
                    latenciesMs.add((System.nanoTime() - started) / 1_000_000);

                    if (response.statusCode() == 200) success.incrementAndGet();
                    else if (response.statusCode() == 409) conflicts.incrementAndGet();
                    else {
                        errors.incrementAndGet();
                        errorKinds.computeIfAbsent("HTTP " + response.statusCode(), k -> new AtomicInteger()).incrementAndGet();
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                    errorKinds.computeIfAbsent(e.getClass().getSimpleName() + ": " + e.getMessage(), k -> new AtomicInteger()).incrementAndGet();
                }
            });
        }

        ready.await();                                      // all users are lined up
        long wallStart = System.nanoTime();
        startGun.countDown();                               // BANG
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.MINUTES);
        long wallMs = (System.nanoTime() - wallStart) / 1_000_000;

        // 4. Ask the server what really ended up in the database.
        String stats = send(client, HttpRequest.newBuilder(URI.create(baseUrl + "/api/admin/stats")).build()).body();
        int bookings = readNumber(stats, "bookings");
        int doubleBooked = readNumber(stats, "doubleBookedSeats");

        // 5. Report.
        Collections.sort(latenciesMs);
        long p50 = percentile(latenciesMs, 50);
        long p95 = percentile(latenciesMs, 95);
        long p99 = percentile(latenciesMs, 99);
        double reqPerSec = users * 1000.0 / Math.max(wallMs, 1);

        System.out.println();
        System.out.println("strategy=" + strategy + "  scenario=" + scenario + "  users=" + users);
        System.out.println("  responses:  " + success + " ok, " + conflicts + " conflict (409), " + errors + " errors");
        errorKinds.forEach((kind, count) -> System.out.println("  error:      " + count + " x " + kind));
        System.out.println("  database:   " + bookings + " booking rows, " + doubleBooked + " seat(s) sold more than once");
        System.out.println("  latency:    p50=" + p50 + "ms  p95=" + p95 + "ms  p99=" + p99 + "ms");
        System.out.println("  total time: " + wallMs + "ms  (" + String.format("%.0f", reqPerSec) + " requests/second)");

        if (record) {
            appendCsv(Instant.now(), strategy, scenario, users, success.get(), conflicts.get(), errors.get(),
                    bookings, doubleBooked, p50, p95, p99, wallMs, reqPerSec);
        }

        if (!strategy.equals("v1") && doubleBooked > 0) {
            System.out.println("FAIL: " + strategy + " double-booked a seat");
            System.exit(1);
        }
    }

    // ---------- small helpers ----------

    private static HttpRequest postJson(String url, String body) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** Pulls every "id":123 out of the /api/seats JSON. */
    private static List<Long> readSeatIds(String json) {
        List<Long> ids = new ArrayList<>();
        Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(json);
        while (m.find()) {
            ids.add(Long.parseLong(m.group(1)));
        }
        return ids;
    }

    /** Pulls one number out of a small JSON object, for example "bookings":5 */
    private static int readNumber(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\":(\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** The value below which p% of the (sorted) latencies fall. */
    private static long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(index, 0));
    }

    private static void appendCsv(Instant when, String strategy, String scenario, int users, int ok, int conflicts,
                                  int errors, int bookings, int doubleBooked, long p50, long p95, long p99,
                                  long wallMs, double reqPerSec) throws Exception {
        Path file = Path.of("docs", "results", "results.csv");
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) {
            Files.writeString(file, "time,strategy,scenario,users,ok,conflicts,errors,booking_rows,"
                    + "double_booked_seats,p50_ms,p95_ms,p99_ms,total_ms,req_per_sec\n");
        }
        String line = when + "," + strategy + "," + scenario + "," + users + "," + ok + "," + conflicts + ","
                + errors + "," + bookings + "," + doubleBooked + "," + p50 + "," + p95 + "," + p99 + ","
                + wallMs + "," + String.format("%.0f", reqPerSec) + "\n";
        Files.writeString(file, line, StandardOpenOption.APPEND);
    }
}
