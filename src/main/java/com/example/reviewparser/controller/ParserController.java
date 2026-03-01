package com.example.reviewparser.controller;

import com.example.reviewparser.service.ParserService;
import com.example.reviewparser.model.Review;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ParserController {

    private final ParserService service;

    public ParserController(ParserService service){
        this.service = service;
    }

    @PostMapping("/parse")
    public String parse(@RequestBody Map<String,String> body){

        String url = body.get("url");

        service.parseAsync(url);   //

        return "Parsing started";
    }

    @GetMapping("/answer")
    public List<Review> answer(){
        return service.getAll();
    }
}