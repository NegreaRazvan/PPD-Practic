package com.example.repository;

import com.example.model.Payment;
import com.example.model.Reservation;
import com.example.model.VerificationReport;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Storage {
    private final Path baseDir;
    private final Lock fileLock = new ReentrantLock();

    public Storage(String dir) {
        this.baseDir = Paths.get(dir);
        try { Files.createDirectories(baseDir); } catch (IOException ignored) {}
    }

    public void appendPlanificare(Reservation r) {
        appendLine("planificari.txt",
                String.format("PLAN|%d|%s|%s|loc=%d|tr=%d|%s-%s|deadline=%d|status=%s",
                        r.id, r.nume, r.cnp, r.locatie, r.tratament, r.start, r.end,
                        r.payDeadline.toEpochMilli(), r.status));
    }

    public void appendPlata(Payment p) {
        appendLine("plati.txt",
                String.format("PAY|%d|%d|%s|%d",
                        p.id(), p.date().toEpochMilli(), p.cnp(), p.suma()));
    }

    public void appendRefund(Payment p) {
        appendLine("refunds.txt",
                String.format("REFUND|%d|%d|%s|%d",
                        p.id(), p.date().toEpochMilli(), p.cnp(), p.suma()));
    }

    public void appendEvent(String fileName, String line) {
        appendLine(fileName, line);
    }

    public void appendVerification(VerificationReport rep, int verifySec) {
        String file = "verificari_" + verifySec + "s.txt";
        StringBuilder sb = new StringBuilder();
        sb.append("TIME|").append(rep.now().toEpochMilli()).append("\n");
        for (var e : rep.soldLoc().entrySet()) {
            int loc = e.getKey();
            sb.append("LOC|").append(loc).append("|SOLD|").append(e.getValue()).append("\n");
            sb.append("UNPAID|").append(rep.unpaid().get(loc)).append("\n");
            sb.append("OVERLAPS|").append(rep.overlaps().get(loc)).append("\n");
        }
        sb.append("----\n");
        appendLine(file, sb.toString());
    }

    private void appendLine(String fileName, String content) {
        fileLock.lock();
        try {
            Path file = baseDir.resolve(fileName);
            try (BufferedWriter bw = Files.newBufferedWriter(file,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                bw.write(content);
                if (!content.endsWith("\n")) bw.write("\n");
            }
        } catch (IOException ignored) {
        } finally {
            fileLock.unlock();
        }
    }
}
