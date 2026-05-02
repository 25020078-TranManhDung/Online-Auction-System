package com.auction.client.controller;

import com.auction.client.model.UserSession;
import com.auction.client.network.SocketClient;
import com.auction.client.util.AlertUtil;
import com.auction.client.util.ViewLoader;
import com.auction.shared.network.protocol.Actions;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuctionListController {

    @FXML private Label lblUserStatus;
    @FXML private FlowPane auctionContainer;

    // --- CÁC LỚP DTO ĐỂ HỨNG DỮ LIỆU TỪ SERVER ---

    // Yêu cầu gửi lên
    public static class GetAuctionsRequest {
        public String status;
        public int page;
        public int size;

        public GetAuctionsRequest(String status, int page, int size) {
            this.status = status;
            this.page = page;
            this.size = size;
        }
    }

    // Phần tử con bên trong mảng auctions trả về
    public static class AuctionItem {
        public String auctionId;
        public String title;
        public double currentPrice;
        public String endTime;
        public String status;
        public int bidCount;
    }

    // Dữ liệu tổng trả về
    public static class GetAuctionsResponse {
        public List<AuctionItem> auctions;
        public int total;
    }

    /**
     * Hàm này tự động chạy ngay sau khi file FXML được load xong.
     */
    @FXML
    public void initialize() {
        // Cập nhật tên người dùng lên góc phải
        String username = UserSession.getInstance().getUsername();
        lblUserStatus.setText("Xin chào, " + (username != null ? username : "Khách"));

        // Gọi ngay hàm làm mới danh sách khi vừa vào màn hình
        refreshList();
    }

    /**
     * Nút Làm mới (Refresh) danh sách đấu giá
     */
    @FXML
    private void refreshList() {
        // Xóa sạch các món hàng cũ trên giao diện
        auctionContainer.getChildren().clear();

        // Mở luồng phụ lấy dữ liệu từ Server
        new Thread(() -> {
            try {
                // Tạo Request: Lấy 20 sản phẩm đang ACTIVE
                GetAuctionsRequest req = new GetAuctionsRequest("OPEN", 0, 20);

                // Gửi Request
                GetAuctionsResponse response = SocketClient.getInstance().send(
                        Actions.GET_AUCTIONS,
                        req,
                        GetAuctionsResponse.class
                );

                // Nếu có kết quả, đổ lên giao diện
                Platform.runLater(() -> {
                    if (response.auctions == null || response.auctions.isEmpty()) {
                        Label lblEmpty = new Label("Hiện tại không có phiên đấu giá nào.");
                        lblEmpty.setStyle("-fx-font-style: italic; -fx-text-fill: gray;");
                        auctionContainer.getChildren().add(lblEmpty);
                        return;
                    }

                    // Duyệt từng sản phẩm và tạo "Thẻ" (Card) giao diện cho nó
                    for (AuctionItem item : response.auctions) {
                        VBox card = createAuctionCard(item);
                        auctionContainer.getChildren().add(card);
                    }
                });

            } catch (RuntimeException e) {
                Platform.runLater(() -> AlertUtil.showError("Lỗi tải dữ liệu", e.getMessage()));
            }
        }).start();
    }

    /**
     * Nút Đăng xuất
     */
    @FXML
    private void handleLogout(ActionEvent event) {
        new Thread(() -> {
            try {
                // Gửi lệnh LOGOUT lên server
                SocketClient.getInstance().send(Actions.LOGOUT, new HashMap<>(), Void.class);
            } catch (Exception e) {
                // Mặc kệ lỗi mạng khi logout, vẫn cho user thoát ra
                System.out.println("Lỗi gọi API Logout: " + e.getMessage());
            } finally {
                // Về luồng chính dọn dẹp và chuyển màn hình
                Platform.runLater(() -> {
                    UserSession.getInstance().cleanUserSession();// Xóa token nội bộ
                    try {
                        ViewLoader.load(event, "login.fxml", "Đăng nhập");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }).start();
    }

    /**
     * Hàm hỗ trợ: Vẽ "Thẻ sản phẩm" (Card) bằng code Java thay vì FXML
     * (Giúp linh hoạt số lượng sản phẩm trả về)
     */
    private VBox createAuctionCard(AuctionItem item) {
        VBox card = new VBox(10); // khoảng cách giữa các chữ là 10px
        card.setPadding(new Insets(15));
        // Thêm viền, màu nền trắng và bo góc cho đẹp
        card.setStyle("-fx-background-color: white; -fx-border-color: #bdc3c7; -fx-border-radius: 8; -fx-background-radius: 8; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        card.setPrefWidth(250);

        // Tiêu đề sản phẩm
        Label lblTitle = new Label(item.title);
        lblTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        // Giá hiện tại
        Label lblPrice = new Label(String.format("Giá: %,.0f VNĐ", item.currentPrice));
        lblPrice.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 14px;");

        // Thời gian kết thúc
        Label lblTime = new Label("Kết thúc: " + item.endTime);
        lblTime.setStyle("-fx-text-fill: #7f8c8d;");

        // Nút Xem chi tiết
        Button btnView = new Button("Xem chi tiết");
        btnView.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");
        btnView.setMaxWidth(Double.MAX_VALUE); // Nút trải dài toàn bộ chiều ngang card

        // Sự kiện khi bấm vào xem chi tiết (chuyển sang màn AuctionDetailController)
        // Sự kiện khi bấm vào xem chi tiết (chuyển sang màn AuctionDetailController)
        btnView.setOnAction(e -> {
            try {
                // 1. Tải giao diện lên bằng tay thay vì dùng ViewLoader
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/auction/client/fxml/auction-detail.fxml"));
                javafx.scene.Parent root = loader.load();

                // 2. LẤY CONTROLLER VÀ BƠM ID VÀO (ĐIỂM ĂN TIỀN LÀ ĐÂY!)
                com.auction.client.controller.AuctionDetailController detailController = loader.getController();
                detailController.initData(item.auctionId);

                // 3. Đổi cảnh (Chuyển màn hình)
                javafx.stage.Stage stage = (javafx.stage.Stage) ((javafx.scene.Node) e.getSource()).getScene().getWindow();
                stage.setScene(new javafx.scene.Scene(root));
                stage.setTitle("Chi tiết đấu giá");
                stage.show();

            } catch (Exception ex) {
                ex.printStackTrace();
                AlertUtil.showError("Lỗi", "Không thể mở chi tiết sản phẩm!");
            }
        });

        card.getChildren().addAll(lblTitle, lblPrice, lblTime, btnView);
        return card;
    }
}