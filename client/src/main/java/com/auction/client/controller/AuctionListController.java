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
<<<<<<< HEAD
import javafx.scene.Node;
=======
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
>>>>>>> main
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.image.ImageView;
import javafx.util.Callback;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class AuctionListController {

<<<<<<< HEAD
    // ─── Header ───────────────────────────────────────────────
=======
    // Header
>>>>>>> main
    @FXML
    private ImageView imgAvatar;
    @FXML
    private Label lblUser;
    @FXML
    private Label lblRole;
    @FXML
    private Button btnLogout;

<<<<<<< HEAD
    // ─── Toolbar / filters ────────────────────────────────────
=======
    // Toolbar / filters
>>>>>>> main
    @FXML
    private TextField txtSearch;
    @FXML
    private ComboBox<String> cboFilter;
    @FXML
    private Button btnRefresh;
    @FXML
    private Label lblAuctionCount;
    @FXML
<<<<<<< HEAD
    private Label lblMainTitle;          // Tiêu đề động cập nhật theo danh mục

    // ─── Table ────────────────────────────────────────────────
=======
    private Label lblMainTitle;

    // Table
>>>>>>> main
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
    private String currentCategory = "ALL";
    private Node emptyPlaceholder;

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

<<<<<<< HEAD
        // 1. Lưu lại giao diện thông báo trống từ FXML (trước khi spinner đè lên)
=======
        // 1. Lưu lại giao diện thông báo trống từ FXML
>>>>>>> main
        if (tbAuctions != null) {
            this.emptyPlaceholder = tbAuctions.getPlaceholder();
        }

<<<<<<< HEAD
        // 2. Wrap với filtered + sorted lists
=======
        // Wrap với filtered + sorted lists
>>>>>>> main
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

<<<<<<< HEAD
        // 4. Listeners cho tìm kiếm và lọc trạng thái
=======
        // Listeners cho tìm kiếm và lọc
>>>>>>> main
        if (txtSearch != null) {
            txtSearch.textProperty().addListener((obs, oldV, newV) -> applyAuctionFilter());
        }
        if (cboFilter != null) {
            cboFilter.setOnAction(e -> applyAuctionFilter());
        }

<<<<<<< HEAD
        // 5. Tải dữ liệu ban đầu
=======
        // Tải dữ liệu ban đầu
>>>>>>> main
        loadAuctions();
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
            colNo.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(tbAuctions.getItems().indexOf(data.getValue()) + 1)));
        }
        if (colName != null) {
            colName.setCellValueFactory(data ->
                new SimpleStringProperty(getJsonString(data.getValue(), "title")));
        }
        if (colSeller != null) {
            colSeller.setCellValueFactory(data -> {
                String seller = getJsonString(data.getValue(), "sellerName");
                if (seller.isEmpty()) seller = getJsonString(data.getValue(), "sellerId");
                return new SimpleStringProperty(seller);
            });
        }
        if (colPrice != null) {
            colPrice.setCellValueFactory(data -> {
                long price = data.getValue().has("currentPrice")
                    ? data.getValue().get("currentPrice").getAsLong() : 0;
                return new SimpleStringProperty(formatMoney(price));
            });
        }
        if (colEndTime != null) {
            colEndTime.setCellValueFactory(data ->
                new SimpleStringProperty(getJsonString(data.getValue(), "endTime")));
        }
        if (colBidCount != null) {
            colBidCount.setCellValueFactory(data ->
                new SimpleStringProperty(getJsonString(data.getValue(), "bidCount")));
        }
        if (colStatus != null) {
            colStatus.setCellValueFactory(data ->
                new SimpleStringProperty(getJsonString(data.getValue(), "status")));
        }

        // ─── Cột hành động: nút "Xem" mở màn hình chi tiết ───
        if (colAction != null) {
            colAction.setCellFactory(new Callback<>() {
                @Override
                public TableCell<JsonObject, Void> call(TableColumn<JsonObject, Void> param) {
                    return new TableCell<>() {
                        private final Button btn = new Button("Xem");

                        {
                            btn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-cursor: hand;");
                            btn.setOnAction(event -> {
                                JsonObject auction = getTableView().getItems().get(getIndex());
                                openAuctionDetail(auction);
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
     * Mở màn hình chi tiết phiên đấu giá và truyền auctionId vào controller.
     * Dùng FXMLLoader trực tiếp để chắc chắn lấy được controller và gọi initData(...)
     */
    private void openAuctionDetail(JsonObject auction) {
        if (auction == null) {
            AlertUtil.showWarning("Lỗi", "Dữ liệu phiên rỗng.");
            return;
        }

        String auctionId = getJsonString(auction, "auctionId");
        String title     = getJsonString(auction, "title");

        // Debug tạm: in ra console để kiểm tra
        System.out.println("openAuctionDetail -> auctionId=" + auctionId + ", title=" + title);

        if (auctionId == null || auctionId.isEmpty()) {
            AlertUtil.showWarning("Lỗi dữ liệu", "Phiên đấu giá chưa có ID hợp lệ.");
            return;
        }

        try {
            com.auction.client.controller.AuctionDetailController ctrl =
                com.auction.client.util.ViewLoader.openInNewWindow(
                    "auction-detail.fxml",
                    "Chi tiết phiên đấu giá - " + (title.isEmpty() ? auctionId : title)
                );
            if (ctrl != null) {
                ctrl.initData(auctionId);
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

<<<<<<< HEAD
            // 1. LỌC THEO DANH MỤC (Category) — logic mới thêm vào
            // Nếu currentCategory khác "ALL", kiểm tra trường "category" trong JSON
            if (!"ALL".equals(currentCategory)) {
                String category = getJsonString(auction, "category");
                // Nếu danh mục không khớp thì loại bỏ
=======
            // 1. LỌC THEO DANH MỤC (Category) - Đây là phần mới thêm vào
            // Nếu currentCategory khác "ALL", ta kiểm tra trường "category" trong JSON
            if (!"ALL".equals(currentCategory)) {
                String category = getJsonString(auction, "category");
                // Nếu danh mục của sản phẩm không khớp với danh mục đang chọn thì loại bỏ (return false)
>>>>>>> main
                if (!currentCategory.equalsIgnoreCase(category)) {
                    return false;
                }
            }

<<<<<<< HEAD
            // 2. LỌC THEO TRẠNG THÁI (code cũ giữ nguyên)
=======
            // 2. LỌC THEO TRẠNG THÁI (Code cũ của bạn)
>>>>>>> main
            if (statusFilter != null && !statusFilter.isEmpty() && !"Tất cả".equals(statusFilter)) {
                String s = getJsonString(auction, "status").toLowerCase();
                if (!s.contains(statusFilter.toLowerCase())) return false;
            }

<<<<<<< HEAD
            // 3. LỌC THEO TỪ KHÓA TÌM KIẾM (code cũ giữ nguyên)
=======
            // 3. LỌC THEO TỪ KHÓA TÌM KIẾM (Code cũ của bạn)
>>>>>>> main
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
<<<<<<< HEAD
                    "GET_AUCTIONS", params, com.google.gson.JsonElement.class
=======
                        "GET_AUCTIONS", params, com.google.gson.JsonElement.class
>>>>>>> main
                );

                Platform.runLater(() -> {
                    if (response != null) {
<<<<<<< HEAD
                        // Xử lý JSON — hỗ trợ cả dạng Array lẫn Object có key "auctions"
=======
>>>>>>> main
                        JsonArray arr = null;
                        if (response.isJsonArray()) {
                            arr = response.getAsJsonArray();
                        } else if (response.isJsonObject()) {
                            JsonObject obj = response.getAsJsonObject();
                            if (obj.has("auctions")) arr = obj.getAsJsonArray("auctions");
                        }

                        if (arr != null) {
<<<<<<< HEAD
                            // 3. ĐỔ DỮ LIỆU MỚI VÀO
=======
>>>>>>> main
                            arr.forEach(el -> auctionList.add(el.getAsJsonObject()));
                            updateAuctionCount();
                        }
                    }

<<<<<<< HEAD
                    // 4. QUAN TRỌNG: Trả lại giao diện "Trống" thẩm mỹ đã lưu từ FXML.
                    // Khi gán lại emptyPlaceholder, nếu auctionList rỗng sẽ hiện hộp 📦
                    // đã thiết kế sẵn trong FXML thay vì spinner xoay mãi hoặc Label đơn giản.
=======
                    // 3. QUAN TRỌNG NHẤT: Trả lại giao diện "Trống" thẩm mỹ đã lưu từ FXML
                    // Khi gán lại emptyPlaceholder, nếu mảng auctionList rỗng,
                    // nó sẽ hiện cái hộp 📦 thay vì xoay mãi.
>>>>>>> main
                    tbAuctions.setPlaceholder(emptyPlaceholder);

                    // Mở khóa lại nút làm mới
                    if (btnRefresh != null) btnRefresh.setDisable(false);
                });
<<<<<<< HEAD

=======
>>>>>>> main
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (btnRefresh != null) btnRefresh.setDisable(false);

<<<<<<< HEAD
                    // Nếu lỗi: hiện thông báo lỗi có style thay vì spinner
=======
                    // Nếu lỗi, hiện thông báo lỗi thay vì spinner
>>>>>>> main
                    Label errorLbl = new Label("Lỗi tải dữ liệu: " + e.getMessage());
                    errorLbl.setStyle("-fx-text-fill: #e67e22; -fx-font-style: italic;");
                    tbAuctions.setPlaceholder(errorLbl);
                });
                e.printStackTrace();
            }
        }).start();
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
<<<<<<< HEAD
=======

    // ================== Menu Category Handlers ==================
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
>>>>>>> main
}