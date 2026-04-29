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
                // Đảm bảo "GET_ALL_USERS" có tồn tại bên Server Protocol
                JsonObject data = SocketClient.getInstance().send("GET_ALL_USERS", new HashMap<>(), JsonObject.class);
                if (data != null && data.has("users")) {
                    JsonArray arr = data.getAsJsonArray("users");
                    Platform.runLater(() -> {
                        userList.clear();
                        arr.forEach(element -> userList.add(element.getAsJsonObject()));
                    });
                }
            } catch (Exception e) {
                System.err.println("Chưa tải được User: " + e.getMessage());
            }
        }).start();
    }

    private void handleToggleUserStatus(JsonObject user) {
        String userId = getJsonString(user, "id");
        // Gọi Socket để đổi trạng thái tài khoản (Ví dụ ACTION: TOGGLE_USER_STATUS)
        // Sau khi thành công thì gọi lại loadUsers();
        AlertUtil.showInfo("Tính năng", "Gửi lệnh khóa tài khoản " + userId + " lên server...");
    }

    // =================================================================================
    // 2. XỬ LÝ BẢNG PHIÊN ĐẤU GIÁ
    // =================================================================================
    private void setupAuctionTable() {

        colAuctionId.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "id")));

        // title hoặc itemId tùy server trả về
        colAuctionName.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "itemId")));
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
                // Đảm bảo "GET_AUCTIONS" tồn tại bên Server Protocol
                JsonObject data = SocketClient.getInstance().send("GET_AUCTIONS", new HashMap<>(), JsonObject.class);
                if (data != null && data.has("auctions")) {
                    JsonArray arr = data.getAsJsonArray("auctions");
                    Platform.runLater(() -> {
                        auctionList.clear();
                        arr.forEach(element -> auctionList.add(element.getAsJsonObject()));
                    });
                }
            } catch (Exception e) {
                System.err.println("Chưa tải được Auctions: " + e.getMessage());
            }
        }).start();
    }

    private void handleForceEndAuction(JsonObject selectedAuction) {
        String status = getJsonString(selectedAuction, "status");
        if ("CLOSED".equals(status)) {
            AlertUtil.showInfo("Thông báo", "Phiên đấu giá này đã kết thúc từ trước rồi.");
            return;
        }

        String auctionId = getJsonString(selectedAuction, "id");

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