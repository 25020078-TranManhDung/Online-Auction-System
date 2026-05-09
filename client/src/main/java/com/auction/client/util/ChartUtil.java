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
     * Overload: Thêm một điểm giá mới với nhãn thời điểm do server cung cấp
     */
    public static void addDataPoint(XYChart.Series<String, Number> series, String timeLabel, long newPrice) {
        final String label = (timeLabel == null || timeLabel.isEmpty()) ? LocalTime.now().format(TIME_FORMATTER) : timeLabel;

        Platform.runLater(() -> {
            try {
                series.getData().add(new XYChart.Data<>(label, newPrice));

                // Giữ cho biểu đồ không bị quá dày (Chỉ hiển thị 10 lượt bid gần nhất)
                if (series.getData().size() > 10) {
                    series.getData().remove(0);
                }
            } catch (Exception e) {
                // tránh crash UI nếu có lỗi khi cập nhật chart
                e.printStackTrace();
            }
        });
    }
}
