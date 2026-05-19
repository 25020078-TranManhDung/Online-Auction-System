package com.auction.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import com.auction.client.network.SocketClient;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

public class MainApp extends Application {

    /**
     * File config ngoài jar (cùng thư mục với file .jar hoặc thư mục chạy).
     * Ưu tiên đọc file này TRƯỚC classpath để mỗi máy có thể tự config IP server.
     */
    private static final Path EXTERNAL_CONFIG = Paths.get("client.properties");

    /**
     * Đọc cấu hình server:
     *   1. Thử đọc file ./client.properties bên ngoài jar (ưu tiên cao nhất)
     *   2. Fallback về client.properties trong classpath (bên trong jar)
     *   3. Default: localhost:8080
     *
     * Lý do cần file ngoài: khi test nhiều laptop, mỗi laptop cần trỏ server.host
     * về IP của máy chạy server. Không thể sửa file bên trong jar mà không recompile.
     */
    private static Properties loadServerConfig() {
        Properties props = new Properties();

        // Ưu tiên 1: File ngoài jar (./client.properties)
        if (Files.exists(EXTERNAL_CONFIG)) {
            try (InputStream is = Files.newInputStream(EXTERNAL_CONFIG)) {
                props.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                System.out.println(">>> Đọc config từ file ngoài: " + EXTERNAL_CONFIG.toAbsolutePath());
                return props;
            } catch (IOException e) {
                System.err.println(">>> Lỗi đọc file ngoài, thử classpath...");
            }
        }

        // Ưu tiên 2: Classpath bên trong jar
        try (InputStream is = MainApp.class.getClassLoader().getResourceAsStream("client.properties")) {
            if (is != null) {
                props.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                System.out.println(">>> Đọc config từ classpath (jar).");
            }
        } catch (IOException e) {
            System.err.println(">>> Không đọc được client.properties, dùng mặc định localhost:8080");
        }
        return props;
    }

    /**
     * Lưu IP và port mới ra file ngoài jar để lần sau không phải nhập lại.
     */
    private static void saveExternalConfig(String host, String port) {
        try {
            Properties p = new Properties();
            p.setProperty("server.host", host);
            p.setProperty("server.port", port);
            try (Writer w = Files.newBufferedWriter(EXTERNAL_CONFIG, StandardCharsets.UTF_8)) {
                p.store(w, "Client config - tu dong luu boi MainApp");
            }
            System.out.println(">>> Đã lưu config: server.host=" + host + " vào " + EXTERNAL_CONFIG.toAbsolutePath());
        } catch (IOException ex) {
            System.err.println(">>> Không thể lưu config: " + ex.getMessage());
        }
    }

