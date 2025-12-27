package com.electricity.forecast.controller;

import com.electricity.forecast.model.ForecastResult;
import com.electricity.forecast.service.ForecastService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Controller
public class ForecastController {
    
    @Autowired
    private ForecastService forecastService;
    
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("pageTitle", "Energy Consumption Forecast");
        return "index";
    }
    
    @PostMapping("/upload")
    @ResponseBody
    public Map<String, Object> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            return forecastService.uploadData(file);
        } catch (Exception e) {
            return Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            );
        }
    }
    
    @GetMapping("/summary")
    @ResponseBody
    public Map<String, Object> getSummary() {
        return forecastService.getDataSummary();
    }
    
    @PostMapping("/train/lstm")
    @ResponseBody
    public Map<String, Object> trainLSTM() {
        return forecastService.trainLSTMModel();
    }
    
    @PostMapping("/train/arima")
    @ResponseBody
    public Map<String, Object> trainARIMA() {
        return forecastService.trainARIMAModel();
    }
    
    @GetMapping("/forecast")
    @ResponseBody
    public ForecastResult getForecast(@RequestParam(defaultValue = "24") int hours) {
        return forecastService.generateForecast(hours);
    }
    
    @GetMapping("/compare")
    @ResponseBody
    public Map<String, Object> compareModels() {
        return forecastService.compareModels();
    }
}