package com.example.reviewparser.service;

import com.example.reviewparser.model.Review;
import com.example.reviewparser.repository.ReviewRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Service
public class ParserService {

    private static final Logger log = LoggerFactory.getLogger(ParserService.class);

    private final ReviewRepository repository;
    private final ExecutorService executorService;
    private final ObservationRegistry observationRegistry;

    private final Counter successParsesCounter;
    private final Counter failedParsesCounter;
    private final Counter savedReviewsCounter;
    private final Timer parseDurationTimer;

    public ParserService(ReviewRepository repository,
                         ExecutorService executorService,
                         MeterRegistry meterRegistry,
                         ObservationRegistry observationRegistry) {
        this.repository = repository;
        this.executorService = executorService;
        this.observationRegistry = observationRegistry;

        this.successParsesCounter = meterRegistry.counter("review_parser.parse.success.total");
        this.failedParsesCounter = meterRegistry.counter("review_parser.parse.error.total");
        this.savedReviewsCounter = meterRegistry.counter("review_parser.db.records.saved.total");
        this.parseDurationTimer = Timer.builder("review_parser.parse.duration")
                .description("Total parse duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    public void parseAsync(String url){
        executorService.submit(() -> parse(url));
    }

    private void parse(String url) {
        Timer.Sample sample = Timer.start();
        try (Observation.Scope ignored = Observation.start("review_parser.parse", observationRegistry)
                .lowCardinalityKeyValue("source", url)
                .openScope()) {

            List<Review> parsedReviews = parsePage(url);
            saveReviews(parsedReviews);

            successParsesCounter.increment();
            log.info("Parsing completed successfully, url={}, parsed={}, saved={}",
                    url, parsedReviews.size(), parsedReviews.size());
        } catch (Exception e){
            failedParsesCounter.increment();
            log.error("Parsing failed for url={}", url, e);
        } finally {
            sample.stop(parseDurationTimer);
        }
    }

    private List<Review> parsePage(String url) throws Exception {
        try (Observation.Scope ignored = Observation.start("review_parser.parse.download", observationRegistry)
                .lowCardinalityKeyValue("source", url)
                .openScope()) {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(15000)
                    .get();

            Elements reviews = doc.select(".item[itemprop='review']");
            log.info("Reviews found = {}", reviews.size());

            return mapReviews(url, reviews);
        }
    }

    private List<Review> mapReviews(String url, Elements reviews) {
        try (Observation.Scope ignored = Observation.start("review_parser.parse.transform", observationRegistry).openScope()) {
            List<Review> mappedReviews = new ArrayList<>(Math.min(reviews.size(), 10));

            int maxSize = Math.min(reviews.size(), 10);
            for (int i = 0; i < maxSize; i++) {
                Element el = reviews.get(i);
                Review review = new Review();

                String author = el.select("[itemprop='name']").text();
                String text = el.select(".review-teaser").text();
                String ratingText = el.select(".rating-score span").text();
                String date = el.select(".review-postdate span").text();

                review.setAuthor(author.isEmpty() ? "unknown" : author);
                review.setText(text.isEmpty() ? "No text" : text);

                try {
                    review.setRating(ratingText.isEmpty() ? 5.0 : Double.parseDouble(ratingText));
                } catch(Exception e){
                    review.setRating(5.0);
                }

                review.setDate(date.isEmpty() ? LocalDate.now().toString() : date);
                review.setSourceUrl(url);
                mappedReviews.add(review);
            }
            return mappedReviews;
        }
    }

    private void saveReviews(List<Review> list) {
        try (Observation.Scope ignored = Observation.start("review_parser.parse.persist", observationRegistry).openScope()) {
            if (!list.isEmpty()) {
                repository.saveAll(list);
                savedReviewsCounter.increment(list.size());
                log.info("Saved reviews = {}", list.size());
            }
        }
    }

    public List<Review> getAll(){
        return repository.findAll().stream()
                .filter(r -> r.getText() != null && !r.getText().isEmpty())
                .sorted(Comparator.comparing(Review::getDate, Comparator.nullsLast(String::compareTo)).reversed())
                .toList();
    }
}
