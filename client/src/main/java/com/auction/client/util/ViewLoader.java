package com.auction.client.util;

import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class ViewLoader {

    private static final String BASE_PATH = "/com/auction/client/fxml/";
    private static final String CSS_PATH = "/com/auction/client/css/style.css";

    /**
     * Giữ nguyên hành vi cũ: load FXML và set Scene lên Stage hiện tại.
     */
    public static void load(Event event, String fxml, String title) throws IOException {
        String path = BASE_PATH + fxml;
        FXMLLoader loader = new FXMLLoader(ViewLoader.class.getResource(path));
        Parent root = loader.load();

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        Scene scene = new Scene(root);

        // Nạp CSS nếu có
        try {
            URL cssUrl = ViewLoader.class.getResource(CSS_PATH);
            if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        } catch (Exception e) {
            System.out.println("Cảnh báo: Không tìm thấy style.css");
        }

        stage.setTitle(title);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Load FXML, set Scene lên Stage hiện tại, và trả về controller để caller có thể gọi initData(...)
     * Sử dụng khi cần truyền dữ liệu vào controller sau khi load.
     */
    public static <T> T loadWithController(Event event, String fxml, String title) throws IOException {
        String path = BASE_PATH + fxml;
        FXMLLoader loader = new FXMLLoader(ViewLoader.class.getResource(path));
        Parent root = loader.load();

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        Scene scene = new Scene(root);

        try {
            URL cssUrl = ViewLoader.class.getResource(CSS_PATH);
            if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        } catch (Exception e) {
            System.out.println("Cảnh báo: Không tìm thấy style.css");
        }

        stage.setTitle(title);
        stage.setScene(scene);
        stage.show();

        @SuppressWarnings("unchecked")
        T controller = (T) loader.getController();
        return controller;
    }

    /**
     * Mở FXML trong cửa sổ mới (Stage) và trả về controller.
     * Dùng cho popup/modal (ví dụ: cửa sổ đặt giá).
     */
    public static <T> T openInNewWindow(String fxml, String title) throws IOException {
        String path = BASE_PATH + fxml;
        FXMLLoader loader = new FXMLLoader(ViewLoader.class.getResource(path));
        Parent root = loader.load();

        Stage stage = new Stage();
        Scene scene = new Scene(root);

        try {
            URL cssUrl = ViewLoader.class.getResource(CSS_PATH);
            if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        } catch (Exception e) {
            System.out.println("Cảnh báo: Không tìm thấy style.css");
        }

        stage.setTitle(title);
        stage.setScene(scene);
        stage.show();

        @SuppressWarnings("unchecked")
        T controller = (T) loader.getController();
        return controller;
    }
}
