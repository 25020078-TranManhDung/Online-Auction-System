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
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.util.Callback;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public class AdminDashboardController {

    // --- TAB QUẢN LÝ NGƯỜI DÙNG ---
    @FXML private TableView<JsonObject> tvUsers;
    @FXML private TableColumn<JsonObject, String> colUserId;
    @FXML private TableColumn<JsonObject, String> colUsername;
    @FXML private TableColumn<JsonObject, String> colUserRole;
    @FXML private TableColumn<JsonObject, String> colUserStatus;
    @FXML private TableColumn<JsonObject, Void> colUserAction; // Cột chứa nút bấm dùng Void

    @FXML private TextField txtUserSearch;
    @FXML private ComboBox<String> cboRoleFilter;
    @FXML private Label lblUserCount;

    // --- TAB QUẢN LÝ PHIÊN ĐẤU GIÁ ---
    @FXML private TableView<JsonObject> tvAllAuctions;
    @FXML private TableColumn<JsonObject, String> colAuctionId;
    @FXML private TableColumn<JsonObject, String> colAuctionName;
    @FXML private TableColumn<JsonObject, String> colSellerName;
    @FXML private TableColumn<JsonObject, String> colCurrentBid;
    @FXML private TableColumn<JsonObject, String> colAuctionStatus;
    @FXML private TableColumn<JsonObject, Void> colAuctionAction; // Cột chứa nút bấm dùng Void

    @FXML private TextField txtAuctionSearch;
    @FXML private ComboBox<String> cboAuctionStatusFilter;
    @FXML private Label lblAuctionCount;

    // --- TAB LỊCH SỬ ĐẶT GIÁ (nếu cần) ---
    @FXML private TextField txtBidSearch;
    @FXML private Label lblBidHistoryCount;

    // Data lists
    private final ObservableList<JsonObject> userList = FXCollections.observableArrayList();
    private final ObservableList<JsonObject> auctionList = FXCollections.observableArrayList();

    // Filtered/Sorted wrappers
    private FilteredList<JsonObject> filteredUsers;
    private FilteredList<JsonObject> filteredAuctions;

    @FXML
    public void initialize() {
        setupUserTable();
        setupAuctionTable();

        // Wrap lists with FilteredList and SortedList for TableView
        filteredUsers = new FilteredList<>(userList, p -> true);
        SortedList<JsonObject> sortedUsers = new SortedList<>(filteredUsers);
        sortedUsers.comparatorProperty().bind(tvUsers.comparatorProperty());
        tvUsers.setItems(sortedUsers);

        filteredAuctions = new FilteredList<>(auctionList, p -> true);
        SortedList<JsonObject> sortedAuctions = new SortedList<>(filteredAuctions);
        sortedAuctions.comparatorProperty().bind(tvAllAuctions.comparatorProperty());
        tvAllAuctions.setItems(sortedAuctions);

        // Populate filter comboboxes (optional defaults)
        if (cboRoleFilter != null) {
            cboRoleFilter.getItems().clear();
            cboRoleFilter.getItems().addAll("Tất cả", "BIDDER", "SELLER", "ADMIN");
            cboRoleFilter.setValue("Tất cả");
        }
        if (cboAuctionStatusFilter != null) {
            cboAuctionStatusFilter.getItems().clear();
            cboAuctionStatusFilter.getItems().addAll("Tất cả", "RUNNING", "CLOSED", "DRAFT");
            cboAuctionStatusFilter.setValue("Tất cả");
        }

        // Listeners for search fields (safer than relying only on onKeyReleased)
        if (txtUserSearch != null) {
            txtUserSearch.textProperty().addListener((obs, oldV, newV) -> {
                String q = newV == null ? "" : newV.trim().toLowerCase();
                filteredUsers.setPredicate(makeUserPredicate(q, cboRoleFilter == null ? "Tất cả" : cboRoleFilter.getValue()));
                updateUserCountLabel();
            });
        }
        if (cboRoleFilter != null) {
            cboRoleFilter.setOnAction(e -> {
                String q = txtUserSearch == null ? "" : txtUserSearch.getText().trim().toLowerCase();
                filteredUsers.setPredicate(makeUserPredicate(q, cboRoleFilter.getValue()));
                updateUserCountLabel();
            });
        }

        if (txtAuctionSearch != null) {
            txtAuctionSearch.textProperty().addListener((obs, oldV, newV) -> {
                String q = newV == null ? "" : newV.trim().toLowerCase();
                filteredAuctions.setPredicate(makeAuctionPredicate(q, cboAuctionStatusFilter == null ? "Tất cả" : cboAuctionStatusFilter.getValue()));
                updateAuctionCountLabel();
            });
        }
        if (cboAuctionStatusFilter != null) {
            cboAuctionStatusFilter.setOnAction(e -> {
                String q = txtAuctionSearch == null ? "" : txtAuctionSearch.getText().trim().toLowerCase();
                filteredAuctions.setPredicate(makeAuctionPredicate(q, cboAuctionStatusFilter.getValue()));
                updateAuctionCountLabel();
            });
        }

        // Load initial data
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

        // Do items được set trong initialize() bằng SortedList, không set trực tiếp ở đây
    }

    private void loadUsers() {
        new Thread(() -> {
            try {
                com.google.gson.JsonElement response = SocketClient.getInstance().send("GET_ALL_USERS", new HashMap<>(), com.google.gson.JsonElement.class);

                System.out.println(">>> RAW DATA USERS TỪ SERVER: " + response);

                if (response != null) {
                    JsonArray arr = null;

                    if (response.isJsonArray()) {
                        arr = response.getAsJsonArray();
                    } else if (response.isJsonObject()) {
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
                            updateUserCountLabel();
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
                Map<String, Object> params = new HashMap<>();
                params.put("userId", targetUserId);

                SocketClient.getInstance().send("TOGGLE_USER_STATUS", params, JsonObject.class);

                Platform.runLater(() -> {
                    AlertUtil.showInfo("Thành công", "Đã đảo trạng thái của tài khoản: " + targetUserId);
                    loadUsers();
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
        colAuctionId.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "auctionId")));
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

        // Items được set trong initialize() bằng SortedList
    }

    private void loadAuctions() {
        new Thread(() -> {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("status", "ALL");

                com.google.gson.JsonElement response = SocketClient.getInstance().send("GET_AUCTIONS", params, com.google.gson.JsonElement.class);

                if (response != null) {
                    JsonArray arr = null;

                    if (response.isJsonArray()) {
                        arr = response.getAsJsonArray();
                    } else if (response.isJsonObject()) {
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
                            updateAuctionCountLabel();
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
                    SocketClient.getInstance().send("ADMIN_CLOSE_AUCTION", params, JsonObject.class);

                    Platform.runLater(() -> {
                        AlertUtil.showInfo("Thành công", "Đã hủy phiên đấu giá!");
                        loadAuctions();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> AlertUtil.showError("Lỗi", e.getMessage()));
                }
            }).start();
        }
    }

    // =================================================================================
    // 3. HANDLER ĐƯỢC FXML GỌI (đảm bảo không bị lỗi resolving)
    // =================================================================================
    @FXML
    private void handleUserSearch(KeyEvent event) {
        String q = txtUserSearch == null ? "" : txtUserSearch.getText().trim().toLowerCase();
        filteredUsers.setPredicate(makeUserPredicate(q, cboRoleFilter == null ? "Tất cả" : cboRoleFilter.getValue()));
        updateUserCountLabel();
    }

    @FXML
    private void handleRoleFilter(ActionEvent event) {
        String q = txtUserSearch == null ? "" : txtUserSearch.getText().trim().toLowerCase();
        filteredUsers.setPredicate(makeUserPredicate(q, cboRoleFilter == null ? "Tất cả" : cboRoleFilter.getValue()));
        updateUserCountLabel();
    }

    @FXML
    private void handleRefreshUsers(ActionEvent event) {
        loadUsers();
    }

    @FXML
    private void handleAuctionSearch(KeyEvent event) {
        String q = txtAuctionSearch == null ? "" : txtAuctionSearch.getText().trim().toLowerCase();
        filteredAuctions.setPredicate(makeAuctionPredicate(q, cboAuctionStatusFilter == null ? "Tất cả" : cboAuctionStatusFilter.getValue()));
        updateAuctionCountLabel();
    }

    @FXML
    private void handleAuctionStatusFilter(ActionEvent event) {
        String q = txtAuctionSearch == null ? "" : txtAuctionSearch.getText().trim().toLowerCase();
        filteredAuctions.setPredicate(makeAuctionPredicate(q, cboAuctionStatusFilter == null ? "Tất cả" : cboAuctionStatusFilter.getValue()));
        updateAuctionCountLabel();
    }

    @FXML
    private void handleRefreshAuctions(ActionEvent event) {
        loadAuctions();
    }

    @FXML
    private void handleBidSearch(KeyEvent event) {
        // Nếu bạn có danh sách lịch sử đặt giá, áp dụng tương tự filtered list ở đây
    }

    @FXML
    private void handleRefreshBids(ActionEvent event) {
        // Nếu có API load lịch sử đặt giá, gọi ở đây
    }

    // =================================================================================
    // 4. CÁC HÀM TIỆN ÍCH (HELPER)
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
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : "N/A";
    }

    private String formatMoney(long amount) {
        return String.format("%,d VNĐ", amount);
    }

    private Predicate<JsonObject> makeUserPredicate(String q, String roleFilter) {
        return user -> {
            if (user == null) return false;
            if (roleFilter != null && !roleFilter.isEmpty() && !"Tất cả".equals(roleFilter)) {
                String role = getJsonString(user, "role").toLowerCase();
                if (!role.contains(roleFilter.toLowerCase())) return false;
            }
            if (q == null || q.isEmpty()) return true;
            String username = getJsonString(user, "username").toLowerCase();
            String email = getJsonString(user, "email").toLowerCase();
            String fullname = getJsonString(user, "fullname").toLowerCase();
            return username.contains(q) || email.contains(q) || fullname.contains(q);
        };
    }

    private Predicate<JsonObject> makeAuctionPredicate(String q, String statusFilter) {
        return auction -> {
            if (auction == null) return false;
            if (statusFilter != null && !statusFilter.isEmpty() && !"Tất cả".equals(statusFilter)) {
                String status = getJsonString(auction, "status").toLowerCase();
                if (!status.contains(statusFilter.toLowerCase())) return false;
            }
            if (q == null || q.isEmpty()) return true;
            String title = getJsonString(auction, "title").toLowerCase();
            String seller = getJsonString(auction, "sellerId").toLowerCase();
            return title.contains(q) || seller.contains(q);
        };
    }

    private void updateUserCountLabel() {
        if (lblUserCount != null) {
            lblUserCount.setText(String.format("%d người", filteredUsers == null ? userList.size() : filteredUsers.size()));
        }
    }

    private void updateAuctionCountLabel() {
        if (lblAuctionCount != null) {
            lblAuctionCount.setText(String.format("%d phiên", filteredAuctions == null ? auctionList.size() : filteredAuctions.size()));
        }
    }
}
