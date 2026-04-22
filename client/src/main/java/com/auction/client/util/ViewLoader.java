package com.auction.client.util;

import javafx.event.Event; // Dùng Event chung
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class ViewLoader {
    public static void load(Event event, String fxml, String title) throws IOException {
        // Sửa đường dẫn cho đúng thư mục của bạn
        String path = "/com/auction/client/fxml/" + fxml;

        FXMLLoader loader = new FXMLLoader(ViewLoader.class.getResource(path));
        Parent root = loader.load();

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        Scene scene = new Scene(root);

        // Nạp CSS
        try {
            String css = ViewLoader.class.getResource("/com/auction/client/css/style.css").toExternalForm();
            scene.getStylesheets().add(css);
        } catch (Exception e) {
            System.out.println("Cảnh báo: Không tìm thấy style.css");
        }

        stage.setTitle(title);
        stage.setScene(scene);
        stage.show();
    }
}