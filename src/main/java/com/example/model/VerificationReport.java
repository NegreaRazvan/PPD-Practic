package com.example.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record VerificationReport(
        Instant now,
        Map<Integer, Integer> soldLoc,
        Map<Integer, List<Long>> unpaid,
        Map<Integer, List<String>> overlaps
) {}
