package com.example.service;

import com.example.model.Payment;
import com.example.model.Reservation;
import com.example.model.Status;
import com.example.model.VerificationReport;
import com.example.repository.Storage;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.Instant;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ClinicService {

    private final ReadWriteLock rw = new ReentrantReadWriteLock();

    private final Map<Long, Reservation> reservations = new HashMap<>();
    private final List<Payment> payments = new ArrayList<>();
    private final List<Payment> refunds = new ArrayList<>();

    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService scheduler;
    private final int tPlataSec;
    private final int verifySec;
    private final Storage storage;

    private final AtomicLong idGen = new AtomicLong(1);
    private volatile boolean shuttingDown = false;

    // Config cerință (n=5, m=5)
    private final int n = 5, m = 5;
    private final int[] costs = {50, 20, 40, 100, 30};
    private final int[] durationsMin = {120, 20, 30, 60, 30};
    private final int[][] cap; // cap[loc][tr] 1-based

    public ClinicService(int tPlataSec, int verifySec, ScheduledExecutorService scheduler, String outDir) {
        this.tPlataSec = tPlataSec;
        this.verifySec = verifySec;
        this.scheduler = scheduler;
        this.storage = new Storage(outDir, verifySec);
        this.cap = buildCapacities();
    }

    private int[][] buildCapacities() {
        int[][] c = new int[n + 1][m + 1];
        int[] base = {0, 3, 1, 1, 2, 1}; // N(1,1)=3, N(1,2)=1, ...
        for (int tr = 1; tr <= m; tr++) c[1][tr] = base[tr];
        for (int loc = 2; loc <= n; loc++) {
            for (int tr = 1; tr <= m; tr++) c[loc][tr] = c[1][tr] * (loc - 1);
        }
        return c;
    }

    public void registerClient(Socket s) { clients.add(s); }
    public void unregisterClient(Socket s) { clients.remove(s); }

    public void initiateShutdown() {
        shuttingDown = true;
        broadcast("SERVER_SHUTDOWN|Server closes now");

        for (Socket s : clients) {
            try { s.close(); } catch (Exception ignored) {}
        }
        clients.clear();
    }

    private void broadcast(String msg) {
        for (Socket s : clients) {
            try {
                PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(s.getOutputStream())), true);
                out.println(msg);
            } catch (Exception ignored) {}
        }
    }

    public void startPeriodicVerification(int verifySec) {
        scheduler.scheduleAtFixedRate(() -> {
            rw.readLock().lock();
            try {
                VerificationReport rep = verify();
                storage.appendVerification(rep, verifySec);
            } finally {
                rw.readLock().unlock();
            }
        }, verifySec, verifySec, TimeUnit.SECONDS);
    }

    // =================== BUSINESS OPS ===================

    public String book(String nume, String cnp, int locatie, int tratament, String hhmm) {
        if (shuttingDown) return "BOOK_FAIL|server_shutting_down";

        try {
            LocalTime start = LocalTime.parse(hhmm);

            if (start.isBefore(LocalTime.of(10, 0)) || start.isAfter(LocalTime.of(18, 0))) {
                return "BOOK_FAIL|outside_hours";
            }

            if (locatie < 1 || locatie > n || tratament < 1 || tratament > m) {
                return "BOOK_FAIL|bad_location_or_treatment";
            }

            int dur = durationsMin[tratament - 1];
            LocalTime end = start.plusMinutes(dur);
            if (end.isAfter(LocalTime.of(18, 0))) return "BOOK_FAIL|ends_after_close";

            rw.writeLock().lock();
            try {
                int current = 0;
                for (Reservation r : reservations.values()) {
                    if (r.status == Status.ANULATA || r.status == Status.EXPIRATA) continue;
                    if (r.locatie == locatie && r.tratament == tratament) {
                        if (overlaps(r.start, r.end, start, end)) current++;
                    }
                }

                if (current >= cap[locatie][tratament]) {
                    return "BOOK_FAIL|no_capacity";
                }

                long id = idGen.getAndIncrement();
                Instant now = Instant.now();
                Instant deadline = now.plusSeconds(tPlataSec);

                Reservation r = new Reservation(id, nume, cnp, locatie, tratament, start, end, now, deadline, Status.REZERVARE);
                reservations.put(id, r);
                storage.appendPlanificare(r);

                // expirare rezervare dacă nu e plătită în T_plata
                scheduler.schedule(() -> expireIfUnpaid(id), tPlataSec, TimeUnit.SECONDS);

                int cost = costs[tratament - 1];
                return "BOOK_OK|" + id + "|" + cost + "|" + deadline.toEpochMilli();
            } finally {
                rw.writeLock().unlock();
            }
        } catch (Exception e) {
            return "BOOK_FAIL|bad_request";
        }
    }

    public String pay(long id, String cnp) {
        if (shuttingDown) return "PAY_FAIL|server_shutting_down";

        rw.writeLock().lock();
        try {
            Reservation r = reservations.get(id);
            if (r == null) return "PAY_FAIL|not_found";
            if (!r.cnp.equals(cnp)) return "PAY_FAIL|cnp_mismatch";

            if (r.status != Status.REZERVARE) return "PAY_FAIL|bad_status_" + r.status;

            if (Instant.now().isAfter(r.payDeadline)) {
                r.status = Status.EXPIRATA;
                storage.appendEvent("EXPIRE_BY_PAY|" + id + "|" + Instant.now().toEpochMilli());
                return "PAY_FAIL|expired";
            }

            r.status = Status.PLATITA;
            int suma = costs[r.tratament - 1];

            Payment p = new Payment(id, Instant.now(), cnp, suma);
            payments.add(p);
            storage.appendPlata(p);

            return "PAY_OK|" + id;
        } finally {
            rw.writeLock().unlock();
        }
    }

    // anulare atomică: update status + stergere plată + refund
    public String cancel(long id, String cnp) {
        if (shuttingDown) return "CANCEL_FAIL|server_shutting_down";

        rw.writeLock().lock();
        try {
            Reservation r = reservations.get(id);
            if (r == null) return "CANCEL_FAIL|not_found";
            if (!r.cnp.equals(cnp)) return "CANCEL_FAIL|cnp_mismatch";
            if (r.status != Status.PLATITA) return "CANCEL_FAIL|bad_status_" + r.status;

            int suma = costs[r.tratament - 1];

            // Remove payment record for this reservation id (simplu)
            payments.removeIf(pp -> pp.id() == id);

            Payment refund = new Payment(id, Instant.now(), cnp, -suma);
            refunds.add(refund);
            storage.appendRefund(refund);

            r.status = Status.ANULATA;
            storage.appendEvent("CANCEL|" + id + "|" + Instant.now().toEpochMilli());

            return "CANCEL_OK|" + id + "|" + suma;
        } finally {
            rw.writeLock().unlock();
        }
    }

    // =================== INTERNALS ===================

    private void expireIfUnpaid(long id) {
        rw.writeLock().lock();
        try {
            Reservation r = reservations.get(id);
            if (r != null && r.status == Status.REZERVARE && Instant.now().isAfter(r.payDeadline)) {
                r.status = Status.EXPIRATA;
                storage.appendEvent("EXPIRE|" + id + "|" + Instant.now().toEpochMilli());
            }
        } finally {
            rw.writeLock().unlock();
        }
    }

    private VerificationReport verify() {
        Instant now = Instant.now();

        Map<Integer, Integer> soldLoc = new HashMap<>();
        Map<Integer, List<Long>> unpaidByLoc = new HashMap<>();
        Map<Integer, List<String>> overlapsByLoc = new HashMap<>();

        for (int loc = 1; loc <= n; loc++) {
            soldLoc.put(loc, 0);
            unpaidByLoc.put(loc, new ArrayList<>());
            overlapsByLoc.put(loc, new ArrayList<>());
        }

        // sold = sum(plati) + sum(refunds negative)
        for (Payment p : payments) {
            Reservation r = reservations.get(p.id());
            if (r != null) soldLoc.compute(r.locatie, (k, v) -> v + p.suma());
        }
        for (Payment rf : refunds) {
            Reservation r = reservations.get(rf.id());
            if (r != null) soldLoc.compute(r.locatie, (k, v) -> v + rf.suma());
        }

        // neplatite (REZERVARE)
        for (Reservation r : reservations.values()) {
            if (r.status == Status.REZERVARE) {
                unpaidByLoc.get(r.locatie).add(r.id);
            }
        }

        // suprapuneri / depasire capacitate (bruteforce simplu)
        for (int loc = 1; loc <= n; loc++) {
            for (int tr = 1; tr <= m; tr++) {
                List<Reservation> act = new ArrayList<>();
                for (Reservation r : reservations.values()) {
                    if (r.locatie == loc && r.tratament == tr &&
                            (r.status == Status.REZERVARE || r.status == Status.PLATITA)) {
                        act.add(r);
                    }
                }

                // pentru fiecare rezervare, numaram cate se suprapun cu ea
                for (Reservation a : act) {
                    int cnt = 0;
                    for (Reservation b : act) {
                        if (overlaps(a.start, a.end, b.start, b.end)) cnt++;
                    }
                    if (cnt > cap[loc][tr]) {
                        overlapsByLoc.get(loc).add("CAP_EXCEEDED|tr=" + tr +
                                "|at=" + a.start + "|count=" + cnt + "|cap=" + cap[loc][tr]);
                        break;
                    }
                }
            }
        }

        return new VerificationReport(now, soldLoc, unpaidByLoc, overlapsByLoc);
    }

    private static boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

}
