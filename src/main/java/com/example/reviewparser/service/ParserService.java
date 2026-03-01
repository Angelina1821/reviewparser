package com.example.reviewparser.service;

import com.example.reviewparser.model.Review;
import com.example.reviewparser.repository.ReviewRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Service
public class ParserService {

    private final ReviewRepository repository;
    private final ExecutorService executorService;

    public ParserService(ReviewRepository repository,
                         ExecutorService executorService) {
        this.repository = repository;
        this.executorService = executorService;
    }

    // Запуск парсинга
    public void parseAsync(String url){

        executorService.submit(() -> {

            try {

                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0")
                        .timeout(15000)
                        .get();

                Elements reviews = doc.select(".item[itemprop='review']");

                System.out.println("Reviews found = " + reviews.size());

                List<Review> list = reviews.stream()
                        .limit(10)
                        .map(el -> {

                            Review review = new Review();

                            // Автор
                            String author = el.select("[itemprop='name']").text();

                            // Текст
                            String text = el.select(".review-teaser").text();

                            // Рейтинг
                            String ratingText = el.select(".rating-score span").text();

                            // Дата
                            String date = el.select(".review-postdate span").text();

                            review.setAuthor(author.isEmpty() ? "unknown" : author);
                            review.setText(text.isEmpty() ? "No text" : text);

                            try {
                                review.setRating(
                                        ratingText.isEmpty() ?
                                                5.0 :
                                                Double.parseDouble(ratingText)
                                );
                            } catch(Exception e){
                                review.setRating(5.0);
                            }

                            review.setDate(
                                    date.isEmpty() ?
                                            LocalDate.now().toString() :
                                            date
                            );

                            review.setSourceUrl(url);

                            return review;

                        })
                        .collect(Collectors.toList());

                if(!list.isEmpty()){
                    repository.saveAll(list);
                    System.out.println("Saved reviews = " + list.size());
                }

            } catch (Exception e){
                e.printStackTrace();
            }

        });
    }

    // Получение результатов
    public List<Review> getAll(){

        return repository.findAll()
                .parallelStream()
                .filter(r -> r.getText() != null && !r.getText().isEmpty())
                .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
                .collect(Collectors.toList());
    }
}