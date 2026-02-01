package com.example.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

public class Metrics {
    private final List<Long> bookNs = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> payNs  = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> cancelNs = Collections.synchronizedList(new ArrayList<>());

    private final LongAdder bookCount = new LongAdder();
    private final LongAdder payCount = new LongAdder();
    private final LongAdder cancelCount = new LongAdder();

    public void addBook(long ns)   { bookNs.add(ns); bookCount.increment(); }
    public void addPay(long ns)    { payNs.add(ns); payCount.increment(); }
    public void addCancel(long ns) { cancelNs.add(ns); cancelCount.increment(); }

    public long getBookCount() { return bookCount.sum(); }
    public long getPayCount() { return payCount.sum(); }
    public long getCancelCount() { return cancelCount.sum(); }

    public Stats snapshot() {
        return new Stats(
                Stats.from(bookNs),
                Stats.from(payNs),
                Stats.from(cancelNs),
                getBookCount(), getPayCount(), getCancelCount()
        );
    }

    public record Stats(Series book, Series pay, Series cancel,
                        long bookCount, long payCount, long cancelCount) {

        public static Stats.Series emptySeries() {
            return new Series(0,0,0,0,0);
        }

        public record Series(double avgMs, double p50Ms, double p95Ms, double p99Ms, double maxMs) {}

        static Series from(List<Long> nsList) {
            if (nsList.isEmpty()) return new Series(0,0,0,0,0);
            long[] arr;
            synchronized (nsList) {
                arr = nsList.stream().mapToLong(x -> x).toArray();
            }
            java.util.Arrays.sort(arr);

            double avg = java.util.Arrays.stream(arr).average().orElse(0) / 1_000_000.0;
            double p50 = percentile(arr, 0.50) / 1_000_000.0;
            double p95 = percentile(arr, 0.95) / 1_000_000.0;
            double p99 = percentile(arr, 0.99) / 1_000_000.0;
            double max = arr[arr.length - 1] / 1_000_000.0;

            return new Series(avg, p50, p95, p99, max);
        }

        static long percentile(long[] sorted, double p) {
            if (sorted.length == 0) return 0;
            double idx = p * (sorted.length - 1);
            int lo = (int) Math.floor(idx);
            int hi = (int) Math.ceil(idx);
            if (lo == hi) return sorted[lo];
            double w = idx - lo;
            return (long) (sorted[lo] * (1 - w) + sorted[hi] * w);
        }
    }
}
