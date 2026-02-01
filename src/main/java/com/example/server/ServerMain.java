package com.example.server;

import com.example.service.ClinicService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.concurrent.*;

public class ServerMain {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getProperty("port", "5050"));
        int p = Integer.parseInt(System.getProperty("p", "10"));
        int verifySec = Integer.parseInt(System.getProperty("verifySec", "5"));
        int runSeconds = Integer.parseInt(System.getProperty("runSeconds", "180"));
        int tPlataSec = Integer.parseInt(System.getProperty("tPlataSec", "20"));
        String outDir = System.getProperty("outDir", "output");

        ExecutorService workerPool = Executors.newFixedThreadPool(p);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        ClinicService service = new ClinicService(tPlataSec, verifySec, scheduler, outDir);
        service.startPeriodicVerification(verifySec);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);

            // shutdown timer (3 minutes)
            scheduler.schedule(() -> {
                System.out.println("Server shutting down...");
                service.initiateShutdown();
                try { serverSocket.close(); } catch (IOException ignored) {}
                workerPool.shutdown();
                scheduler.shutdown();
            }, runSeconds, TimeUnit.SECONDS);

            while (!serverSocket.isClosed()) {
                try {
                    var client = serverSocket.accept();
                    service.registerClient(client);
                    new Thread(new ClientConnection(client, service, workerPool)).start();
                } catch (SocketException se) {
                    break; // closed by shutdown
                }
            }
        }

        System.out.println("Server terminated.");
    }
}
