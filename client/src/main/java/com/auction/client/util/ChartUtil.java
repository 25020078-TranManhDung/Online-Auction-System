package com.auction.client.util;

import javafx.application.Platform;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ChartUtil {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Khởi tạo biểu đồ với giá khởi điểm
     */
    public static XYChart.Series<String, Number> initPriceChart(LineChart<String, Number> lineChart, long startPrice) {
        lineChart.setAnimated(false);
        lineChart.getXAxis().setLabel("Thời gian");
        lineChart.getYAxis().setLabel("Mức giá (VNĐ)");

        XYChart.Series<String, Number> priceSeries = new XYChart.Series<>();
        priceSeries.setName("Biến động giá");

        lineChart.getData().clear();
        lineChart.getData().add(priceSeries);

        // Thêm điểm giá đầu tiên (dùng nhãn thời gian hiện tại)
        addDataPoint(priceSeries, startPrice);

        return priceSeries;
    }

    /**
     * Thêm một điểm giá mới vào biểu đồ (mặc định dùng nhãn thời gian hiện tại)
     */
    public static void addDataPoint(XYChart.Series<String, Number> series, long newPrice) {
        String currentTime = LocalTime.now().format(TIME_FORMATTER);
        addDataPoint(series, currentTime, newPrice);
    }

    /**
     * Overload: Thêm một điểm giá mới với nhãn thời điểm do server cung cấp.
     * Tự động chuẩn hoá chuỗi ISO-8601 (vd "2026-05-09T09:36:23") → "HH:mm:ss"
     * để tránh label dài làm trục X bị xoay và chồng đè (phần thừa ở góc dưới trái).
     */
    public static void addDataPoint(XYChart.Series<String, Number> series, String timeLabel, long newPrice) {
        final String label = normalizeLabel(timeLabel);
        Platform.runLater(() -> {
            try {
                series.getData().add(new XYChart.Data<>(label, newPrice));
                // Giữ cho biểu đồ không bị quá dày (Chỉ hiển thị 10 lượt bid gần nhất)
                if (series.getData().size() > 10) {
                    series.getData().remove(0);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Chuẩn hoá nhãn thời gian:
     *  - null / rỗng          → giờ hiện tại "HH:mm:ss"
     *  - ISO datetime (có 'T', độ dài ≥ 19) → chỉ lấy phần giờ "HH:mm:ss" (ký tự 11–18)
     *  - Chuỗi khác           → giữ nguyên
     */
    public static String normalizeLabel(String raw) {
        if (raw == null || raw.isEmpty()) return LocalTime.now().format(TIME_FORMATTER);

        // 1. Nếu là chuẩn ISO-8601 (có chữ 'T')
        if (raw.contains("T") && raw.length() >= 19) return raw.substring(11, 19);

        // 2. MỚI: Nếu Server trả về Unix Timestamp (toàn chữ số)
        if (raw.matches("\\d+")) {
            try {
                long epochMs = Long.parseLong(raw);
                return java.time.Instant.ofEpochMilli(epochMs)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalTime()
                        .format(TIME_FORMATTER);
            } catch (Exception ignored) {}
        }
        return raw;
    }
}