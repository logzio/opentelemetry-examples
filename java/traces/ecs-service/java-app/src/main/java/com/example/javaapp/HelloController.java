package com.example.javaapp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public String index() {
        return "Hello from the instrumented Java app!";
    }

    @GetMapping("/hello")
    public String hello() {
        return "Hello, World!";
    }
}