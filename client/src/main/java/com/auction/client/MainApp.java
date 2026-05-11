package com.auction.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.auction.client.network.SocketClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class MainApp extends Application {

    /**
     * Đọc cấu hình server từ client.properties.
     * Nếu file không tồn tại, fallback về localhost:8080.
     */
    private static String[] loadServerConfig() {
        Properties props = new Properties();
        try (InputStream is = MainApp.class.getClassLoader()
            .getResourceAsStream("client.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            System.err.println(">>> Không đọc được client.properties, dùng mặc định localhost:8080");
        }
        String host = props.getProperty("server.host", "localhost");
        String port = props.getProperty("server.port", "8080");
        return new String[]{host, port};
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            // =========================================================
            //  KẾT NỐI TỚI SERVER (đọc host/port từ client.properties)
            // =========================================================
            try {
                String[] config = loadServerConfig();
                String host = config[0];
                int port = Integer.parseInt(config[1]);
                System.out.println(">>> Đang thử kết nối tới Server " + host + ":" + port + "...");
                SocketClient.getInstance().connect(host, port);
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