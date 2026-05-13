package com.auction.client.controller;

import com.auction.client.network.SocketClient;
import com.auction.client.util.AlertUtil;
import com.auction.client.util.ViewLoader;
import com.auction.shared.network.protocol.Actions;
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
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public class AdminDashboardController {

    // --- THỐNG KÊ TỔNG QUAN (FIX: thiếu @FXML → luôn null → không cập nhật được) ---
    @FXML private Label lblTotalUsers;
    @FXML private Label lblLockedUsers;
    @FXML private Label lblTotalAuctions;
    @FXML private Label lblActiveAuctions;
    @FXML private Label lblTotalBids;

    // --- TAB QUẢN LÝ NGƯỜI DÙNG ---
    @FXML private TableView<JsonObject> tvUsers;
    @FXML private TableColumn<JsonObject, String> colUserId;
    @FXML private TableColumn<JsonObject, String> colUsername;
    @FXML private TableColumn<JsonObject, String> colFullname;   // FIX: thiếu @FXML bind
    @FXML private TableColumn<JsonObject, String> colEmail;      // FIX: thiếu @FXML bind
    @FXML private TableColumn<JsonObject, String> colUserRole;
    @FXML private TableColumn<JsonObject, String> colUserStatus;
    @FXML private TableColumn<JsonObject, Void>   colUserAction;

    @FXML private TextField txtUserSearch;
    @FXML private ComboBox<String> cboRoleFilter;
    @FXML private Label lblUserCount;

    // --- TAB QUẢN LÝ PHIÊN ĐẤU GIÁ ---
    @FXML private TableView<JsonObject> tvAllAuctions;
    @FXML private TableColumn<JsonObject, String> colAuctionId;
    @FXML private TableColumn<JsonObject, String> colAuctionName;
    @FXML private TableColumn<JsonObject, String> colSellerName;
    @FXML private TableColumn<JsonObject, String> colCurrentBid;
    @FXML private TableColumn<JsonObject, String> colBidCount;    // FIX: thiếu @FXML bind
    @FXML private TableColumn<JsonObject, String> colAuctionStatus;
    @FXML private TableColumn<JsonObject, String> colEndTime;     // FIX: thiếu @FXML bind
    @FXML private TableColumn<JsonObject, Void>   colAuctionAction;

    @FXML private TextField txtAuctionSearch;
    @FXML private ComboBox<String> cboAuctionStatusFilter;
    @FXML private Label lblAuctionCount;

    // --- TAB LỊCH SỬ ĐẶT GIÁ ---
    @FXML private TableView<JsonObject>             tvBidHistory;
    @FXML private TableColumn<JsonObject, String>   colBidNo;
    @FXML private TableColumn<JsonObject, String>   colBidder;
    @FXML private TableColumn<JsonObject, String>   colBidProduct;
    @FXML private TableColumn<JsonObject, String>   colBidAmount;
    @FXML private TableColumn<JsonObject, String>   colBidTime;
    @FXML private TableColumn<JsonObject, String>   colBidStatus;
    @FXML private TextField txtBidSearch;
    @FXML private Label lblBidHistoryCount;

    // Data lists
    private final ObservableList<JsonObject> userList    = FXCollections.observableArrayList();
    private final ObservableList<JsonObject> auctionList = FXCollections.observableArrayList();
    private final ObservableList<JsonObject> bidList     = FXCollections.observableArrayList();

    // Filtered/Sorted wrappers
    private FilteredList<JsonObject> filteredUsers;
    private FilteredList<JsonObject> filteredAuctions;
    private FilteredList<JsonObject> filteredBids;

    @FXML
    public void initialize() {
        setupUserTable();
        setupAuctionTable();
        setupBidTable();   // FIX: khởi tạo bảng bid history

        // Wrap lists with FilteredList and SortedList for TableView
        filteredUsers = new FilteredList<>(userList, p -> true);
        SortedList<JsonObject> sortedUsers = new SortedList<>(filteredUsers);
        sortedUsers.comparatorProperty().bind(tvUsers.comparatorProperty());
        tvUsers.setItems(sortedUsers);

        filteredAuctions = new FilteredList<>(auctionList, p -> true);
        SortedList<JsonObject> sortedAuctions = new SortedList<>(filteredAuctions);
        sortedAuctions.comparatorProperty().bind(tvAllAuctions.comparatorProperty());
        tvAllAuctions.setItems(sortedAuctions);

        // FIX: bid history FilteredList + SortedList
        filteredBids = new FilteredList<>(bidList, p -> true);
        SortedList<JsonObject> sortedBids = new SortedList<>(filteredBids);
        if (tvBidHistory != null) {
            sortedBids.comparatorProperty().bind(tvBidHistory.comparatorProperty());
            tvBidHistory.setItems(sortedBids);
        }
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

        if (txtBidSearch != null) {
            txtBidSearch.textProperty().addListener((obs, oldV, newV) -> {
                String q = newV == null ? "" : newV.trim().toLowerCase();
                filteredBids.setPredicate(makeBidPredicate(q));
                updateBidCountLabel();
            });
        }

        // Load initial data
        loadUsers();
        loadAuctions();
        loadBids();   // FIX: load bid history khi khởi động
    }

    // =================================================================================
    // 1. XỬ LÝ BẢNG NGƯỜI DÙNG
    // =================================================================================
    private void setupUserTable() {
        colUserId.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "id")));
        colUsername.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "username")));
        // FIX: bind cột Họ và tên + Email (trước đây bỏ qua khiến hai cột này trống hoàn toàn)
        colFullname.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "fullname")));
        colEmail.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "email")));
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
                            updateStatLabels(); // FIX: cập nhật stat cards sau khi có dữ liệu
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
        // FIX: bind cột Lượt đặt + Kết thúc (trước đây bỏ qua khiến hai cột này trống)
        colBidCount.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "bidCount")));
        colEndTime.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "endTime")));
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
                            updateStatLabels(); // FIX: cập nhật stat cards sau khi có dữ liệu
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
        if ("PAID".equals(status) || "CANCELED".equals(status)) {
            AlertUtil.showInfo("Thông báo", "Phiên đấu giá này không thể hủy (trạng thái: " + status + ").");
            return;
        }

        String auctionId = getJsonString(selectedAuction, "auctionId");
        String title     = getJsonString(selectedAuction, "title");

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận hủy phiên");
        confirm.setHeaderText("Bạn có chắc chắn muốn HỦY phiên đấu giá này?");
        confirm.setContentText("\"" + title + "\"\nHành động này không thể hoàn tác.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            new Thread(() -> {
                try {
                    Map<String, Object> params = new HashMap<>();
                    params.put("auctionId", auctionId);
                    // FIX: dùng CANCEL_AUCTION thay vì ADMIN_CLOSE_AUCTION
                    // ADMIN_CLOSE_AUCTION → closeAuction() → FINISHED (sai)
                    // CANCEL_AUCTION      → cancelAuction() → CANCELED  (đúng)
                    SocketClient.getInstance().send(Actions.CANCEL_AUCTION, params, JsonObject.class);

                    Platform.runLater(() -> {
                        AlertUtil.showInfo("Thành công", "Đã hủy phiên: " + title);
                        loadAuctions();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> AlertUtil.showError("Lỗi", e.getMessage()));
                }
            }).start();
        }
    }

    // =================================================================================
    // 3. XỬ LÝ BẢNG LỊCH SỬ ĐẶT GIÁ (FIX: toàn bộ phần này bị thiếu)
    // =================================================================================
    private void setupBidTable() {
        if (colBidNo == null) return; // guard nếu FXML chưa inject

        // Cột # — số thứ tự tự sinh theo vị trí hàng
        colBidNo.setCellValueFactory(data -> {
            int idx = tvBidHistory.getItems().indexOf(data.getValue()) + 1;
            return new SimpleStringProperty(String.valueOf(idx));
        });
        colBidder.setCellValueFactory(data ->
            new SimpleStringProperty(getJsonString(data.getValue(), "bidderName")));
        colBidProduct.setCellValueFactory(data ->
            new SimpleStringProperty(getJsonString(data.getValue(), "productTitle")));
        colBidAmount.setCellValueFactory(data -> {
            try {
                long amt = data.getValue().has("amount")
                    ? data.getValue().get("amount").getAsLong() : 0L;
                return new SimpleStringProperty(formatMoney(amt));
            } catch (Exception e) {
                return new SimpleStringProperty("—");
            }
        });
        colBidTime.setCellValueFactory(data ->
            new SimpleStringProperty(getJsonString(data.getValue(), "timestamp")));
        colBidStatus.setCellValueFactory(data -> {
            String auto = getJsonString(data.getValue(), "isAutoBid");
            return new SimpleStringProperty("true".equalsIgnoreCase(auto) ? "AUTO" : "THƯỜNG");
        });
    }

    private void loadBids() {
        new Thread(() -> {
            try {
                com.google.gson.JsonElement response = SocketClient.getInstance()
                    .send("GET_ALL_BIDS", new HashMap<>(), com.google.gson.JsonElement.class);

                System.out.println(">>> RAW BID HISTORY: " + response);

                if (response != null) {
                    JsonArray arr = null;
                    if (response.isJsonArray()) {
                        arr = response.getAsJsonArray();
                    } else if (response.isJsonObject()) {
                        JsonObject obj = response.getAsJsonObject();
                        if (obj.has("bids"))      arr = obj.getAsJsonArray("bids");
                        else if (obj.has("data")) arr = obj.getAsJsonArray("data");
                    }

                    if (arr != null) {
                        final JsonArray finalArr = arr;
                        Platform.runLater(() -> {
                            bidList.clear();
                            finalArr.forEach(e -> bidList.add(e.getAsJsonObject()));
                            System.out.println("✅ ADMIN Đã tải " + bidList.size() + " lượt đặt giá!");
                            updateBidCountLabel();
                        });
                    } else {
                        System.err.println("❌ Không tìm thấy mảng bids trong response!");
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Lỗi Admin tải Bids: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    // =================================================================================
    // 4. HANDLER ĐƯỢC FXML GỌI (đảm bảo không bị lỗi resolving)
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
        // FIX: trước đây là stub rỗng — giờ thực sự lọc bảng
        String q = txtBidSearch == null ? "" : txtBidSearch.getText().trim().toLowerCase();
        if (filteredBids != null) filteredBids.setPredicate(makeBidPredicate(q));
        updateBidCountLabel();
    }

    @FXML
    private void handleRefreshBids(ActionEvent event) {
        // FIX: trước đây là stub rỗng — giờ thực sự reload
        loadBids();
    }

    // =================================================================================
    // 5. CÁC HÀM TIỆN ÍCH (HELPER)
    // =================================================================================
    @FXML
    void handleLogout(ActionEvent event) {
        try {
            ViewLoader.load(event, "login.fxml", "Đăng nhập hệ thống");
        } catch (Exception e) {
            AlertUtil.showError("Lỗi", "Không thể quay lại màn hình đăng nhập.");
        }
    }

    @FXML
    void handleOpenWallet(ActionEvent event) {
        try {
            javafx.scene.Parent walletView = ViewLoader.loadView("admin-wallet.fxml");
            if (walletView != null) {
                Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
                stage.getScene().setRoot(walletView);
                stage.setTitle("💼 Ví Quản Trị – Hoa Hồng Hệ Thống");
            } else {
                AlertUtil.showError("Lỗi", "Không thể tải màn hình ví.");
            }
        } catch (Exception e) {
            AlertUtil.showError("Lỗi", "Không thể mở ví: " + e.getMessage());
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

    private Predicate<JsonObject> makeBidPredicate(String q) {
        return bid -> {
            if (bid == null) return false;
            if (q == null || q.isEmpty()) return true;
            String bidder  = getJsonString(bid, "bidderName").toLowerCase();
            String product = getJsonString(bid, "productTitle").toLowerCase();
            return bidder.contains(q) || product.contains(q);
        };
    }

    private void updateBidCountLabel() {
        if (lblBidHistoryCount != null) {
            int count = filteredBids == null ? bidList.size() : filteredBids.size();
            lblBidHistoryCount.setText(count + " lượt đặt giá");
        }
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

    /**
     * FIX: Tính toán và cập nhật 5 stat cards ở header.
     * Được gọi sau mỗi lần loadUsers() hoặc loadAuctions() hoàn thành.
     */
    private void updateStatLabels() {
        // --- Thống kê người dùng ---
        long totalUsers  = userList.size();
        long lockedUsers = userList.stream()
            .filter(u -> "LOCKED".equalsIgnoreCase(getJsonString(u, "status")))
            .count();

        if (lblTotalUsers  != null) lblTotalUsers.setText(String.valueOf(totalUsers));
        if (lblLockedUsers != null) lblLockedUsers.setText(String.valueOf(lockedUsers));

        // --- Thống kê phiên đấu giá ---
        long totalAuctions  = auctionList.size();
        long activeAuctions = auctionList.stream()
            .filter(a -> {
                String s = getJsonString(a, "status");
                return "RUNNING".equalsIgnoreCase(s) || "OPEN".equalsIgnoreCase(s);
            })
            .count();
        // Tổng lượt đặt giá = cộng dồn bidCount của tất cả phiên
        long totalBids = auctionList.stream()
            .mapToLong(a -> {
                try { return a.has("bidCount") ? a.get("bidCount").getAsLong() : 0L; }
                catch (Exception e) { return 0L; }
            })
            .sum();

        if (lblTotalAuctions  != null) lblTotalAuctions.setText(String.valueOf(totalAuctions));
        if (lblActiveAuctions != null) lblActiveAuctions.setText(String.valueOf(activeAuctions));
        if (lblTotalBids      != null) lblTotalBids.setText(String.valueOf(totalBids));
    }
}