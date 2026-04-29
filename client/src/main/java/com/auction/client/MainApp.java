package com.auction.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.auction.client.network.SocketClient; // Đảm bảo import đúng class này

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            // =========================================================
            //  KẾT NỐI TỚI SERVER
            // =========================================================
            try {
                System.out.println(">>> Đang thử kết nối tới Server...");
                SocketClient.getInstance().connect("localhost", 8080);
                System.out.println("✅ Kết nối Server thành công!");
            } catch (Exception ex) {
                System.err.println("❌ LỖI: Không thể kết nối tới Server");

            }

            // =========================================================
            //  LOAD GIAO DIỆN
            // =========================================================
            String path = "/com/auction/client/fxml/login.fxml";
            FXMLLoader loader = new FXMLLoader(getClass().getResource(path));
            Parent root = loader.load();

            // 2. Tạo Scene
            Scene scene = new Scene(root, 1200, 750);

            // 3. Nạp CSS
            try {
                String css = getClass().getResource("/com/auction/client/css/style.css").toExternalForm();
                scene.getStylesheets().add(css);
                System.out.println(">>> Đã nạp CSS thành công!");
            } catch (Exception e) {
                System.err.println(">>> LỖI: Không tìm thấy file style.css!");
            }

            // 4. Cấu hình Stage
            primaryStage.setTitle("Hệ thống đấu giá trực tuyến");
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