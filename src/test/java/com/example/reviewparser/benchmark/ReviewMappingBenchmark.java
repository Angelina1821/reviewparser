package com.example.reviewparser.benchmark;

import com.example.reviewparser.model.Review;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class ReviewMappingBenchmark {

    private List<String> rawReviews;

    @Setup
    public void setup() {
        rawReviews = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            rawReviews.add("author-" + i + ";text-" + i + ";4.5;2025-01-01");
        }
    }

    @Benchmark
    public List<Review> withForLoop() {
        List<Review> result = new ArrayList<>(rawReviews.size());
        for (String raw : rawReviews) {
            result.add(map(raw));
        }
        return result;
    }

    @Benchmark
    public List<Review> withStream() {
        return rawReviews.stream().map(this::map).collect(Collectors.toList());
    }

    @Benchmark
    public List<Review> withParallelStream() {
        return rawReviews.parallelStream().map(this::map).collect(Collectors.toList());
    }

    private Review map(String raw) {
        String[] parts = raw.split(";");
        Review review = new Review();
        review.setAuthor(parts[0]);
        review.setText(parts[1]);
        review.setRating(Double.parseDouble(parts[2]));
        review.setDate(parts[3]);
        review.setSourceUrl("benchmark");
        return review;
    }
}
