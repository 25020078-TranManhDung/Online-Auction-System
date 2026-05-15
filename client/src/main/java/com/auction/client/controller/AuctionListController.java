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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Popup;
import javafx.util.Callback;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class AuctionListController {

    // ─── Header ───────────────────────────────────────────────
    @FXML private HBox      headerUserArea;   // clickable profile zone
    @FXML private ImageView imgAvatar;
    @FXML private Label     lblUser;
    @FXML private Label     lblRole;
    @FXML private Button    btnLogout;
    @FXML private Label     lblWalletBalance;

    // ─── Toolbar / filters ────────────────────────────────────
    @FXML private TextField         txtSearch;
    @FXML private ComboBox<String>  cboFilter;
    @FXML private Button            btnRefresh;
    @FXML private Label             lblAuctionCount;
    @FXML private Label             lblMainTitle;

    // ─── Table ────────────────────────────────────────────────
    @FXML private TableView<JsonObject>              tbAuctions;
    @FXML private TableColumn<JsonObject, String>    colNo;
    @FXML private TableColumn<JsonObject, String>    colName;
    @FXML private TableColumn<JsonObject, String>    colSeller;
    @FXML private TableColumn<JsonObject, String>    colPrice;
    @FXML private TableColumn<JsonObject, String>    colEndTime;
    @FXML private TableColumn<JsonObject, String>    colBidCount;
    @FXML private TableColumn<JsonObject, String>    colStatus;
    @FXML private TableColumn<JsonObject, Void>      colAction;

    @FXML private Label lblMessage;

    // ─── Data & state ─────────────────────────────────────────
    private final ObservableList<JsonObject> auctionList = FXCollections.observableArrayList();
    private FilteredList<JsonObject> filteredAuctions;
    private Node     emptyPlaceholder;
    private String   currentCategory = "ALL";

    /** The single shared profile Popup (lazy-built on first click). */
    private Popup profilePopup;

    // ==========================================================
    //  KHỞI TẠO
    // ==========================================================

    @FXML
    public void initialize() {
        setupTableColumns();
        loadUserInfo();

        if (tbAuctions != null) {
            this.emptyPlaceholder = tbAuctions.getPlaceholder();
        }

        filteredAuctions = new FilteredList<>(auctionList, p -> true);
        SortedList<JsonObject> sorted = new SortedList<>(filteredAuctions);
        sorted.comparatorProperty().bind(tbAuctions.comparatorProperty());
        tbAuctions.setItems(sorted);

        if (cboFilter != null) {
            cboFilter.getItems().clear();
            ObservableList<String> statusList = FXCollections.observableArrayList("Tất cả");
            for (AuctionStatus status : AuctionStatus.values()) {
                statusList.add(status.name());
            }
            cboFilter.setItems(statusList);
            cboFilter.setValue("Tất cả");
        }

        if (txtSearch != null) {
            txtSearch.textProperty().addListener((obs, o, n) -> applyAuctionFilter());
        }
        if (cboFilter != null) {
            cboFilter.setOnAction(e -> applyAuctionFilter());
        }

        // Hover highlight on the profile click zone
        if (headerUserArea != null) {
            headerUserArea.setOnMouseEntered(e ->
                    headerUserArea.setStyle(
                            "-fx-cursor: hand; -fx-padding: 6 12 6 12; " +
                                    "-fx-background-radius: 14; " +
                                    "-fx-background-color: rgba(155,89,182,0.12);"));
            headerUserArea.setOnMouseExited(e ->
                    headerUserArea.setStyle(
                            "-fx-cursor: hand; -fx-padding: 6 12 6 12; " +
                                    "-fx-background-radius: 14; " +
                                    "-fx-background-color: transparent;"));
        }

        loadAuctions();
        loadWalletBalance();
    }

    // ==========================================================
    //  THÔNG TIN NGƯỜI DÙNG
    // ==========================================================

    private void loadUserInfo() {
        UserSession s = UserSession.getInstance();
        if (lblUser != null) {
            String name = s.getUsername();
            lblUser.setText(name != null && !name.isEmpty() ? name : "Người dùng");
        }
        if (lblRole != null) {
            String role = s.getRole();
            lblRole.setText(role != null && !role.isEmpty() ? role : "");
        }
    }

    // ==========================================================
    //  PROFILE POPUP  (built programmatically)
    // ==========================================================

    /**
     * Fired by onMouseClicked="#handleProfileClick" in the FXML header zone.
     * Toggles the glassmorphism profile dropdown below the clicked node.
     */
    @FXML
    private void handleProfileClick(MouseEvent event) {
        if (profilePopup == null) {
            profilePopup = buildProfilePopup();
        }

        if (profilePopup.isShowing()) {
            profilePopup.hide();
            return;
        }

        // Position it just below the header click zone
        Node source  = (Node) event.getSource();
        double anchorX = source.localToScreen(source.getBoundsInLocal()).getMinX();
        double anchorY = source.localToScreen(source.getBoundsInLocal()).getMaxY() + 6;
        profilePopup.show(source, anchorX, anchorY);
    }

    /**
     * Constructs the profile Popup once and reuses it.
     *
     * Visual anatomy (matches glassmorphism dark-purple theme):
     * ┌─────────────────────────────────────────┐
     * │  [▲ top arrow notch]                    │
     * │  ┌─────────────────────────────────────┐│
     * │  │  [Avatar 64px]                      ││
     * │  │  Full Name  (bold, large)            ││
     * │  │  @username                           ││
     * │  │  ─────────────────────── (divider)   ││
     * │  │  👤 User ID  :  value               ││
     * │  │  ✉  Email    :  value               ││
     * │  │  🏷 Role     :  value               ││
     * │  │  ─────────────────────── (divider)   ││
     * │  │  [ 🔐 Đổi mật khẩu ]               ││
     * │  └─────────────────────────────────────┘│
     * └─────────────────────────────────────────┘
     */
    private Popup buildProfilePopup() {
        UserSession session = UserSession.getInstance();

        Popup popup = new Popup();
        popup.setAutoHide(true);   // closes when clicking elsewhere
        popup.setAutoFix(true);

        /* ── Outer container (gives drop-shadow room) ── */
        StackPane wrapper = new StackPane();
        wrapper.setPadding(new Insets(8, 8, 8, 8));

        /* ── Card ── */
        VBox card = new VBox(0);
        card.setPrefWidth(300);
        card.setStyle(
                "-fx-background-color: rgba(35, 10, 60, 0.93);" +
                        "-fx-background-radius: 18;" +
                        "-fx-border-color: rgba(155, 89, 182, 0.40);" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 18;"
        );

        // Drop-shadow
        DropShadow ds = new DropShadow();
        ds.setColor(Color.rgb(67, 20, 118, 0.55));
        ds.setRadius(28);
        ds.setOffsetY(8);
        card.setEffect(ds);

        /* ── TOP SECTION: Avatar + name + username ── */
        VBox topSection = new VBox(6);
        topSection.setAlignment(Pos.CENTER);
        topSection.setPadding(new Insets(24, 20, 18, 20));
        topSection.setStyle("-fx-background-radius: 18 18 0 0; -fx-background-color: transparent;");

        // Avatar with circular clip
        ImageView popupAvatar = new ImageView();
        popupAvatar.setFitWidth(64);
        popupAvatar.setFitHeight(64);
        popupAvatar.setPreserveRatio(true);
        try {
            Image avatarImg = new Image(
                    getClass().getResourceAsStream("/com/auction/client/images/default_avatar.png"),
                    64, 64, true, true);
            popupAvatar.setImage(avatarImg);
        } catch (Exception ignored) {}

        Circle clip = new Circle(32, 32, 32);
        popupAvatar.setClip(clip);

        // Glow ring around avatar
        DropShadow avatarGlow = new DropShadow();
        avatarGlow.setColor(Color.rgb(155, 89, 182, 0.70));
        avatarGlow.setRadius(14);
        popupAvatar.setEffect(avatarGlow);

        // Full name
        Label lblFullName = new Label(nullSafe(session.getFullName(), session.getUsername()));
        lblFullName.setStyle(
                "-fx-font-size: 17px; -fx-font-weight: bold; " +
                        "-fx-text-fill: #f0e6ff; -fx-padding: 8 0 2 0;");

        // @username
        Label lblAtUsername = new Label("@" + nullSafe(session.getUsername(), "—"));
        lblAtUsername.setStyle("-fx-font-size: 13px; -fx-text-fill: #9b59b6;");

        topSection.getChildren().addAll(popupAvatar, lblFullName, lblAtUsername);

        /* ── DIVIDER ── */
        Separator div1 = styledDivider();

        /* ── INFO SECTION ── */
        VBox infoSection = new VBox(10);
        infoSection.setPadding(new Insets(14, 24, 14, 24));

        infoSection.getChildren().addAll(
                infoRow("👤", "User ID",  nullSafe(session.getUserId(), "—")),
                infoRow("✉",  "Email",    nullSafe(session.getEmail(),  "Chưa cập nhật")),
                infoRow("🏷", "Vai trò",  nullSafe(session.getRole(),   "—"))
        );

        /* ── DIVIDER ── */
        Separator div2 = styledDivider();

        /* ── ACTION SECTION: Change Password button ── */
        VBox actionSection = new VBox(10);
        actionSection.setAlignment(Pos.CENTER);
        actionSection.setPadding(new Insets(14, 20, 20, 20));

        Button btnChangePassword = new Button("🔐  Đổi mật khẩu");
        btnChangePassword.setMaxWidth(Double.MAX_VALUE);
        btnChangePassword.setStyle(
                "-fx-background-color: linear-gradient(to right, #6c3483, #8e44ad);" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 13.5px;" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-radius: 10;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 10 20;"
        );
        btnChangePassword.setOnMouseEntered(e -> btnChangePassword.setStyle(
                "-fx-background-color: linear-gradient(to right, #7d3c98, #9b59b6);" +
                        "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13.5px;" +
                        "-fx-background-radius: 10; -fx-border-radius: 10;" +
                        "-fx-cursor: hand; -fx-padding: 10 20;" +
                        "-fx-effect: dropshadow(gaussian, rgba(142,68,173,0.55), 10, 0, 0, 2);"
        ));
        btnChangePassword.setOnMouseExited(e -> btnChangePassword.setStyle(
                "-fx-background-color: linear-gradient(to right, #6c3483, #8e44ad);" +
                        "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13.5px;" +
                        "-fx-background-radius: 10; -fx-border-radius: 10;" +
                        "-fx-cursor: hand; -fx-padding: 10 20;"
        ));
        btnChangePassword.setOnAction(e -> {
            popup.hide();
            handleChangePassword();
        });

        actionSection.getChildren().add(btnChangePassword);

        /* ── Assemble card ── */
        card.getChildren().addAll(topSection, div1, infoSection, div2, actionSection);
        wrapper.getChildren().add(card);
        popup.getContent().add(wrapper);

        return popup;
    }

    /** Single info row: emoji icon | label | value */
    private HBox infoRow(String icon, String labelText, String value) {
        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 14px;");
        iconLbl.setMinWidth(22);

        Label keyLbl = new Label(labelText + ":");
        keyLbl.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #9b59b6; -fx-min-width: 70;");

        Label valLbl = new Label(value);
        valLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #dcd0ff; -fx-font-weight: bold;");
        valLbl.setWrapText(true);

        HBox row = new HBox(8, iconLbl, keyLbl, valLbl);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Thin purple-tinted separator for the popup card. */
    private Separator styledDivider() {
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: rgba(155,89,182,0.30); -fx-padding: 0 20;");
        VBox.setMargin(sep, new Insets(0, 20, 0, 20));
        return sep;
    }

    /** Null-safe string helper. */
    private String nullSafe(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    // ==========================================================
    //  CHANGE PASSWORD
    // ==========================================================

    /**
     * Called when the "Đổi mật khẩu" button inside the popup is pressed.
     * Loads change-password.fxml in a new window via ViewLoader.
     * Adjust the fxml filename / title to match your project.
     */
    private void handleChangePassword() {
        try {
            ViewLoader.openInNewWindow("change-password.fxml", "Đổi mật khẩu");
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.showError("Lỗi", "Không thể mở màn hình đổi mật khẩu: " + e.getMessage());
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
            colSeller.setCellValueFactory(data ->
                    new SimpleStringProperty(getJsonString(data.getValue(), "sellerId")));
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

    private void openAuctionDetail(JsonObject auction, ActionEvent event) {
        if (auction == null) { AlertUtil.showWarning("Lỗi", "Dữ liệu phiên rỗng."); return; }

        String auctionId = getJsonString(auction, "auctionId");
        String title     = getJsonString(auction, "title");

        if (auctionId == null || auctionId.isEmpty()) {
            AlertUtil.showWarning("Lỗi dữ liệu", "Phiên đấu giá chưa có ID hợp lệ.");
            return;
        }

        try {
            ViewLoader.ViewResult<com.auction.client.controller.AuctionDetailController> result =
                    ViewLoader.loadViewWithController("auction-detail.fxml");
            if (result != null) {
                result.getController().initData(auctionId);
                javafx.stage.Stage stage =
                        (javafx.stage.Stage) ((Node) event.getSource()).getScene().getWindow();
                stage.getScene().setRoot(result.getView());
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

            if (!"ALL".equals(currentCategory)) {
                String category = getJsonString(auction, "category");
                if (!currentCategory.equalsIgnoreCase(category)) return false;
            }

            if (statusFilter != null && !statusFilter.isEmpty() && !"Tất cả".equals(statusFilter)) {
                String s = getJsonString(auction, "status").toLowerCase();
                if (!s.contains(statusFilter.toLowerCase())) return false;
            }

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
    //  HANDLERS — MENU DANH MỤC SIDEBAR
    // ==========================================================

    @FXML public void showAllItems()    { currentCategory = "ALL";         if (lblMainTitle != null) lblMainTitle.setText("Danh sách phiên đấu giá"); applyAuctionFilter(); }
    @FXML public void showArts()        { currentCategory = "Art";         if (lblMainTitle != null) lblMainTitle.setText("Danh sách: Nghệ thuật (Art)"); applyAuctionFilter(); }
    @FXML public void showElectronics() { currentCategory = "Electronics"; if (lblMainTitle != null) lblMainTitle.setText("Danh sách: Đồ điện tử (Electronics)"); applyAuctionFilter(); }
    @FXML public void showVehicles()    { currentCategory = "Vehicle";     if (lblMainTitle != null) lblMainTitle.setText("Danh sách: Phương tiện (Vehicle)"); applyAuctionFilter(); }
    @FXML public void showOthers()      { currentCategory = "Other";       if (lblMainTitle != null) lblMainTitle.setText("Danh sách: Tài sản khác"); applyAuctionFilter(); }

    // ==========================================================
    //  HANDLERS — TOOLBAR
    // ==========================================================

    @FXML private void handleSearch(KeyEvent event)    { applyAuctionFilter(); }
    @FXML private void handleFilter(ActionEvent event) { applyAuctionFilter(); }

    @FXML
    private void handleRefresh(ActionEvent event) {
        System.out.println("[Client] Refreshing auction data...");
        loadAuctions();
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            UserSession.getInstance().cleanUserSession();
            ViewLoader.load(event, "login.fxml", "Đăng nhập hệ thống");
        } catch (Exception e) {
            AlertUtil.showError("Lỗi", "Không thể quay lại màn hình đăng nhập.");
        }
    }

    // ==========================================================
    //  TẢI DỮ LIỆU TỪ SERVER
    // ==========================================================

    private void loadAuctions() {
        if (lblMessage != null) lblMessage.setVisible(false);
        auctionList.clear();
        if (btnRefresh != null) btnRefresh.setDisable(true);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(40, 40);
        tbAuctions.setPlaceholder(spinner);

        new Thread(() -> {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("status", "ALL");

                com.google.gson.JsonElement response = SocketClient.getInstance().send(
                        "GET_AUCTIONS", params, com.google.gson.JsonElement.class);

                Platform.runLater(() -> {
                    if (response != null) {
                        JsonArray arr = null;
                        if (response.isJsonArray()) {
                            arr = response.getAsJsonArray();
                        } else if (response.isJsonObject()) {
                            JsonObject obj = response.getAsJsonObject();
                            if (obj.has("auctions")) arr = obj.getAsJsonArray("auctions");
                        }
                        if (arr != null) {
                            arr.forEach(el -> auctionList.add(el.getAsJsonObject()));
                            updateAuctionCount();
                        }
                    }
                    tbAuctions.setPlaceholder(emptyPlaceholder);
                    if (btnRefresh != null) btnRefresh.setDisable(false);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (btnRefresh != null) btnRefresh.setDisable(false);
                    Label err = new Label("Lỗi tải dữ liệu: " + e.getMessage());
                    err.setStyle("-fx-text-fill: #e67e22; -fx-font-style: italic;");
                    tbAuctions.setPlaceholder(err);
                });
                e.printStackTrace();
            }
        }).start();
    }

    // ==========================================================
    //  VÍ ĐIỆN TỬ
    // ==========================================================

    private void loadWalletBalance() {
        new Thread(() -> {
            try {
                Map<String, Object> params = new HashMap<>();
                JsonObject data = SocketClient.getInstance().send("GET_WALLET", params, JsonObject.class);
                if (data != null) {
                    long balance = data.has("availableBalance") ? data.get("availableBalance").getAsLong() : 0;
                    Platform.runLater(() -> {
                        if (lblWalletBalance != null) lblWalletBalance.setText(formatMoney(balance));
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
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/com/auction/client/fxml/bidder-wallet.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage stage =
                    (javafx.stage.Stage) ((Node) event.getSource()).getScene().getWindow();
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