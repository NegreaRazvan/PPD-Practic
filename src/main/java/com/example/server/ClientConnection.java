package com.example.server;

import com.example.service.ClinicService;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class ClientConnection implements Runnable {
    private final Socket socket;
    private final ClinicService service;
    private final ExecutorService workerPool;

    public ClientConnection(Socket socket, ClinicService service, ExecutorService workerPool) {
        this.socket = socket;
        this.service = service;
        this.workerPool = workerPool;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                String[] parts = line.split("\\|");
                String cmd = parts[0];

                if ("BOOK".equals(cmd)) {
                    // BOOK|nume|cnp|loc|tr|HH:MM
                    CompletableFuture
                            .supplyAsync(() -> service.book(
                                    parts[1],
                                    parts[2],
                                    Integer.parseInt(parts[3]),
                                    Integer.parseInt(parts[4]),
                                    parts[5]
                            ), workerPool)
                            .thenAccept(out::println);

                } else if ("PAY".equals(cmd)) {
                    // PAY|reservationId|cnp
                    String resp = service.pay(Long.parseLong(parts[1]), parts[2]);
                    out.println(resp);

                } else if ("CANCEL".equals(cmd)) {
                    // CANCEL|reservationId|cnp
                    String resp = service.cancel(Long.parseLong(parts[1]), parts[2]);
                    out.println(resp);

                } else if ("BYE".equals(cmd)) {
                    out.println("BYE_OK");
                    break;

                } else {
                    out.println("ERR|Unknown command");
                }
            }
        } catch (IOException ignored) {
            // disconnected
        } finally {
            service.unregisterClient(socket);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
