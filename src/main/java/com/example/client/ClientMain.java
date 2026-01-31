package com.example.client;

public class ClientMain {
    public static void main(String[] args) {
        String host = System.getProperty("host", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("port", "5050"));
        int clients = Integer.parseInt(System.getProperty("clients", "10"));

        for (int i = 0; i < clients; i++) {
            new Thread(new ClientWorker(host, port, i)).start();
        }
    }
}
