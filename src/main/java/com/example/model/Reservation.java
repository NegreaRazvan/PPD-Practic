package com.example.model;

import java.time.Instant;
import java.time.LocalTime;

public class Reservation {
    public final long id;
    public final String nume;
    public final String cnp;
    public final int locatie;
    public final int tratament;
    public final LocalTime start;
    public final LocalTime end;
    public final Instant createdAt;
    public final Instant payDeadline;
    public Status status;

    public Reservation(long id, String nume, String cnp, int locatie, int tratament,
                       LocalTime start, LocalTime end, Instant createdAt, Instant payDeadline,
                       Status status) {
        this.id = id;
        this.nume = nume;
        this.cnp = cnp;
        this.locatie = locatie;
        this.tratament = tratament;
        this.start = start;
        this.end = end;
        this.createdAt = createdAt;
        this.payDeadline = payDeadline;
        this.status = status;
    }
}
