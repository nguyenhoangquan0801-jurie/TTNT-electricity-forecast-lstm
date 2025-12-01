package com.electricity.forecast.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@CrossOrigin(origins = "*")
public class ForecastController {

    private static final String PROJECT_ROOT = new File("").getAbsolutePath();
    private static final String PYTHON_DIR = PROJECT_ROOT + "/src/main/resources/python";
    private static final String MODEL_DIR = PROJECT_ROOT + "/src/main/resources/python/models";
    private static final String LSTM_MODEL = MODEL_DIR + "/lstm.h5";
    private static final String DATA_DIR = PROJECT_ROOT + "/src/main/resources/data";

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty() || !file.getOriginalFilename().endsWith(".csv")) {
            return "{\"error\":\"File không hợp lệ! Chỉ chấp nhận CSV.\"}";
        }
        Path path = Paths.get(DATA_DIR + "/PJME_hourly.csv");
        Files.createDirectories(path.getParent());
        file.transferTo(path.toFile());
        return "{\"success\":\"Upload thành công! Bây giờ bạn có thể huấn luyện.\"}";
    }

    @GetMapping("/train")
    public String train() throws Exception {
        File lstmFile = new File(LSTM_MODEL);
        if (lstmFile.exists()) {
            return "MÔ HÌNH ĐÃ SẴN SÀNG! Không cần huấn luyện lại.<br>Bạn có thể nhấn DỰ BÁO ngay lập tức!";
        }

        new File(MODEL_DIR).mkdirs();
        ProcessBuilder pb = new ProcessBuilder("py", "-3.11", "train.py");
        pb.directory(new File(PYTHON_DIR));
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        pb.redirectErrorStream(true);

        Process p = pb.start();
        String output = readOutput(p.getInputStream());
        p.waitFor();

        System.out.println("=== TRAIN OUTPUT ===");
        System.out.println(output);

        if (output.contains("HOÀN TẤT") || output.contains("saved")) {
            return "HUẤN LUYỆN THÀNH CÔNG!<br>Metrics: " + extractMetrics(output);
        } else {
            return "Lỗi huấn luyện:<br><pre>" + output.replace("\n", "<br>") + "</pre>";
        }
    }

    @GetMapping("/predict")
    public String predict() throws Exception {
        if (!new File(LSTM_MODEL).exists()) {
            return "{\"error\":\"Chưa có mô hình! Vui lòng huấn luyện trước.\"}";
        }

        ProcessBuilder pb = new ProcessBuilder("py", "-3.11", "predict.py");
        pb.directory(new File(PYTHON_DIR));
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        pb.redirectErrorStream(true);

        Process p = pb.start();
        String output = readOutput(p.getInputStream());
        p.waitFor();

        System.out.println("=== PREDICT OUTPUT ===");
        System.out.println(output);

        String json = output.trim();
        if (json.isEmpty() || json.toLowerCase().contains("error")) {
            return "{\"error\":\"Lỗi chạy predict.py. Xem console server.\"}";
        }

        return json;
    }

    private String extractMetrics(String output) {
        // Trích metrics từ output (ví dụ: MAE: 123, RMSE: 456)
        return output.substring(output.lastIndexOf("Metrics:")).replace("\n", "<br>");
    }

    private String readOutput(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}