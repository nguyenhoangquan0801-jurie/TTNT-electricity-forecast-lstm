package com.electricity.forecast.model;

import java.util.List;

public class ForecastResult {
    private boolean success;
    private String message;
    private List<Double> lstmForecast;
    private List<Double> arimaForecast;
    private List<String> timestamps;
    private String plotImage;
    
    public ForecastResult() {}
    
    public ForecastResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    
    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public List<Double> getLstmForecast() { return lstmForecast; }
    public void setLstmForecast(List<Double> lstmForecast) { this.lstmForecast = lstmForecast; }
    
    public List<Double> getArimaForecast() { return arimaForecast; }
    public void setArimaForecast(List<Double> arimaForecast) { this.arimaForecast = arimaForecast; }
    
    public List<String> getTimestamps() { return timestamps; }
    public void setTimestamps(List<String> timestamps) { this.timestamps = timestamps; }
    
    public String getPlotImage() { return plotImage; }
    public void setPlotImage(String plotImage) { this.plotImage = plotImage; }
}