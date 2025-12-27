package com.electricity.forecast.model;

public class ModelMetrics {
    private String modelName;
    private double mae;
    private double rmse;
    private double mape;
    private double trainingTime;
    
    // Getters and Setters
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    
    public double getMae() { return mae; }
    public void setMae(double mae) { this.mae = mae; }
    
    public double getRmse() { return rmse; }
    public void setRmse(double rmse) { this.rmse = rmse; }
    
    public double getMape() { return mape; }
    public void setMape(double mape) { this.mape = mape; }
    
    public double getTrainingTime() { return trainingTime; }
    public void setTrainingTime(double trainingTime) { this.trainingTime = trainingTime; }
}