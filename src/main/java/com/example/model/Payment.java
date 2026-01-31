package com.example.model;

import java.time.Instant;

public record Payment(long id, Instant date, String cnp, int suma) {}
