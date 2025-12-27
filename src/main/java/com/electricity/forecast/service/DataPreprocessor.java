package com.electricity.forecast.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class DataPreprocessor {
    
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };
    
    /**
     * Tiền xử lý dữ liệu thô từ CSV
     */
    public List<Map<String, Object>> preprocessData(List<Map<String, Object>> rawData, String targetColumn) {
        if (rawData == null || rawData.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 1. Làm sạch dữ liệu cơ bản
        List<Map<String, Object>> cleanedData = cleanBasicData(rawData);
        
        // 2. Xác định và chuẩn hóa cột thời gian
        cleanedData = normalizeTimeColumn(cleanedData);
        
        // 3. Xử lý giá trị thiếu
        cleanedData = handleMissingValues(cleanedData, targetColumn);
        
        // 4. Xử lý ngoại lệ (outliers)
        cleanedData = handleOutliers(cleanedData, targetColumn);
        
        // 5. Tạo đặc trưng thời gian
        cleanedData = createTimeFeatures(cleanedData);
        
        // 6. Chuẩn hóa dữ liệu số
        cleanedData = normalizeNumericalData(cleanedData, targetColumn);
        
        return cleanedData;
    }
    
    /**
     * 1. Làm sạch dữ liệu cơ bản
     */
    private List<Map<String, Object>> cleanBasicData(List<Map<String, Object>> data) {
        List<Map<String, Object>> cleaned = new ArrayList<>();
        
        for (Map<String, Object> row : data) {
            Map<String, Object> cleanRow = new HashMap<>();
            
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey().trim().toLowerCase();
                Object value = entry.getValue();
                
                // Xử lý giá trị null hoặc rỗng
                if (value == null || value.toString().trim().isEmpty()) {
                    cleanRow.put(key, null);
                } else {
                    // Chuẩn hóa string
                    if (value instanceof String) {
                        String strValue = ((String) value).trim();
                        // Thử parse thành số nếu có thể
                        try {
                            if (strValue.contains(".")) {
                                cleanRow.put(key, Double.parseDouble(strValue));
                            } else {
                                cleanRow.put(key, Long.parseLong(strValue));
                            }
                        } catch (NumberFormatException e) {
                            cleanRow.put(key, strValue);
                        }
                    } else {
                        cleanRow.put(key, value);
                    }
                }
            }
            cleaned.add(cleanRow);
        }
        
        return cleaned;
    }
    
    /**
     * 2. Chuẩn hóa cột thời gian
     */
    private List<Map<String, Object>> normalizeTimeColumn(List<Map<String, Object>> data) {
        if (data.isEmpty()) return data;
        
        // Tìm cột thời gian
        String timeColumn = findTimeColumn(data.get(0));
        if (timeColumn == null) {
            // Nếu không có cột thời gian, tạo timestamp tự động
            return addAutoTimestamp(data);
        }
        
        // Chuẩn hóa giá trị thời gian
        for (Map<String, Object> row : data) {
            Object timeValue = row.get(timeColumn);
            if (timeValue != null) {
                try {
                    LocalDateTime dateTime = parseDateTime(timeValue.toString());
                    if (dateTime != null) {
                        row.put(timeColumn, dateTime);
                        row.put("timestamp", dateTime); // Thêm cột timestamp chuẩn
                    }
                } catch (Exception e) {
                    // Giữ nguyên nếu không parse được
                }
            }
        }
        
        // Sắp xếp theo thời gian
        data.sort((row1, row2) -> {
            LocalDateTime t1 = (LocalDateTime) row1.get("timestamp");
            LocalDateTime t2 = (LocalDateTime) row2.get("timestamp");
            if (t1 == null || t2 == null) return 0;
            return t1.compareTo(t2);
        });
        
        return data;
    }
    
    /**
     * 3. Xử lý giá trị thiếu (Missing Values)
     */
    private List<Map<String, Object>> handleMissingValues(List<Map<String, Object>> data, String targetColumn) {
        if (data.isEmpty()) return data;
        
        // Phân tích missing values
        Map<String, Integer> missingCounts = new HashMap<>();
        for (String column : data.get(0).keySet()) {
            int missing = 0;
            for (Map<String, Object> row : data) {
                if (row.get(column) == null) missing++;
            }
            missingCounts.put(column, missing);
        }
        
        // Xử lý missing values cho cột mục tiêu
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Map<String, Object> row : data) {
            Map<String, Object> processedRow = new HashMap<>(row);
            
            for (String column : row.keySet()) {
                Object value = row.get(column);
                
                if (value == null) {
                    // Chiến lược xử lý missing values
                    if (column.equals(targetColumn)) {
                        // Cột mục tiêu: sử dụng interpolation hoặc fill forward/backward
                        processedRow.put(column, interpolateMissingValue(data, result, column, result.size()));
                    } else if (missingCounts.get(column) > data.size() * 0.3) {
                        // Nhiều hơn 30% missing: xóa cột
                        processedRow.remove(column);
                    } else if (column.equals("timestamp")) {
                        // Không xử lý timestamp missing
                        continue;
                    } else {
                        // Cột khác: fill với mean/median/mode
                        processedRow.put(column, fillMissingValue(data, column));
                    }
                }
            }
            result.add(processedRow);
        }
        
        return result;
    }
    
    /**
     * 4. Xử lý ngoại lệ (Outliers)
     */
    private List<Map<String, Object>> handleOutliers(List<Map<String, Object>> data, String targetColumn) {
        if (data.isEmpty() || targetColumn == null) return data;
        
        // Chỉ xử lý nếu cột mục tiêu là số
        if (!isNumericColumn(data, targetColumn)) {
            return data;
        }
        
        // Tính IQR (Interquartile Range)
        List<Double> values = data.stream()
            .map(row -> row.get(targetColumn))
            .filter(Objects::nonNull)
            .map(value -> {
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        if (values.size() < 10) return data; // Không đủ dữ liệu
        
        Collections.sort(values);
        
        double q1 = values.get((int) (values.size() * 0.25));
        double q3 = values.get((int) (values.size() * 0.75));
        double iqr = q3 - q1;
        double lowerBound = q1 - 1.5 * iqr;
        double upperBound = q3 + 1.5 * iqr;
        
        // Thay thế outliers bằng median
        double median = values.get(values.size() / 2);
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : data) {
            Map<String, Object> processedRow = new HashMap<>(row);
            Object value = row.get(targetColumn);
            
            if (value instanceof Number) {
                double numValue = ((Number) value).doubleValue();
                if (numValue < lowerBound || numValue > upperBound) {
                    // Thay thế outlier bằng median
                    processedRow.put(targetColumn, median);
                }
            }
            result.add(processedRow);
        }
        
        return result;
    }
    
    /**
     * 5. Tạo đặc trưng thời gian
     */
    private List<Map<String, Object>> createTimeFeatures(List<Map<String, Object>> data) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Map<String, Object> row : data) {
            Map<String, Object> enhancedRow = new HashMap<>(row);
            
            Object timestamp = row.get("timestamp");
            if (timestamp instanceof LocalDateTime) {
                LocalDateTime dt = (LocalDateTime) timestamp;
                
                // Các đặc trưng cơ bản
                enhancedRow.put("hour", dt.getHour());
                enhancedRow.put("day_of_week", dt.getDayOfWeek().getValue()); // 1-7
                enhancedRow.put("day_of_month", dt.getDayOfMonth());
                enhancedRow.put("month", dt.getMonthValue());
                enhancedRow.put("year", dt.getYear());
                enhancedRow.put("is_weekend", 
                    dt.getDayOfWeek() == DayOfWeek.SATURDAY || 
                    dt.getDayOfWeek() == DayOfWeek.SUNDAY);
                
                // Cyclical encoding cho giờ
                enhancedRow.put("hour_sin", Math.sin(2 * Math.PI * dt.getHour() / 24));
                enhancedRow.put("hour_cos", Math.cos(2 * Math.PI * dt.getHour() / 24));
                
                // Cyclical encoding cho ngày trong tuần
                enhancedRow.put("day_sin", Math.sin(2 * Math.PI * dt.getDayOfWeek().getValue() / 7));
                enhancedRow.put("day_cos", Math.cos(2 * Math.PI * dt.getDayOfWeek().getValue() / 7));
                
                // Đặc trưng mùa
                enhancedRow.put("season", getSeason(dt.getMonthValue()));
                
                // Giờ trong ngày phân loại
                enhancedRow.put("time_of_day", getTimeOfDay(dt.getHour()));
            }
            
            result.add(enhancedRow);
        }
        
        return result;
    }
    
    /**
     * 6. Chuẩn hóa dữ liệu số
     */
    private List<Map<String, Object>> normalizeNumericalData(List<Map<String, Object>> data, String targetColumn) {
        if (data.isEmpty()) return data;
        
        // Xác định các cột số
        Set<String> numericColumns = new HashSet<>();
        Map<String, Object> firstRow = data.get(0);
        
        for (String column : firstRow.keySet()) {
            if (isNumericColumn(data, column) && !column.equals("timestamp")) {
                numericColumns.add(column);
            }
        }
        
        // Tính min, max, mean, std cho mỗi cột số
        Map<String, Double> columnMeans = new HashMap<>();
        Map<String, Double> columnStds = new HashMap<>();
        Map<String, Double> columnMins = new HashMap<>();
        Map<String, Double> columnMaxs = new HashMap<>();
        
        for (String column : numericColumns) {
            List<Double> values = extractNumericValues(data, column);
            if (!values.isEmpty()) {
                columnMeans.put(column, calculateMean(values));
                columnStds.put(column, calculateStd(values));
                columnMins.put(column, Collections.min(values));
                columnMaxs.put(column, Collections.max(values));
            }
        }
        
        // Chuẩn hóa dữ liệu (Standardization)
        List<Map<String, Object>> normalizedData = new ArrayList<>();
        
        for (Map<String, Object> row : data) {
            Map<String, Object> normalizedRow = new HashMap<>(row);
            
            for (String column : numericColumns) {
                Object value = row.get(column);
                if (value instanceof Number) {
                    double numValue = ((Number) value).doubleValue();
                    double mean = columnMeans.get(column);
                    double std = columnStds.get(column);
                    
                    if (std > 0) {
                        // Standardization: (x - mean) / std
                        double normalized = (numValue - mean) / std;
                        normalizedRow.put(column + "_scaled", normalized);
                    }
                    
                    // Lưu giá trị gốc để inverse transform sau
                    normalizedRow.put(column + "_original", numValue);
                }
            }
            
            normalizedData.add(normalizedRow);
        }
        
        return normalizedData;
    }
    
    // ========== CÁC PHƯƠNG THỨC HỖ TRỢ ==========
    
    private String findTimeColumn(Map<String, Object> row) {
        List<String> timeKeywords = Arrays.asList(
            "time", "timestamp", "date", "datetime", 
            "utc", "hour", "period"
        );
        
        for (String column : row.keySet()) {
            String columnLower = column.toLowerCase();
            for (String keyword : timeKeywords) {
                if (columnLower.contains(keyword)) {
                    return column;
                }
            }
        }
        return null;
    }
    
    private LocalDateTime parseDateTime(String dateString) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(dateString, formatter);
            } catch (Exception e) {
                // Thử formatter tiếp theo
            }
        }
        return null;
    }
    
    private boolean isNumericColumn(List<Map<String, Object>> data, String column) {
        if (data.isEmpty()) return false;
        
        int numericCount = 0;
        int totalCount = 0;
        
        for (Map<String, Object> row : data) {
            Object value = row.get(column);
            if (value != null) {
                totalCount++;
                if (value instanceof Number) {
                    numericCount++;
                }
            }
        }
        
        return totalCount > 0 && (numericCount * 1.0 / totalCount) > 0.7;
    }
    
    private List<Double> extractNumericValues(List<Map<String, Object>> data, String column) {
        return data.stream()
            .map(row -> row.get(column))
            .filter(Objects::nonNull)
            .filter(value -> value instanceof Number)
            .map(value -> ((Number) value).doubleValue())
            .collect(Collectors.toList());
    }
    
    private double calculateMean(List<Double> values) {
        return values.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
    }
    
    private double calculateStd(List<Double> values) {
        double mean = calculateMean(values);
        double variance = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0.0);
        return Math.sqrt(variance);
    }
    
    private String getSeason(int month) {
        if (month >= 3 && month <= 5) return "spring";
        if (month >= 6 && month <= 8) return "summer";
        if (month >= 9 && month <= 11) return "autumn";
        return "winter";
    }
    
    private String getTimeOfDay(int hour) {
        if (hour >= 0 && hour < 6) return "night";
        if (hour >= 6 && hour < 12) return "morning";
        if (hour >= 12 && hour < 18) return "afternoon";
        return "evening";
    }
    
    private List<Map<String, Object>> addAutoTimestamp(List<Map<String, Object>> data) {
        LocalDateTime startTime = LocalDateTime.now().minusHours(data.size());
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> row = new HashMap<>(data.get(i));
            row.put("timestamp", startTime.plusHours(i));
            result.add(row);
        }
        
        return result;
    }
    
    private Object interpolateMissingValue(List<Map<String, Object>> allData, 
                                          List<Map<String, Object>> processedData, 
                                          String column, int currentIndex) {
        // Linear interpolation cho time series
        if (allData.size() < 2 || currentIndex == 0) {
            return fillMissingValue(allData, column);
        }
        
        // Tìm giá trị trước và sau
        Double before = null;
        Double after = null;
        
        for (int i = currentIndex - 1; i >= 0 && before == null; i--) {
            Object val = processedData.get(i).get(column);
            if (val instanceof Number) {
                before = ((Number) val).doubleValue();
            }
        }
        
        for (int i = currentIndex + 1; i < allData.size() && after == null; i++) {
            Object val = allData.get(i).get(column);
            if (val instanceof Number) {
                after = ((Number) val).doubleValue();
            }
        }
        
        if (before != null && after != null) {
            // Linear interpolation
            return (before + after) / 2;
        } else if (before != null) {
            return before;
        } else if (after != null) {
            return after;
        }
        
        return fillMissingValue(allData, column);
    }
    
    private Object fillMissingValue(List<Map<String, Object>> data, String column) {
        // Tính median cho cột
        List<Double> values = extractNumericValues(data, column);
        if (!values.isEmpty()) {
            Collections.sort(values);
            return values.get(values.size() / 2); // Median
        }
        return 0.0;
    }
    
    /**
     * Lấy thông tin tiền xử lý để hiển thị
     */
    public Map<String, Object> getPreprocessingInfo(List<Map<String, Object>> rawData, 
                                                   List<Map<String, Object>> processedData,
                                                   String targetColumn) {
        Map<String, Object> info = new HashMap<>();
        
        if (rawData.isEmpty() || processedData.isEmpty()) {
            return info;
        }
        
        // Thống kê missing values trước/sau
        Map<String, Integer> missingBefore = countMissingValues(rawData);
        Map<String, Integer> missingAfter = countMissingValues(processedData);
        
        info.put("rows_before", rawData.size());
        info.put("rows_after", processedData.size());
        info.put("columns_before", rawData.get(0).keySet().size());
        info.put("columns_after", processedData.get(0).keySet().size());
        info.put("missing_before", missingBefore);
        info.put("missing_after", missingAfter);
        
        // Thống kê outliers nếu có cột mục tiêu
        if (targetColumn != null && isNumericColumn(rawData, targetColumn)) {
            List<Double> originalValues = extractNumericValues(rawData, targetColumn);
            List<Double> processedValues = extractNumericValues(processedData, targetColumn);
            
            if (!originalValues.isEmpty()) {
                Collections.sort(originalValues);
                double q1 = originalValues.get((int) (originalValues.size() * 0.25));
                double q3 = originalValues.get((int) (originalValues.size() * 0.75));
                double iqr = q3 - q1;
                double lowerBound = q1 - 1.5 * iqr;
                double upperBound = q3 + 1.5 * iqr;
                
                long outliers = originalValues.stream()
                    .filter(v -> v < lowerBound || v > upperBound)
                    .count();
                
                info.put("outliers_detected", outliers);
                info.put("outlier_percentage", (outliers * 100.0) / originalValues.size());
            }
        }
        
        // Danh sách features được tạo
        Set<String> originalColumns = rawData.get(0).keySet();
        Set<String> processedColumns = processedData.get(0).keySet();
        Set<String> newFeatures = new HashSet<>(processedColumns);
        newFeatures.removeAll(originalColumns);
        
        info.put("new_features", new ArrayList<>(newFeatures));
        info.put("time_features_created", 
            newFeatures.stream()
                .filter(f -> f.contains("hour") || f.contains("day") || 
                           f.contains("month") || f.contains("season"))
                .collect(Collectors.toList()));
        
        return info;
    }
    
    private Map<String, Integer> countMissingValues(List<Map<String, Object>> data) {
        Map<String, Integer> counts = new HashMap<>();
        
        if (data.isEmpty()) return counts;
        
        for (String column : data.get(0).keySet()) {
            int missing = 0;
            for (Map<String, Object> row : data) {
                if (row.get(column) == null) {
                    missing++;
                }
            }
            counts.put(column, missing);
        }
        
        return counts;
    }
}