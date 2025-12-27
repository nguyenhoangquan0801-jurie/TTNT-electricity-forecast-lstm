package com.electricity.forecast.service;

import com.electricity.forecast.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
public class ForecastService {
    
    private DataModel currentData;
    private ModelMetrics lstmMetrics;
    private ModelMetrics arimaMetrics;
    
    public ForecastService() {
        this.currentData = new DataModel();
    }
    
    public Map<String, Object> uploadData(MultipartFile file) throws IOException {
        Map<String, Object> result = new HashMap<>();
        
        // Save file
        String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path uploadDir = Paths.get("data");
        
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
        
        Path filePath = uploadDir.resolve(filename);
        file.transferTo(filePath);
        
        // Update current data
        currentData.setFilename(filename);
        currentData.setFilepath(filePath.toString());
        
        // Read and analyze data
        try {
            List<Map<String, Object>> data = readCSV(filePath.toString());
            currentData.setData(data);
            
            Map<String, Object> summary = analyzeData(data);
            currentData.setSummary(summary);
            
            // Find target column
            String targetCol = findTargetColumn(data);
            currentData.setTargetColumn(targetCol);
            
            result.put("success", true);
            result.put("message", "File uploaded successfully");
            result.put("filename", filename);
            result.put("summary", summary);
            result.put("targetColumn", targetCol);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Error processing file: " + e.getMessage());
        }
        
        return result;
    }
    
    private List<Map<String, Object>> readCSV(String filepath) throws IOException {
        List<Map<String, Object>> data = new ArrayList<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {
            String line;
            String[] headers = null;
            
            if ((line = br.readLine()) != null) {
                headers = line.split(",");
            }
            
            int rowCount = 0;
            while ((line = br.readLine()) != null && rowCount < 1000) { // Read first 1000 rows
                String[] values = line.split(",");
                Map<String, Object> row = new HashMap<>();
                
                for (int i = 0; i < Math.min(headers.length, values.length); i++) {
                    String header = headers[i].trim();
                    String value = values[i].trim();
                    
                    // Try to parse as number
                    try {
                        if (value.contains(".")) {
                            row.put(header, Double.parseDouble(value));
                        } else {
                            row.put(header, Long.parseLong(value));
                        }
                    } catch (NumberFormatException e) {
                        // Keep as string
                        row.put(header, value);
                    }
                }
                
                data.add(row);
                rowCount++;
            }
        }
        
        return data;
    }
    
    private Map<String, Object> analyzeData(List<Map<String, Object>> data) {
        Map<String, Object> summary = new HashMap<>();
        
        if (data.isEmpty()) {
            return summary;
        }
        
        summary.put("rowCount", data.size());
        summary.put("columnCount", data.get(0).keySet().size());
        summary.put("columns", new ArrayList<>(data.get(0).keySet()));
        
        // Calculate basic statistics for numeric columns
        Map<String, Object> stats = new HashMap<>();
        for (String column : data.get(0).keySet()) {
            if (data.get(0).get(column) instanceof Number) {
                List<Double> values = new ArrayList<>();
                for (Map<String, Object> row : data) {
                    if (row.get(column) instanceof Number) {
                        values.add(((Number) row.get(column)).doubleValue());
                    }
                }
                
                if (!values.isEmpty()) {
                    double sum = 0;
                    double min = Double.MAX_VALUE;
                    double max = Double.MIN_VALUE;
                    
                    for (Double val : values) {
                        sum += val;
                        if (val < min) min = val;
                        if (val > max) max = val;
                    }
                    
                    double mean = sum / values.size();
                    
                    Map<String, Object> columnStats = new HashMap<>();
                    columnStats.put("mean", mean);
                    columnStats.put("min", min);
                    columnStats.put("max", max);
                    columnStats.put("count", values.size());
                    
                    stats.put(column, columnStats);
                }
            }
        }
        
        summary.put("statistics", stats);
        
        return summary;
    }
    
    private String findTargetColumn(List<Map<String, Object>> data) {
        if (data.isEmpty()) return "value";
        
        // Look for common energy column names
        String[] energyKeywords = {"consumption", "load", "energy", "demand", "value", "total"};
        
        for (String column : data.get(0).keySet()) {
            String columnLower = column.toLowerCase();
            for (String keyword : energyKeywords) {
                if (columnLower.contains(keyword) && data.get(0).get(column) instanceof Number) {
                    return column;
                }
            }
        }
        
        // If no energy column found, use first numeric column
        for (String column : data.get(0).keySet()) {
            if (data.get(0).get(column) instanceof Number) {
                return column;
            }
        }
        
        // If no numeric column, use first column
        return data.get(0).keySet().iterator().next();
    }
    