    /**
     * Hiển thị dialog nhập địa chỉ IP server khi:
     *   - server.host=localhost VÀ kết nối thất bại (tức là server không chạy trên máy này)
     *   - Hoặc bất cứ lần kết nối nào thất bại
     *
     * @param defaultHost host hiện tại để điền sẵn vào ô nhập
     * @param defaultPort port hiện tại
     * @param errorMsg    thông báo lỗi kết nối (hiện lên để người dùng biết tại sao)
     * @return String[]{host, port} sau khi người dùng xác nhận, hoặc null nếu đóng dialog
     */
    private String[] showServerConfigDialog(String defaultHost, String defaultPort, String errorMsg) {
        AtomicReference<String[]> result = new AtomicReference<>(null);
        Object lock = new Object();

        javafx.application.Platform.runLater(() -> {
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initStyle(StageStyle.UNDECORATED);
            dialog.setTitle("Cấu hình Server");

            // ── Layout ──────────────────────────────────────────────────
            VBox root = new VBox(18);
            root.setPadding(new Insets(30, 36, 26, 36));
            root.setAlignment(Pos.CENTER_LEFT);
            root.setStyle(
                "-fx-background-color: #1a1a2e;" +
                    "-fx-background-radius: 16;" +
                    "-fx-border-color: #4a90d9;" +
                    "-fx-border-width: 2;" +
                    "-fx-border-radius: 16;");

            // Tiêu đề
            Label title = new Label("🌐  Cấu hình địa chỉ Server");
            title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
            title.setTextFill(Color.WHITE);

            // Mô tả
            Label desc = new Label(
                "Khi test trên nhiều máy, mỗi Client cần trỏ đến\n" +
                    "IP của máy đang chạy Server (không phải localhost).\n" +
                    "Cấu hình sẽ được lưu vào file client.properties\n" +
                    "bên cạnh file jar để lần sau không cần nhập lại.");
            desc.setFont(Font.font("Segoe UI", 13));
            desc.setTextFill(Color.rgb(180, 200, 230));

            // Lỗi kết nối (nếu có)
            if (errorMsg != null && !errorMsg.isEmpty()) {
                Label errLabel = new Label("⚠  " + errorMsg);
                errLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
                errLabel.setTextFill(Color.rgb(255, 120, 100));
                errLabel.setWrapText(true);
                root.getChildren().add(errLabel);
            }

            // IP input
            Label lblHost = new Label("Địa chỉ IP Server:");
            lblHost.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
            lblHost.setTextFill(Color.rgb(200, 220, 255));

            TextField tfHost = new TextField(defaultHost);
            tfHost.setPromptText("Ví dụ: 192.168.1.100");
            tfHost.setFont(Font.font("Segoe UI", 14));
            tfHost.setPrefHeight(38);
            tfHost.setStyle(
                "-fx-background-color: #0d0d1e;" +
                    "-fx-text-fill: white;" +
                    "-fx-border-color: #4a90d9;" +
                    "-fx-border-radius: 8;" +
                    "-fx-background-radius: 8;" +
                    "-fx-padding: 6 12 6 12;");

            // Port input
            Label lblPort = new Label("Cổng (Port):");
            lblPort.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
            lblPort.setTextFill(Color.rgb(200, 220, 255));

            TextField tfPort = new TextField(defaultPort);
            tfPort.setFont(Font.font("Segoe UI", 14));
            tfPort.setPrefHeight(38);
            tfPort.setPrefWidth(120);
            tfPort.setStyle(
                "-fx-background-color: #0d0d1e;" +
                    "-fx-text-fill: white;" +
                    "-fx-border-color: #4a90d9;" +
                    "-fx-border-radius: 8;" +
                    "-fx-background-radius: 8;" +
                    "-fx-padding: 6 12 6 12;");

            // Hint localhost
            Label hint = new Label("💡 Máy chạy Server: dùng localhost hoặc 127.0.0.1\n" +
                "    Máy khác cùng mạng: dùng IP LAN (vd: 192.168.x.x)");
            hint.setFont(Font.font("Segoe UI", 11));
            hint.setTextFill(Color.rgb(140, 160, 190));

            // Status kết nối
            Label lblStatus = new Label("");
            lblStatus.setFont(Font.font("Segoe UI", 13));
            lblStatus.setWrapText(true);

            // Buttons
            Button btnConnect = new Button("🔌  Kết nối");
            btnConnect.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
            btnConnect.setPrefHeight(40);
            btnConnect.setPrefWidth(160);
            btnConnect.setStyle(
                "-fx-background-color: #4a90d9;" +
                    "-fx-text-fill: white;" +
                    "-fx-background-radius: 10;" +
                    "-fx-cursor: hand;");

            Button btnLocalhost = new Button("localhost");
            btnLocalhost.setFont(Font.font("Segoe UI", 13));
            btnLocalhost.setPrefHeight(40);
            btnLocalhost.setStyle(
                "-fx-background-color: #2c3e50;" +
                    "-fx-text-fill: #aaa;" +
                    "-fx-background-radius: 10;" +
                    "-fx-cursor: hand;");
            btnLocalhost.setOnAction(e -> tfHost.setText("localhost"));

            HBox btnRow = new HBox(12, btnConnect, btnLocalhost);
            btnRow.setAlignment(Pos.CENTER_LEFT);

            root.getChildren().addAll(
                title, desc,
                lblHost, tfHost,
                lblPort, tfPort,
                hint, lblStatus, btnRow
            );

            Scene scene = new Scene(root, 480, errorMsg != null && !errorMsg.isEmpty() ? 460 : 430);
            scene.setFill(Color.TRANSPARENT);
            dialog.setScene(scene);

            // ── Xử lý kết nối ──────────────────────────────────────────
            btnConnect.setOnAction(e -> {
                String h = tfHost.getText().trim();
                String p = tfPort.getText().trim();
                if (h.isEmpty()) { h = "localhost"; }
                if (p.isEmpty()) { p = "8080"; }

                lblStatus.setText("⏳ Đang thử kết nối tới " + h + ":" + p + " ...");
                lblStatus.setTextFill(Color.rgb(200, 200, 100));

                final String fh = h, fp = p;
                new Thread(() -> {
                    try {
                        // Thử kết nối TCP thực sự
                        SocketClient client = SocketClient.getInstance();
                        client.connect(fh, Integer.parseInt(fp));
                        // Thành công
                        saveExternalConfig(fh, fp);
                        javafx.application.Platform.runLater(() -> {
                            lblStatus.setText("✅ Kết nối thành công!");
                            lblStatus.setTextFill(Color.rgb(100, 220, 100));
                        });
                        try { Thread.sleep(700); } catch (InterruptedException ignored) {}
                        synchronized (lock) {
                            result.set(new String[]{fh, fp});
                            lock.notifyAll();
                        }
                        javafx.application.Platform.runLater(dialog::close);
                    } catch (Exception ex) {
                        javafx.application.Platform.runLater(() -> {
                            lblStatus.setText("❌ Không kết nối được: " + ex.getMessage());
                            lblStatus.setTextFill(Color.rgb(255, 100, 100));
                        });
                        // Người dùng có thể thử lại → KHÔNG đóng dialog
                    }
                }, "conn-test-thread").start();
            });

            // Enter để kết nối
            tfHost.setOnAction(e -> btnConnect.fire());
            tfPort.setOnAction(e -> btnConnect.fire());

            dialog.show();
            dialog.centerOnScreen();
        });

        // Block thread start() cho đến khi dialog đóng (hoặc kết nối thành công)
        // Dùng wait/notify thay vì CountDownLatch để dialog có thể thử lại nhiều lần
        synchronized (lock) {
            while (result.get() == null) {
                try { lock.wait(200); } catch (InterruptedException ignored) {}
                // Kiểm tra nếu app đang tắt
                if (!javafx.application.Platform.isFxApplicationThread()
                    && Thread.currentThread().isInterrupted()) break;
            }
        }
        return result.get();
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            // =========================================================
            //  KẾT NỐI TỚI SERVER
            //  Thứ tự ưu tiên đọc config:
            //    1. ./client.properties (file ngoài jar, bên cạnh .jar)
            //    2. classpath/client.properties (bên trong jar)
            //    3. Dialog nhập tay nếu kết nối thất bại
            // =========================================================
            Properties config = loadServerConfig();
            String host = config.getProperty("server.host", "localhost");
            String port = config.getProperty("server.port", "8080");

            boolean connected = false;
            String lastError = null;

            // Thử kết nối với config đọc được
            try {
                System.out.println(">>> Đang thử kết nối tới Server " + host + ":" + port + "...");
                SocketClient.getInstance().connect(host, Integer.parseInt(port));
                System.out.println("✅ Kết nối Server thành công!");
                connected = true;
            } catch (Exception ex) {
                lastError = ex.getMessage();
                System.err.println("❌ Kết nối thất bại (" + host + ":" + port + "): " + lastError);
            }

            // Nếu thất bại → hiển thị dialog để người dùng nhập IP đúng
            if (!connected) {
                System.out.println(">>> Hiển thị dialog cấu hình server...");
                String[] userConfig = showServerConfigDialog(host, port, lastError);
                if (userConfig != null) {
                    host = userConfig[0];
                    port = userConfig[1];
                    connected = true;
                    System.out.println(">>> Dùng server: " + host + ":" + port);
                } else {
                    System.err.println(">>> Người dùng đóng dialog, tiếp tục không có kết nối.");
                }
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

            // ★ THÊM MỚI: Đăng ký Scene với ThemeManager để dark mode hoạt động toàn app.
            // Gọi sau khi nạp style.css để ThemeManager có thể thêm dark-mode.css vào sau.
            com.auction.client.util.ThemeManager.getInstance().setScene(scene);

            // 4. Cấu hình Stage
            primaryStage.setTitle("Hệ thống đấu giá trực tuyến");
            primaryStage.setScene(scene);
            primaryStage.setResizable(true);
            // ★ RESPONSIVE: Kích thước tối thiểu để layout không bị vỡ
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);
            // ★ FULLSCREEN: Luôn mở toàn màn hình (maximized)
            primaryStage.setMaximized(true);
            primaryStage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}