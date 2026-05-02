package com.auction.client.controller;

import com.auction.client.network.SocketClient;
import com.auction.client.util.AlertUtil;
import com.auction.client.util.ViewLoader;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Callback;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AdminDashboardController {

    // --- TAB QUẢN LÝ NGƯỜI DÙNG ---
    @FXML private TableView<JsonObject> tvUsers;
    @FXML private TableColumn<JsonObject, String> colUserId;
    @FXML private TableColumn<JsonObject, String> colUsername;
    @FXML private TableColumn<JsonObject, String> colUserRole;
    @FXML private TableColumn<JsonObject, String> colUserStatus;
    @FXML private TableColumn<JsonObject, Void> colUserAction; // Cột chứa nút bấm dùng Void

    // --- TAB QUẢN LÝ PHIÊN ĐẤU GIÁ ---
    @FXML private TableView<JsonObject> tvAllAuctions;
    @FXML private TableColumn<JsonObject, String> colAuctionId;
    @FXML private TableColumn<JsonObject, String> colAuctionName;
    @FXML private TableColumn<JsonObject, String> colSellerName;
    @FXML private TableColumn<JsonObject, String> colCurrentBid;
    @FXML private TableColumn<JsonObject, String> colAuctionStatus;
    @FXML private TableColumn<JsonObject, Void> colAuctionAction; // Cột chứa nút bấm dùng Void

    private ObservableList<JsonObject> userList = FXCollections.observableArrayList();
    private ObservableList<JsonObject> auctionList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupUserTable();
        setupAuctionTable();

        // Tải dữ liệu từ Server khi vừa mở giao diện
        loadUsers();
        loadAuctions();
    }

    // =================================================================================
    // 1. XỬ LÝ BẢNG NGƯỜI DÙNG
    // =================================================================================
    private void setupUserTable() {
        colUserId.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "id")));
        colUsername.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "username")));
        colUserRole.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "role")));
        colUserStatus.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "status")));

        // Tạo nút Khóa/Mở cho từng dòng trong cột Hành động
        colUserAction.setCellFactory(new Callback<>() {
            @Override
            public TableCell<JsonObject, Void> call(TableColumn<JsonObject, Void> param) {
                return new TableCell<>() {
                    private final Button btn = new Button("Khóa/Mở");

                    {
                        btn.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-cursor: hand;");
                        btn.setOnAction(event -> {
                            JsonObject user = getTableView().getItems().get(getIndex());
                            handleToggleUserStatus(user);
                        });
                    }

                    @Override
                    protected void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        setGraphic(empty ? null : btn);
                    }
                };
            }
        });

        tvUsers.setItems(userList);
    }

    private void loadUsers() {
        new Thread(() -> {
            try {
                // Dùng JsonElement để bắt gọn cả Array lẫn Object (không sợ ép kiểu sai nữa)
                com.google.gson.JsonElement response = SocketClient.getInstance().send("GET_ALL_USERS", new HashMap<>(), com.google.gson.JsonElement.class);

                // IN RAW DATA RA CONSOLE ĐỂ BẮT BỆNH
                System.out.println(">>> RAW DATA USERS TỪ SERVER: " + response);

                if (response != null) {
                    JsonArray arr = null;

                    // Nếu Server trả thẳng Mảng
                    if (response.isJsonArray()) {
                        arr = response.getAsJsonArray();
                    }
                    // Nếu Server giấu Mảng trong một Object
                    else if (response.isJsonObject()) {
                        JsonObject obj = response.getAsJsonObject();
                        if (obj.has("users")) arr = obj.getAsJsonArray("users");
                        else if (obj.has("data")) arr = obj.getAsJsonArray("data");
                    }

                    if (arr != null) {
                        final JsonArray finalArr = arr;
                        Platform.runLater(() -> {
                            userList.clear();
                            finalArr.forEach(element -> userList.add(element.getAsJsonObject()));
                            System.out.println("✅ ADMIN Đã tải lên bảng " + userList.size() + " người dùng!");
                        });
                    } else {
                        System.err.println("❌ Không tìm thấy mảng dữ liệu Users nào trong cục Response!");
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Lỗi Admin tải Users: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void handleToggleUserStatus(JsonObject user) {
        String targetUserId = getJsonString(user, "id");

        new Thread(() -> {
            try {
                // Đóng gói ID gửi lên Server
                Map<String, Object> params = new HashMap<>();
                params.put("userId", targetUserId);

                // Gọi Socket gửi lệnh TOGGLE_USER_STATUS mà lúc nãy anh em mình setup ở Server
                SocketClient.getInstance().send("TOGGLE_USER_STATUS", params, JsonObject.class);

                // Nếu Server phản hồi OK thì hiển thị thông báo và Tải lại bảng
                Platform.runLater(() -> {
                    AlertUtil.showInfo("Thành công", "Đã đảo trạng thái của tài khoản: " + targetUserId);
                    loadUsers(); // 👈 Gọi lại hàm này để bảng tự động cập nhật chữ ACTIVE/LOCKED mới
                });
            } catch (Exception e) {
                Platform.runLater(() -> AlertUtil.showError("Lỗi", "Không thể cập nhật: " + e.getMessage()));
            }
        }).start();
    }
    // =================================================================================
    // 2. XỬ LÝ BẢNG PHIÊN ĐẤU GIÁ
    // =================================================================================
    private void setupAuctionTable() {
        // SỬA: 'id' -> 'auctionId'
        colAuctionId.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "auctionId")));

        // SỬA: 'itemId' -> 'title'
        colAuctionName.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "title")));

        colSellerName.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "sellerId")));

        colCurrentBid.setCellValueFactory(data -> {
            long price = data.getValue().has("currentPrice") ? data.getValue().get("currentPrice").getAsLong() : 0;
            return new SimpleStringProperty(formatMoney(price));
        });

        colAuctionStatus.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "status")));

        // Tạo nút Hủy phiên cho từng dòng
        colAuctionAction.setCellFactory(new Callback<>() {
            @Override
            public TableCell<JsonObject, Void> call(TableColumn<JsonObject, Void> param) {
                return new TableCell<>() {
                    private final Button btn = new Button("Hủy phiên");

                    {
                        btn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-cursor: hand;");
                        btn.setOnAction(event -> {
                            JsonObject auction = getTableView().getItems().get(getIndex());
                            handleForceEndAuction(auction);
                        });
                    }

                    @Override
                    protected void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        setGraphic(empty ? null : btn);
                    }
                };
            }
        });

        tvAllAuctions.setItems(auctionList);
    }

    private void loadAuctions() {
        new Thread(() -> {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("status", "ALL");

                // Bắt bằng JsonElement cho an toàn (hứng được cả Hộp lẫn Mảng)
                com.google.gson.JsonElement response = SocketClient.getInstance().send("GET_AUCTIONS", params, com.google.gson.JsonElement.class);

                if (response != null) {
                    JsonArray arr = null;

                    // Nếu là Mảng
                    if (response.isJsonArray()) {
                        arr = response.getAsJsonArray();
                    }
                    // Nếu là Hộp (Đây chính là cái Server đang trả về)
                    else if (response.isJsonObject()) {
                        JsonObject obj = response.getAsJsonObject();
                        if (obj.has("auctions")) {
                            arr = obj.getAsJsonArray("auctions");
                        }
                    }

                    if (arr != null) {
                        final JsonArray finalArr = arr;
                        Platform.runLater(() -> {
                            auctionList.clear();
                            finalArr.forEach(element -> auctionList.add(element.getAsJsonObject()));
                            System.out.println("✅ ADMIN Đã tải xong " + auctionList.size() + " phiên đấu giá!");
                        });
                    } else {
                        System.err.println("❌ Không tìm thấy mảng auctions nào trong cục Response!");
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Lỗi Admin tải Auctions: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void handleForceEndAuction(JsonObject selectedAuction) {
        String status = getJsonString(selectedAuction, "status");
        if ("CLOSED".equals(status)) {
            AlertUtil.showInfo("Thông báo", "Phiên đấu giá này đã kết thúc từ trước rồi.");
            return;
        }

        String auctionId = getJsonString(selectedAuction, "auctionId");

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận hủy");
        confirm.setHeaderText("Bạn có chắc chắn muốn HỦY phiên đấu giá này?");
        confirm.setContentText("ID: " + auctionId);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            new Thread(() -> {
                try {
                    Map<String, Object> params = new HashMap<>();
                    params.put("auctionId", auctionId);
                    // Lệnh đóng Server (ACTION: ADMIN_CLOSE_AUCTION)
                    SocketClient.getInstance().send("ADMIN_CLOSE_AUCTION", params, JsonObject.class);

                    Platform.runLater(() -> {
                        AlertUtil.showInfo("Thành công", "Đã hủy phiên đấu giá!");
                        loadAuctions(); // Load lại bảng
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> AlertUtil.showError("Lỗi", e.getMessage()));
                }
            }).start();
        }
    }

    // =================================================================================
    // 3. CÁC HÀM TIỆN ÍCH (HELPER)
    // =================================================================================
    @FXML
    void handleLogout(ActionEvent event) {
        try {
            ViewLoader.load(event, "login.fxml", "Đăng nhập hệ thống");
        } catch (Exception e) {
            AlertUtil.showError("Lỗi", "Không thể quay lại màn hình đăng nhập.");
        }
    }

    private String getJsonString(JsonObject obj, String key) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : "N/A";
    }

    private String formatMoney(long amount) {
        return String.format("%,d VNĐ", amount);
    }
}