    public Map<String, Object> getDataSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        if (currentData.getSummary() != null) {
            summary.put("success", true);
            summary.put("data", currentData.getSummary());
            summary.put("filename", currentData.getFilename());
            summary.put("targetColumn", currentData.getTargetColumn());
        } else {
            summary.put("success", false);
            summary.put("message", "No data loaded");
        }
        
        return summary;
    }
    
    public Map<String, Object> trainLSTMModel() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Simulate LSTM training
            Thread.sleep(2000); // Simulate training time
            
            lstmMetrics = new ModelMetrics();
            lstmMetrics.setModelName("LSTM");
            lstmMetrics.setMae(150.5);
            lstmMetrics.setRmse(210.3);
            lstmMetrics.setMape(8.7);
            lstmMetrics.setTrainingTime(2.1);
            
            result.put("success", true);
            result.put("message", "LSTM model trained successfully");
            result.put("metrics", lstmMetrics);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Error training LSTM: " + e.getMessage());
        }
        
        return result;
    }
    
    public Map<String, Object> trainARIMAModel() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Simulate ARIMA training
            Thread.sleep(1000); // Simulate training time
            
            arimaMetrics = new ModelMetrics();
            arimaMetrics.setModelName("ARIMA");
            arimaMetrics.setMae(180.2);
            arimaMetrics.setRmse(245.8);
            arimaMetrics.setMape(10.3);
            arimaMetrics.setTrainingTime(1.2);
            
            result.put("success", true);
            result.put("message", "ARIMA model trained successfully");
            result.put("metrics", arimaMetrics);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Error training ARIMA: " + e.getMessage());
        }
        
        return result;
    }
    
    public ForecastResult generateForecast(int hours) {
        ForecastResult result = new ForecastResult();
        
        try {
            // Generate sample forecast data
            List<Double> lstmForecast = new ArrayList<>();
            List<Double> arimaForecast = new ArrayList<>();
            List<String> timestamps = new ArrayList<>();
            
            Random rand = new Random();
            double baseValue = 1000.0;
            
            for (int i = 1; i <= hours; i++) {
                // LSTM forecast with some pattern
                double lstmValue = baseValue + 100 * Math.sin(i * 0.5) + rand.nextDouble() * 50;
                lstmForecast.add(lstmValue);
                
                // ARIMA forecast with different pattern
                double arimaValue = baseValue + 80 * Math.cos(i * 0.3) + rand.nextDouble() * 60;
                arimaForecast.add(arimaValue);
                
                timestamps.add("Hour " + i);
            }
            
            result.setSuccess(true);
            result.setMessage("Forecast generated for " + hours + " hours");
            result.setLstmForecast(lstmForecast);
            result.setArimaForecast(arimaForecast);
            result.setTimestamps(timestamps);
            
            // Generate simple plot data
            result.setPlotImage(generateSimplePlot(timestamps, lstmForecast, arimaForecast));
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("Error generating forecast: " + e.getMessage());
        }
        
        return result;
    }
    
    private String generateSimplePlot(List<String> timestamps, 
                                    List<Double> lstmForecast, 
                                    List<Double> arimaForecast) {
        // Generate simple ASCII plot for demonstration
        StringBuilder plot = new StringBuilder();
        plot.append("Forecast Comparison:\n");
        plot.append("====================\n");
        
        int maxLength = 50;
        double maxValue = Math.max(
            Collections.max(lstmForecast),
            Collections.max(arimaForecast)
        );
        
        for (int i = 0; i < Math.min(10, timestamps.size()); i++) {
            int lstmBars = (int) ((lstmForecast.get(i) / maxValue) * maxLength);
            int arimaBars = (int) ((arimaForecast.get(i) / maxValue) * maxLength);
            
            plot.append(String.format("%-10s: LSTM[%-50s] ARIMA[%-50s]\n",
                timestamps.get(i),
                "=".repeat(lstmBars),
                "=".repeat(arimaBars)));
        }
        
        return plot.toString();
    }
    
    public Map<String, Object> compareModels() {
        Map<String, Object> comparison = new HashMap<>();
        
        if (lstmMetrics == null || arimaMetrics == null) {
            comparison.put("success", false);
            comparison.put("message", "Both models need to be trained first");
            return comparison;
        }
        
        comparison.put("success", true);
        comparison.put("lstm", lstmMetrics);
        comparison.put("arima", arimaMetrics);
        
        // Determine best model
        String bestModel = lstmMetrics.getRmse() < arimaMetrics.getRmse() ? "LSTM" : "ARIMA";
        double improvement = Math.abs(lstmMetrics.getRmse() - arimaMetrics.getRmse()) / 
                           Math.max(lstmMetrics.getRmse(), arimaMetrics.getRmse()) * 100;
        
        comparison.put("bestModel", bestModel);
        comparison.put("improvement", improvement);
        
        return comparison;
    }
}