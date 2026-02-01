package com.example.client;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ClientMain {
    public static void main(String[] args) throws Exception {
        String host = System.getProperty("host", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("port", "5050"));
        int clients = Integer.parseInt(System.getProperty("clients", "10"));
        String tag = System.getProperty("tag", "run"); // ex: 5s / 10s

        Metrics metrics = new Metrics();
        List<Thread> threads = new ArrayList<>();

        long startMs = System.currentTimeMillis();

        for (int i = 0; i < clients; i++) {
            Thread t = new Thread(new ClientWorker(host, port, i, metrics));
            t.start();
            threads.add(t);
        }

        // așteptăm terminarea (clienții ies când serverul dă shutdown / nu mai răspunde)
        for (Thread t : threads) t.join();

        long endMs = System.currentTimeMillis();
        double runtimeSec = Math.max(0.001, (endMs - startMs) / 1000.0);

        Metrics.Stats s = metrics.snapshot();

        double throughputAll = (s.bookCount() + s.payCount() + s.cancelCount()) / runtimeSec;

        String outFile = "output/client_metrics_" + tag + ".txt";
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outFile, true))) {
            bw.write("TIME|" + Instant.now().toEpochMilli()); bw.newLine();
            bw.write(String.format("RUNTIME_SEC|%.3f%n", runtimeSec));

            bw.write(String.format("BOOK_COUNT|%d%n", s.bookCount()));
            bw.write(String.format("PAY_COUNT|%d%n", s.payCount()));
            bw.write(String.format("CANCEL_COUNT|%d%n", s.cancelCount()));
            bw.write(String.format("THROUGHPUT_REQ_PER_SEC|%.3f%n", throughputAll));
            bw.newLine();

            writeSeries(bw, "BOOK", s.book());
            writeSeries(bw, "PAY", s.pay());
            writeSeries(bw, "CANCEL", s.cancel());

            bw.write("----"); bw.newLine();
        }

        System.out.println("Client metrics written to: " + outFile);
    }

    private static void writeSeries(BufferedWriter bw, String name, Metrics.Stats.Series series) throws Exception {
        bw.write(String.format("%s_AVG_MS|%.3f%n", name, series.avgMs()));
        bw.write(String.format("%s_P50_MS|%.3f%n", name, series.p50Ms()));
        bw.write(String.format("%s_P95_MS|%.3f%n", name, series.p95Ms()));
        bw.write(String.format("%s_P99_MS|%.3f%n", name, series.p99Ms()));
        bw.write(String.format("%s_MAX_MS|%.3f%n", name, series.maxMs()));
        bw.newLine();
    }
}
