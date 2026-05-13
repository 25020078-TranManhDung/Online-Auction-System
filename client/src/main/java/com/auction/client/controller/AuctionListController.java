package com.auction.client.controller;

import com.auction.client.model.UserSession;
import com.auction.client.network.SocketClient;
import com.auction.client.util.AlertUtil;
import com.auction.client.util.ViewLoader;
import com.auction.shared.enums.AuctionStatus;
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
import javafx.scene.image.ImageView;
import javafx.util.Callback;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class AuctionListController {

    // ─── Header ───────────────────────────────────────────────
    @FXML
    private ImageView imgAvatar;
    @FXML
    private Label lblUser;
    @FXML
    private Label lblRole;
    @FXML
    private Button btnLogout;
    @FXML
    private Label lblWalletBalance;

    // ─── Toolbar / filters ────────────────────────────────────
    @FXML
    private TextField txtSearch;
    @FXML
    private ComboBox<String> cboFilter;
    @FXML
    private Button btnRefresh;
    @FXML
    private Label lblAuctionCount;
    @FXML
    private Label lblMainTitle;          // Tiêu đề động cập nhật theo danh mục

    // ─── Table ────────────────────────────────────────────────
    @FXML
    private TableView<JsonObject> tbAuctions;
    @FXML
    private TableColumn<JsonObject, String> colNo;
    @FXML
    private TableColumn<JsonObject, String> colName;
    @FXML
    private TableColumn<JsonObject, String> colSeller;
    @FXML
    private TableColumn<JsonObject, String> colPrice;
    @FXML
    private TableColumn<JsonObject, String> colEndTime;
    @FXML
    private TableColumn<JsonObject, String> colBidCount;
    @FXML
    private TableColumn<JsonObject, String> colStatus;
    @FXML
    private TableColumn<JsonObject, Void> colAction;

    @FXML
    private Label lblMessage;

    // ─── Data & state ─────────────────────────────────────────
    private final ObservableList<JsonObject> auctionList = FXCollections.observableArrayList();
    private FilteredList<JsonObject> filteredAuctions;

    /** Lưu lại placeholder đẹp từ FXML để khôi phục sau khi spinner tải xong */
    private Node emptyPlaceholder;

    /** Danh mục đang được chọn ở sidebar; "ALL" nghĩa là hiển thị tất cả */
    private String currentCategory = "ALL";

    // ==========================================================
    //  KHỞI TẠO
    // ==========================================================

    @FXML
    public void initialize() {
        setupTableColumns();
        loadUserInfo();

        // 1. Lưu lại giao diện thông báo trống từ FXML (trước khi spinner đè lên)
        if (tbAuctions != null) {
            this.emptyPlaceholder = tbAuctions.getPlaceholder();
        }

        // 2. Wrap với filtered + sorted lists
        filteredAuctions = new FilteredList<>(auctionList, p -> true);
        SortedList<JsonObject> sorted = new SortedList<>(filteredAuctions);
        sorted.comparatorProperty().bind(tbAuctions.comparatorProperty());
        tbAuctions.setItems(sorted);

        // 3. Populate filter combobox từ Enum AuctionStatus
        if (cboFilter != null) {
            cboFilter.getItems().clear();
            ObservableList<String> statusList = FXCollections.observableArrayList("Tất cả");
            for (AuctionStatus status : AuctionStatus.values()) {
                statusList.add(status.name());
            }
            cboFilter.setItems(statusList);
            cboFilter.setValue("Tất cả");
        }

        // 4. Listeners cho tìm kiếm và lọc trạng thái
        if (txtSearch != null) {
            txtSearch.textProperty().addListener((obs, oldV, newV) -> applyAuctionFilter());
        }
        if (cboFilter != null) {
            cboFilter.setOnAction(e -> applyAuctionFilter());
        }

        // 5. Tải dữ liệu ban đầu
        loadAuctions();
        loadWalletBalance();
    }

    // ==========================================================
    //  THÔNG TIN NGƯỜI DÙNG
    // ==========================================================

    private void loadUserInfo() {
        UserSession session = UserSession.getInstance();
        if (lblUser != null) {
            String name = session.getUsername();
            lblUser.setText(name != null && !name.isEmpty() ? name : "Người dùng");
        }
        if (lblRole != null) {
            String role = session.getRole();
            lblRole.setText(role != null && !role.isEmpty() ? role : "");
        }
    }

    // ==========================================================
    //  THIẾT LẬP CỘT BẢNG
    // ==========================================================

    private void setupTableColumns() {
        if (colNo != null) {
            colNo.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(tbAuctions.getItems().indexOf(data.getValue()) + 1)));
        }
        if (colName != null) {
            colName.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "title")));
        }
        if (colSeller != null) {
            colSeller.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "sellerId")));
        }
        if (colPrice != null) {
            colPrice.setCellValueFactory(data -> {
                long price = data.getValue().has("currentPrice") ? data.getValue().get("currentPrice").getAsLong() : 0;
                return new SimpleStringProperty(formatMoney(price));
            });
        }
        if (colEndTime != null) {
            colEndTime.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "endTime")));
        }
        if (colBidCount != null) {
            colBidCount.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "bidCount")));
        }
        if (colStatus != null) {
            colStatus.setCellValueFactory(data -> new SimpleStringProperty(getJsonString(data.getValue(), "status")));
        }

        // ===== Action column: tạo nút Xem và mở chi tiết (ĐÃ ĐƯỢC CẬP NHẬT) =====
        if (colAction != null) {
            colAction.setCellFactory(new Callback<>() {
                @Override
                public TableCell<JsonObject, Void> call(TableColumn<JsonObject, Void> param) {
                    return new TableCell<>() {
                        private final Button btn = new Button("Xem");

                        {
                            // Thiết kế giao diện cho nút Xem
                            btn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-cursor: hand;");

                            // Bắt sự kiện khi click vào nút
                            btn.setOnAction(event -> {
                                JsonObject auction = getTableView().getItems().get(getIndex());
                                // Truyền thêm 'event' vào hàm để lấy được Cửa sổ (Stage) hiện tại
                                openAuctionDetail(auction, event);
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
        }
    }

    // ==========================================================
    //  MỞ CHI TIẾT PHIÊN ĐẤU GIÁ
    // ==========================================================

    /**
     * Mở màn hình chi tiết phiên đấu giá (Chế độ toàn màn hình - SPA).
     * Đã loại bỏ mainContentArea để giao diện không bị lồng ghép header/menu.
     */
    private void openAuctionDetail(JsonObject auction, javafx.event.ActionEvent event) {
        if (auction == null) {
            AlertUtil.showWarning("Lỗi", "Dữ liệu phiên rỗng.");
            return;
        }

        String auctionId = getJsonString(auction, "auctionId");
        String title = getJsonString(auction, "title");

        System.out.println("openAuctionDetail -> auctionId=" + auctionId + ", title=" + title);

        if (auctionId == null || auctionId.isEmpty()) {
            AlertUtil.showWarning("Lỗi dữ liệu", "Phiên đấu giá chưa có ID hợp lệ.");
            return;
        }

        try {
            // 1. Tải Giao diện + Controller từ ViewLoader
            ViewLoader.ViewResult<com.auction.client.controller.AuctionDetailController> result =
                    ViewLoader.loadViewWithController("auction-detail.fxml");

            if (result != null) {
                // 2. Kích hoạt nạp dữ liệu chi tiết từ Server
                com.auction.client.controller.AuctionDetailController detailController = result.getController();
                detailController.initData(auctionId);

                // 3. THAY THẾ TOÀN BỘ GIAO DIỆN TRÊN CỬA SỔ HIỆN TẠI
                // Lấy ra Stage (cửa sổ) đang chạy từ sự kiện click nút "Xem"
                javafx.stage.Stage stage = (javafx.stage.Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();

                // Thay thế Root của Scene hiện tại bằng giao diện chi tiết.
                // Lệnh này giúp màn hình chi tiết chiếm trọn 100% diện tích cửa sổ hiện tại.
                stage.getScene().setRoot(result.getView());

                // Cập nhật tiêu đề cho chuyên nghiệp
                stage.setTitle("Chi tiết phiên đấu giá - " + (title.isEmpty() ? auctionId : title));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            AlertUtil.showError("Lỗi", "Không thể mở chi tiết phiên: " + ex.getMessage());
        }
    }

    // ==========================================================
    //  LỌC DỮ LIỆU
    // ==========================================================

    private void applyAuctionFilter() {
        String q      = txtSearch == null ? "" : txtSearch.getText().trim().toLowerCase();
        String status = cboFilter == null ? "Tất cả" : cboFilter.getValue();
        filteredAuctions.setPredicate(makeAuctionPredicate(q, status));
        updateAuctionCount();
    }

    private Predicate<JsonObject> makeAuctionPredicate(String q, String statusFilter) {
        return auction -> {
            if (auction == null) return false;

            // 1. LỌC THEO DANH MỤC (Category) — logic mới thêm vào
            // Nếu currentCategory khác "ALL", kiểm tra trường "category" trong JSON
            if (!"ALL".equals(currentCategory)) {
                String category = getJsonString(auction, "category");
                // Nếu danh mục không khớp thì loại bỏ
                if (!currentCategory.equalsIgnoreCase(category)) {
                    return false;
                }
            }

            // 2. LỌC THEO TRẠNG THÁI (code cũ giữ nguyên)
            if (statusFilter != null && !statusFilter.isEmpty() && !"Tất cả".equals(statusFilter)) {
                String s = getJsonString(auction, "status").toLowerCase();
                if (!s.contains(statusFilter.toLowerCase())) return false;
            }

            // 3. LỌC THEO TỪ KHÓA TÌM KIẾM (code cũ giữ nguyên)
            if (q == null || q.isEmpty()) return true;
            String title  = getJsonString(auction, "title").toLowerCase();
            String seller = getJsonString(auction, "sellerId").toLowerCase();
            return title.contains(q) || seller.contains(q);
        };
    }

    private void updateAuctionCount() {
        if (lblAuctionCount != null) {
            lblAuctionCount.setText(String.format("%d phiên",
                filteredAuctions == null ? auctionList.size() : filteredAuctions.size()));
        }
    }

    // ==========================================================
    //  HANDLERS — MENU DANH MỤC SIDEBAR (logic mới)
    // ==========================================================

    @FXML
    public void showAllItems() {
        currentCategory = "ALL";
        if (lblMainTitle != null) lblMainTitle.setText("Danh sách phiên đấu giá");
        applyAuctionFilter();
    }

    @FXML
    public void showArts() {
        currentCategory = "Art";
        if (lblMainTitle != null) lblMainTitle.setText("Danh sách: Nghệ thuật (Art)");
        applyAuctionFilter();
    }

    @FXML
    public void showElectronics() {
        currentCategory = "Electronics";
        if (lblMainTitle != null) lblMainTitle.setText("Danh sách: Đồ điện tử (Electronics)");
        applyAuctionFilter();
    }

    @FXML
    public void showVehicles() {
        currentCategory = "Vehicle";
        if (lblMainTitle != null) lblMainTitle.setText("Danh sách: Phương tiện (Vehicle)");
        applyAuctionFilter();
    }

    @FXML
    public void showOthers() {
        currentCategory = "Other";
        if (lblMainTitle != null) lblMainTitle.setText("Danh sách: Tài sản khác");
        applyAuctionFilter();
    }

    // ==========================================================
    //  HANDLERS — TOOLBAR (code cũ giữ nguyên)
    // ==========================================================

    @FXML
    private void handleSearch(KeyEvent event) {
        applyAuctionFilter();
    }

    @FXML
    private void handleFilter(ActionEvent event) {
        applyAuctionFilter();
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        System.out.println("[Client] Đang gửi yêu cầu làm mới dữ liệu lên Server...");
        loadAuctions();
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            ViewLoader.load(event, "login.fxml", "Đăng nhập hệ thống");
        } catch (Exception e) {
            AlertUtil.showError("Lỗi", "Không thể quay lại màn hình đăng nhập.");
        }
    }

    // ==========================================================
    //  TẢI DỮ LIỆU TỪ SERVER (code cũ + fix emptyPlaceholder)
    // ==========================================================

    private void loadAuctions() {
        if (lblMessage != null) lblMessage.setVisible(false);

        // 1. XÓA DỮ LIỆU CŨ VÀ KHÓA NÚT LÀM MỚI
        auctionList.clear();
        if (btnRefresh != null) btnRefresh.setDisable(true);

        // 2. HIỂN THỊ VÒNG TRÒN XOAY CHỜ (Loading Spinner)
        ProgressIndicator loadingSpinner = new ProgressIndicator();
        loadingSpinner.setMaxSize(40, 40);
        tbAuctions.setPlaceholder(loadingSpinner);

        new Thread(() -> {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("status", "ALL");

                // Gửi yêu cầu lên Server
                com.google.gson.JsonElement response = SocketClient.getInstance().send(
                    "GET_AUCTIONS", params, com.google.gson.JsonElement.class
                );

                Platform.runLater(() -> {
                    if (response != null) {
                        // Xử lý JSON — hỗ trợ cả dạng Array lẫn Object có key "auctions"
                        JsonArray arr = null;
                        if (response.isJsonArray()) {
                            arr = response.getAsJsonArray();
                        } else if (response.isJsonObject()) {
                            JsonObject obj = response.getAsJsonObject();
                            if (obj.has("auctions")) arr = obj.getAsJsonArray("auctions");
                        }

                        if (arr != null) {
                            // 3. ĐỔ DỮ LIỆU MỚI VÀO
                            arr.forEach(el -> auctionList.add(el.getAsJsonObject()));
                            updateAuctionCount();
                        }
                    }

                    // 4. QUAN TRỌNG: Trả lại giao diện "Trống" thẩm mỹ đã lưu từ FXML.
                    // Khi gán lại emptyPlaceholder, nếu auctionList rỗng sẽ hiện hộp 📦
                    // đã thiết kế sẵn trong FXML thay vì spinner xoay mãi hoặc Label đơn giản.
                    tbAuctions.setPlaceholder(emptyPlaceholder);

                    // Mở khóa lại nút làm mới
                    if (btnRefresh != null) btnRefresh.setDisable(false);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (btnRefresh != null) btnRefresh.setDisable(false);

                    // Nếu lỗi: hiện thông báo lỗi có style thay vì spinner
                    Label errorLbl = new Label("Lỗi tải dữ liệu: " + e.getMessage());
                    errorLbl.setStyle("-fx-text-fill: #e67e22; -fx-font-style: italic;");
                    tbAuctions.setPlaceholder(errorLbl);
                });
                e.printStackTrace();
            }
        }).start();
    }

    // ==========================================================
    //  VÍ ĐIỆN TỬ (LOGIC MỚI)
    // ==========================================================

    private void loadWalletBalance() {
        new Thread(() -> {
            try {
                Map<String, Object> params = new HashMap<>();
                // Gửi request lấy thông tin ví (Dùng String "GET_WALLET" để khớp với style code hiện tại của em)
                JsonObject data = SocketClient.getInstance().send("GET_WALLET", params, JsonObject.class);

                if (data != null) {
                    // Ưu tiên lấy Số dư khả dụng
                    long availableBalance = data.has("availableBalance") ? data.get("availableBalance").getAsLong() : 0;

                    Platform.runLater(() -> {
                        if (lblWalletBalance != null) {
                            // Tận dụng luôn hàm formatMoney có sẵn của em ở dưới cùng
                            lblWalletBalance.setText(formatMoney(availableBalance));
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    if (lblWalletBalance != null) lblWalletBalance.setText("Lỗi tải ví");
                });
            }
        }).start();
    }

    @FXML
    public void handleOpenWallet(ActionEvent event) {
        try {
            // 1. Tải file giao diện Ví
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/auction/client/fxml/bidder-wallet.fxml"));
            javafx.scene.Parent root = loader.load();

            // 2. Lấy cửa sổ (Stage) hiện tại đang chạy
            javafx.stage.Stage stage = (javafx.stage.Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();

            // 3. Thay thế nội dung cửa sổ bằng giao diện Ví (Cách này giữ nguyên được CSS nền)
            stage.getScene().setRoot(root);
            stage.setTitle("Ví Điện Tử - Quản lý số dư");

        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.showError("Lỗi", "Không thể mở ví: " + e.getMessage());
        }
    }

    // ==========================================================
    //  HELPERS
    // ==========================================================

    private String getJsonString(JsonObject obj, String key) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull())
            ? obj.get(key).getAsString() : "";
    }

    private String formatMoney(long amount) {
        return String.format("%,d VNĐ", amount);
    }
}