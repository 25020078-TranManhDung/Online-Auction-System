package com.auction.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            // 1. Load trực tiếp FXML (Vì lúc khởi tạo không dùng ViewLoader có ActionEvent được)
            String path = "/com/auction/client/fxml/login.fxml";
            FXMLLoader loader = new FXMLLoader(getClass().getResource(path));
            Parent root = loader.load();

            // 2. Tạo Scene
            Scene scene = new Scene(root, 1200, 750);

            try {

                String css = getClass().getResource("/com/auction/client/css/style.css").toExternalForm();
                scene.getStylesheets().add(css);
                System.out.println(">>> Đã nạp CSS thành công!");
            } catch (Exception e) {
                System.err.println(">>> LỖI: Không tìm thấy file style.css tại đường dẫn đã khai báo!");
            }

            // 4. Cấu hình Stage
            primaryStage.setTitle("Hệ thống đấu giá trực tuyến - Đăng nhập");
            primaryStage.setScene(scene);
            primaryStage.setResizable(true);
            primaryStage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}