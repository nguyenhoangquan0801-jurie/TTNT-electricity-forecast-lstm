package com.electricity.forecast;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ForecastApplication {
    public static void main(String[] args) {
        SpringApplication.run(ForecastApplication.class, args);
        System.out.println("Energy Forecast Application started!");
        System.out.println("Open browser: http://localhost:8080");
    }
}