package com.example.client;

import java.io.*;
import java.net.Socket;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ClientWorker implements Runnable {
    private final String host;
    private final int port;
    private final int idx;
    private final Random rnd = new Random();
    private final Queue<Long> paidIds = new ConcurrentLinkedQueue<>();
    private final Metrics metrics;

    public ClientWorker(String host, int port, int idx, Metrics metrics) {
        this.host = host;
        this.port = port;
        this.idx = idx;
        this.metrics = metrics;
    }

    @Override
    public void run() {
        String cnp = String.format("500%06d", idx);
        String name = "Client" + idx;

        try (Socket s = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(s.getOutputStream())), true)) {

            while (true) {
                int loc = 1 + rnd.nextInt(5);
                int tr = 1 + rnd.nextInt(5);

                int hour = 10 + rnd.nextInt(8);     // 10..17
                int min = (rnd.nextInt(6)) * 10;    // 0,10,20,30,40,50
                String hhmm = String.format("%02d:%02d", hour, min);

                // --- BOOK (measure latency) ---
                long t0 = System.nanoTime();
                out.println("BOOK|" + name + "|" + cnp + "|" + loc + "|" + tr + "|" + hhmm);
                String resp = in.readLine();
                long t1 = System.nanoTime();
                if (resp == null) break;

                if (resp.startsWith("SERVER_SHUTDOWN")) break;
                if (resp.startsWith("BOOK_FAIL|server_shutting_down")) break;

                metrics.addBook(t1 - t0);

                if (resp.startsWith("BOOK_OK")) {
                    String[] p = resp.split("\\|");
                    long id = Long.parseLong(p[1]);

                    // delay înainte de plată (simulate)
                    Thread.sleep(200 + rnd.nextInt(800));

                    // --- PAY (measure latency) ---
                    long p0 = System.nanoTime();
                    out.println("PAY|" + id + "|" + cnp);
                    String payResp = in.readLine();
                    long p1 = System.nanoTime();
                    if (payResp == null) break;

                    if (payResp.startsWith("SERVER_SHUTDOWN")) break;

                    metrics.addPay(p1 - p0);

                    if (payResp.startsWith("PAY_OK")) {
                        paidIds.add(id);
                    }

                    // 20% anulare
                    if (!paidIds.isEmpty() && rnd.nextInt(100) < 20) {
                        Long cancelId = paidIds.poll();
                        if (cancelId != null) {
                            long c0 = System.nanoTime();
                            out.println("CANCEL|" + cancelId + "|" + cnp);
                            String cResp = in.readLine();
                            long c1 = System.nanoTime();
                            if (cResp == null) break;

                            if (cResp.startsWith("SERVER_SHUTDOWN")) break;

                            metrics.addCancel(c1 - c0);
                        }
                    }
                }

                Thread.sleep(2000);
            }
        } catch (Exception e) {
            // ok pentru test; server poate fi oprit sau conexiunea se închide
        }
    }
}
