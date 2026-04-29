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
        // Tắt animation mặc định để biểu đồ mượt hơn khi giật số liên tục
        lineChart.setAnimated(false);
        lineChart.getXAxis().setLabel("Thời gian");
        lineChart.getYAxis().setLabel("Mức giá (VNĐ)");

        XYChart.Series<String, Number> priceSeries = new XYChart.Series<>();
        priceSeries.setName("Biến động giá");

        lineChart.getData().clear();
        lineChart.getData().add(priceSeries);

        // Thêm điểm giá đầu tiên
        addDataPoint(priceSeries, startPrice);

        return priceSeries;
    }

    /**
     * Thêm một điểm giá mới vào biểu đồ khi có người Bid
     */
    public static void addDataPoint(XYChart.Series<String, Number> series, long newPrice) {
        String currentTime = LocalTime.now().format(TIME_FORMATTER);

        Platform.runLater(() -> {
            series.getData().add(new XYChart.Data<>(currentTime, newPrice));

            // Giữ cho biểu đồ không bị quá dày (Chỉ hiển thị 10 lượt bid gần nhất)
            if (series.getData().size() > 10) {
                series.getData().remove(0);
            }
        });
    }
}