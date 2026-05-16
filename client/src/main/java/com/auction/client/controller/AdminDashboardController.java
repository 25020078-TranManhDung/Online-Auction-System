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
import com.auction.client.model.UserSession;
import javafx.scene.input.MouseEvent;
import javafx.stage.Popup;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

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
    @FXML private TableColumn<JsonObject, Integer> colViolationCount;
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

    // ===== Profile Popup & User =====
    @FXML private Label lblUser;
    @FXML private HBox headerUserArea;
    private Popup profilePopup;

    // Data lists
    private final ObservableList<JsonObject> userList    = FXCollections.observableArrayList();
    private final ObservableList<JsonObject> auctionList = FXCollections.observableArrayList();
    private final ObservableList<JsonObject> bidList     = FXCollections.observableArrayList();

    // Filtered/Sorted wrappers
    private FilteredList<JsonObject> filteredUsers;
    private FilteredList<JsonObject> filteredAuctions;
    private FilteredList<JsonObject> filteredBids;

    @FXML private Button btnTheme;  // Dark/Light mode toggle

    public void initialize() {
        if (btnTheme != null) btnTheme.setText(com.auction.client.util.ThemeManager.getInstance().getToggleIcon());

        // Load tên Admin hiện tại
        if (lblUser != null) {
            String username = UserSession.getInstance().getUsername();
            lblUser.setText(username != null ? username : "Quản trị viên");
        }

        // Hiệu ứng hover cho vùng Avatar
        if (headerUserArea != null) {
            headerUserArea.setOnMouseEntered(e ->
                headerUserArea.setStyle("-fx-cursor: hand; -fx-padding: 4 10; -fx-background-radius: 12; -fx-background-color: rgba(155,89,182,0.12);"));
            headerUserArea.setOnMouseExited(e ->
                headerUserArea.setStyle("-fx-cursor: hand; -fx-padding: 4 10; -fx-background-radius: 12; -fx-background-color: transparent;"));
        }
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
            cboRoleFilter.getItems().addAll("Tất cả", "BIDDER", "SELLER"); // Admin không quản lý Admin khác
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

        // Cột Vi phạm — màu theo mức độ nguy hiểm
        colViolationCount.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setText(null); setStyle(""); return; }
                JsonObject user = getTableView().getItems().get(getIndex());
                int count = 0;
                try { count = user.has("violationCount") ? user.get("violationCount").getAsInt() : 0; } catch (Exception ignored) {}
                setText(String.valueOf(count));
                String color = count == 0 ? "#27ae60"       // xanh — sạch
                    : count < 3  ? "#f39c12"       // vàng — cảnh cáo nhẹ
                    : count < 7  ? "#e67e22"       // cam  — nguy hiểm
                    : "#e74c3c";      // đỏ   — sắp/đã khoá nặng
                setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
            }
        });

        // Tạo nút Khóa/Mở cho từng dòng trong cột Hành động
        colUserAction.setCellFactory(new Callback<>() {
            @Override
            public TableCell<JsonObject, Void> call(TableColumn<JsonObject, Void> param) {
                return new TableCell<>() {
                    private final Button btnBan    = new Button("Xử lý");
                    private final Button btnUnlock = new Button("Mở khoá");
                    private final javafx.scene.layout.HBox box =
                        new javafx.scene.layout.HBox(5, btnBan, btnUnlock);

                    {
                        btnBan.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 11px;");
                        btnUnlock.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 11px;");

                        btnBan.setOnAction(event -> {
                            JsonObject user = getTableView().getItems().get(getIndex());
                            handleBanUser(user);
                        });
                        btnUnlock.setOnAction(event -> {
                            JsonObject user = getTableView().getItems().get(getIndex());
                            handleUnlockUser(user);
                        });
                    }

                    @Override
                    protected void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) { setGraphic(null); return; }
                        JsonObject user = getTableView().getItems().get(getIndex());
                        String status = getJsonString(user, "status");
                        // Ẩn nút mở khoá nếu tài khoản đang ACTIVE
                        boolean isLocked = "TEMP_LOCKED".equals(status) || "PERM_LOCKED".equals(status) || "LOCKED".equals(status);
                        btnUnlock.setVisible(isLocked);
                        btnUnlock.setManaged(isLocked);
                        setGraphic(box);
                    }
                };
            }
        });
    }

    /** Hiện ChoiceDialog để admin chọn mức xử lý vi phạm */
    private void handleBanUser(JsonObject user) {
        String targetUserId = getJsonString(user, "id");
        String username     = getJsonString(user, "username");

        javafx.scene.control.ChoiceDialog<String> dialog = new javafx.scene.control.ChoiceDialog<>(
            "Cảnh cáo (Warn)",
            "Cảnh cáo (Warn)",
            "Khoá tạm — 1 ngày",
            "Khoá tạm — 7 ngày",
            "Khoá tạm — 30 ngày",
            "Khoá vĩnh viễn"
        );
        dialog.setTitle("Xử lý vi phạm");
        dialog.setHeaderText("Tài khoản: " + username);
        dialog.setContentText("Chọn mức độ xử lý:");

        dialog.showAndWait().ifPresent(choice -> {
            String action = switch (choice) {
                case "Cảnh cáo (Warn)"      -> "WARN";
                case "Khoá tạm — 1 ngày"   -> "TEMP_1D";
                case "Khoá tạm — 7 ngày"   -> "TEMP_7D";
                case "Khoá tạm — 30 ngày"  -> "TEMP_30D";
                case "Khoá vĩnh viễn"       -> "PERM";
                default -> null;
            };
            if (action == null) return;

            new Thread(() -> {
                try {
                    Map<String, Object> params = new HashMap<>();
                    params.put("userId", targetUserId);
                    params.put("action", action);
                    JsonObject result = SocketClient.getInstance().send("BAN_USER", params, JsonObject.class);
                    String msg = result != null && result.has("message")
                        ? result.get("message").getAsString()
                        : "Đã xử lý thành công.";
                    Platform.runLater(() -> {
                        // Admin chỉ thấy xác nhận gọn — nội dung cảnh báo đã push tới cửa sổ của user
                        String adminMsg = "WARN".equals(action)
                            ? "✅ Đã gửi cảnh báo tới tài khoản: " + username
                            : "✅ Đã xử lý tài khoản: " + username;
                        AlertUtil.showInfo("Thành công", adminMsg);
                        loadUsers();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> AlertUtil.showError("Lỗi", e.getMessage()));
                }
            }).start();
        });
    }

    /** Mở khoá thủ công — admin → ACTIVE */
    private void handleUnlockUser(JsonObject user) {
        String targetUserId = getJsonString(user, "id");
        String username     = getJsonString(user, "username");

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Mở khoá tài khoản");
        confirm.setHeaderText("Bạn có chắc muốn mở khoá tài khoản: " + username + "?");
        confirm.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            new Thread(() -> {
                try {
                    Map<String, Object> params = new HashMap<>();
                    params.put("userId", targetUserId);
                    params.put("action", "UNLOCK");
                    SocketClient.getInstance().send("BAN_USER", params, JsonObject.class);
                    Platform.runLater(() -> {
                        AlertUtil.showInfo("Thành công", "Đã mở khoá tài khoản: " + username);
                        loadUsers();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> AlertUtil.showError("Lỗi", e.getMessage()));
                }
            }).start();
        });
    }

    private void handleToggleUserStatus(JsonObject user) {
        handleBanUser(user); // Redirect sang handleBanUser
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

    private void loadUsers() {
        new Thread(() -> {
            try {
                com.google.gson.JsonElement response = SocketClient.getInstance()
                    .send("GET_ALL_USERS", new HashMap<>(), com.google.gson.JsonElement.class);

                if (response != null) {
                    com.google.gson.JsonArray arr = null;
                    if (response.isJsonArray()) {
                        arr = response.getAsJsonArray();
                    } else if (response.isJsonObject()) {
                        JsonObject obj = response.getAsJsonObject();
                        if (obj.has("users"))      arr = obj.getAsJsonArray("users");
                        else if (obj.has("data"))  arr = obj.getAsJsonArray("data");
                    }
                    if (arr != null) {
                        final com.google.gson.JsonArray finalArr = arr;
                        Platform.runLater(() -> {
                            userList.clear();
                            finalArr.forEach(el -> {
                                JsonObject user = el.getAsJsonObject();
                                // Admin không cần quản lý tài khoản ADMIN khác
                                String role = user.has("role") ? user.get("role").getAsString() : "";
                                if (!"ADMIN".equalsIgnoreCase(role)) {
                                    userList.add(user);
                                }
                            });
                            updateUserCountLabel();
                            updateStatLabels();
                        });
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Lỗi Admin tải Users: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
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

            // Safety net: không bao giờ hiện tài khoản ADMIN trong danh sách quản lý
            String userRole = getJsonString(user, "role");
            if ("ADMIN".equalsIgnoreCase(userRole)) return false;

            if (roleFilter != null && !roleFilter.isEmpty() && !"Tất cả".equals(roleFilter)) {
                if (!userRole.equalsIgnoreCase(roleFilter)) return false;
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

    // ==========================================================
    //  PROFILE POPUP & ĐỔI MẬT KHẨU
    // ==========================================================

    @FXML
    private void handleProfileClick(MouseEvent event) {
        if (profilePopup == null) {
            profilePopup = buildProfilePopup();
        }

        if (profilePopup.isShowing()) {
            profilePopup.hide();
            return;
        }

        javafx.scene.Node source = (javafx.scene.Node) event.getSource();
        double anchorX = source.localToScreen(source.getBoundsInLocal()).getMinX() - 40;
        double anchorY = source.localToScreen(source.getBoundsInLocal()).getMaxY() + 8;
        profilePopup.show(source, anchorX, anchorY);
    }

    private Popup buildProfilePopup() {
        UserSession session = UserSession.getInstance();
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);

        StackPane wrapper = new StackPane();
        wrapper.setPadding(new Insets(8));

        VBox card = new VBox(0);
        card.setPrefWidth(300);
        card.setStyle("-fx-background-color: rgba(35, 10, 60, 0.93); -fx-background-radius: 18; -fx-border-color: rgba(155, 89, 182, 0.40); -fx-border-width: 1.5; -fx-border-radius: 18;");

        DropShadow ds = new DropShadow();
        ds.setColor(Color.rgb(67, 20, 118, 0.55)); ds.setRadius(28); ds.setOffsetY(8);
        card.setEffect(ds);

        // TOP SECTION
        VBox topSection = new VBox(6);
        topSection.setAlignment(Pos.CENTER);
        topSection.setPadding(new Insets(24, 20, 18, 20));

        ImageView popupAvatar = new ImageView();
        popupAvatar.setFitWidth(64); popupAvatar.setFitHeight(64); popupAvatar.setPreserveRatio(true);
        try { popupAvatar.setImage(new Image(getClass().getResourceAsStream("/com/auction/client/images/default_avatar.png"), 64, 64, true, true)); } catch (Exception ignored) {}
        popupAvatar.setClip(new Circle(32, 32, 32));

        DropShadow avatarGlow = new DropShadow(); avatarGlow.setColor(Color.rgb(155, 89, 182, 0.70)); avatarGlow.setRadius(14);
        popupAvatar.setEffect(avatarGlow);

        Label lblFullName = new Label(nullSafe(session.getFullName(), session.getUsername()));
        lblFullName.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #f0e6ff; -fx-padding: 8 0 2 0;");

        Label lblAtUsername = new Label("@" + nullSafe(session.getUsername(), "—"));
        lblAtUsername.setStyle("-fx-font-size: 13px; -fx-text-fill: #9b59b6;");

        topSection.getChildren().addAll(popupAvatar, lblFullName, lblAtUsername);

        // INFO SECTION
        VBox infoSection = new VBox(10);
        infoSection.setPadding(new Insets(14, 24, 14, 24));
        infoSection.getChildren().addAll(
            infoRow("👤", "User ID", nullSafe(session.getUserId(), "—")),
            infoRow("✉", "Email", nullSafe(session.getEmail(), "Chưa cập nhật")),
            infoRow("🏷", "Vai trò", nullSafe(session.getRole(), "—"))
        );

        // ACTION SECTION
        VBox actionSection = new VBox(10);
        actionSection.setAlignment(Pos.CENTER);
        actionSection.setPadding(new Insets(14, 20, 20, 20));

        Button btnChangePassword = new Button("🔐  Đổi mật khẩu");
        btnChangePassword.setMaxWidth(Double.MAX_VALUE);
        btnChangePassword.setStyle("-fx-background-color: linear-gradient(to right, #6c3483, #8e44ad); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13.5px; -fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 10 20;");
        btnChangePassword.setOnMouseEntered(e -> btnChangePassword.setStyle("-fx-background-color: linear-gradient(to right, #7d3c98, #9b59b6); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13.5px; -fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 10 20; -fx-effect: dropshadow(gaussian, rgba(142,68,173,0.55), 10, 0, 0, 2);"));
        btnChangePassword.setOnMouseExited(e -> btnChangePassword.setStyle("-fx-background-color: linear-gradient(to right, #6c3483, #8e44ad); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13.5px; -fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 10 20;"));

        btnChangePassword.setOnAction(e -> {
            popup.hide();
            handleChangePassword();
        });

        actionSection.getChildren().add(btnChangePassword);
        card.getChildren().addAll(topSection, styledDivider(), infoSection, styledDivider(), actionSection);
        wrapper.getChildren().add(card);
        popup.getContent().add(wrapper);

        return popup;
    }

    private HBox infoRow(String icon, String labelText, String value) {
        Label iconLbl = new Label(icon); iconLbl.setStyle("-fx-font-size: 14px;"); iconLbl.setMinWidth(22);
        Label keyLbl = new Label(labelText + ":"); keyLbl.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #9b59b6; -fx-min-width: 70;");
        Label valLbl = new Label(value); valLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #dcd0ff; -fx-font-weight: bold;"); valLbl.setWrapText(true);
        HBox row = new HBox(8, iconLbl, keyLbl, valLbl); row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Separator styledDivider() {
        Separator sep = new Separator(); sep.setStyle("-fx-background-color: rgba(155,89,182,0.30); -fx-padding: 0 20;");
        VBox.setMargin(sep, new Insets(0, 20, 0, 20)); return sep;
    }

    private String nullSafe(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private void handleChangePassword() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/auction/client/fxml/change-password.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage modalStage = new javafx.stage.Stage();
            modalStage.setTitle("Đổi mật khẩu");
            modalStage.setScene(new javafx.scene.Scene(root));
            modalStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            if (lblUser != null && lblUser.getScene() != null) {
                modalStage.initOwner(lblUser.getScene().getWindow());
            }
            modalStage.setResizable(false);
            modalStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.showError("Lỗi", "Không thể mở màn hình đổi mật khẩu: " + e.getMessage());
        }
    }

    @FXML
    private void handleToggleTheme(javafx.event.ActionEvent event) {
        com.auction.client.util.ThemeManager tm =
            com.auction.client.util.ThemeManager.getInstance();
        tm.toggle();
        if (btnTheme != null) btnTheme.setText(tm.getToggleIcon());
    }

}