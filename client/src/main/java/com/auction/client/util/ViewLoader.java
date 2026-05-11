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

    // =================================================================================
    // NHÓM 1: CÁC PHƯƠNG THỨC MỚI HỖ TRỢ SPA (SINGLE PAGE APPLICATION)
    // Dùng để đổi nội dung một khu vực (ví dụ: bên phải màn hình) mà không tạo cửa sổ mới
    // =================================================================================

    /**
     * Tải FXML và trả về Node giao diện (Parent).
     * Dùng khi chuyển sang màn hình mới (trong cùng một cửa sổ) mà KHÔNG cần truyền dữ liệu.
     */
    public static Parent loadView(String fxml) {
        try {
            String path = BASE_PATH + fxml;
            FXMLLoader loader = new FXMLLoader(ViewLoader.class.getResource(path));
            return loader.load();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Lỗi khi load FXML: " + fxml);
            return null;
        }
    }

    /**
     * Lớp hỗ trợ (Wrapper) chứa cả giao diện và Controller.
     */
    public static class ViewResult<T> {
        private final Parent view;
        private final T controller;

        public ViewResult(Parent view, T controller) {
            this.view = view;
            this.controller = controller;
        }

        public Parent getView() { return view; }
        public T getController() { return controller; }
    }

    /**
     * Tải FXML, trả về cả Giao diện (để nhúng vào layout) và Controller (để truyền dữ liệu).
     * Dùng khi click xem chi tiết một Auction và cần truyền ID sản phẩm sang AuctionDetailController.
     */
    public static <T> ViewResult<T> loadViewWithController(String fxml) {
        try {
            String path = BASE_PATH + fxml;
            FXMLLoader loader = new FXMLLoader(ViewLoader.class.getResource(path));
            Parent view = loader.load();
            T controller = loader.getController();
            return new ViewResult<>(view, controller);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Lỗi khi load FXML kèm Controller: " + fxml);
            return null;
        }
    }


    // =================================================================================
    // NHÓM 2: CÁC PHƯƠNG THỨC CŨ (GIỮ NGUYÊN HOẶC TỐI ƯU GỌN HƠN)
    // Dùng để đổi toàn bộ Scene hoặc mở Popup
    // =================================================================================

    /**
     * Giữ nguyên hành vi cũ: load FXML và set Scene lên Stage hiện tại.
     * (Thường dùng khi chuyển từ Login -> Main Dashboard)
     */
    public static void load(Event event, String fxml, String title) throws IOException {
        String path = BASE_PATH + fxml;
        FXMLLoader loader = new FXMLLoader(ViewLoader.class.getResource(path));
        Parent root = loader.load();

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        setSceneAndShow(stage, root, title);
    }

    /**
     * Load FXML, set Scene lên Stage hiện tại, và trả về controller để caller có thể gọi initData(...)
     */
    public static <T> T loadWithController(Event event, String fxml, String title) throws IOException {
        String path = BASE_PATH + fxml;
        FXMLLoader loader = new FXMLLoader(ViewLoader.class.getResource(path));
        Parent root = loader.load();

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        setSceneAndShow(stage, root, title);

        return loader.getController();
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
        setSceneAndShow(stage, root, title);

        return loader.getController();
    }

    /**
     * Hàm hỗ trợ (Helper method) để tránh lặp lại code nạp CSS và set Scene.
     */
    private static void setSceneAndShow(Stage stage, Parent root, String title) {
        Scene scene = new Scene(root);
        try {
            URL cssUrl = ViewLoader.class.getResource(CSS_PATH);
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
        } catch (Exception e) {
            System.out.println("Cảnh báo: Không tìm thấy style.css");
        }
        stage.setTitle(title);
        stage.setScene(scene);
        stage.show();

        stage.centerOnScreen();
    }
}