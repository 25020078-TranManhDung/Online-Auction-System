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
import javafx.stage.Popup;
import javafx.util.Callback;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class AuctionListController {

    // ─── Header ───────────────────────────────────────────────
    @FXML private HBox      headerUserArea;
    @FXML private ImageView imgAvatar;
    @FXML private Label     lblUser;
    @FXML private Label     lblRole;
    @FXML private Button    btnLogout;
    @FXML private Button    btnTheme;

    @FXML private Label lblWalletBalance;

    // ─── Toolbar / basic filters ──────────────────────────────
    @FXML private TextField        txtSearch;
    @FXML private ComboBox<String> cboFilter;
    @FXML private Button           btnRefresh;
    @FXML private Label            lblAuctionCount;
    @FXML private Label            lblMainTitle;

    // ─── [MỚI] Advanced filter controls ───────────────────────
    @FXML private TextField        txtPriceMin;      // Giá từ
    @FXML private TextField        txtPriceMax;      // Giá đến
    @FXML private TextField        txtMinMinutes;    // Còn ít nhất N phút
    @FXML private ComboBox<String> cboHasImage;      // Tất cả / Có ảnh / Không có ảnh
    @FXML private Button           btnApplyFilter;
    @FXML private Button           btnClearFilter;
    @FXML private Label            lblFilterStatus;  // Hiển thị bộ lọc đang hoạt động

    // ─── [MỚI] Thêm cột Ảnh ──────────────────────────────────
    @FXML private TableColumn<JsonObject, String> colHasImage;

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

    /** State của bộ lọc nâng cao */
    private double  filterPriceMin    = -1;
    private double  filterPriceMax    = -1;
    private int     filterMinMinutes  = -1;  // -1 = không lọc
    private String  filterHasImage    = "Tất cả";

    private Popup profilePopup;

    // ==========================================================
    //  KHỞI TẠO
    // ==========================================================

    @FXML
    public void initialize() {
        if (btnTheme != null)
            btnTheme.setText(com.auction.client.util.ThemeManager.getInstance().getToggleIcon());

        setupTableColumns();
        loadUserInfo();

        if (tbAuctions != null)
            emptyPlaceholder = tbAuctions.getPlaceholder();

        filteredAuctions = new FilteredList<>(auctionList, p -> true);
        SortedList<JsonObject> sorted = new SortedList<>(filteredAuctions);
        sorted.comparatorProperty().bind(tbAuctions.comparatorProperty());
        tbAuctions.setItems(sorted);

        // ComboBox trạng thái
        if (cboFilter != null) {
            cboFilter.getItems().clear();
            ObservableList<String> statusList = FXCollections.observableArrayList("Tất cả");
            for (AuctionStatus s : AuctionStatus.values()) statusList.add(s.name());
            cboFilter.setItems(statusList);
            cboFilter.setValue("Tất cả");
            cboFilter.setOnAction(e -> applyAuctionFilter());
        }

        // ComboBox có ảnh
        if (cboHasImage != null) {
            cboHasImage.setItems(FXCollections.observableArrayList(
                "Tất cả", "Có ảnh", "Không có ảnh"));
            cboHasImage.setValue("Tất cả");
        }

        // Search realtime
        if (txtSearch != null)
            txtSearch.textProperty().addListener((obs, o, n) -> applyAuctionFilter());

        // Hover header profile
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

        // [MỚI] Hiển thị ảnh đại diện nhỏ (góc trái) ngay khi đăng nhập vào
        if (this.imgAvatar != null) {
            javafx.scene.image.Image img;
            if (s.getAvatarBase64() != null && !s.getAvatarBase64().isBlank()) {
                byte[] imageBytes = com.auction.client.util.ImageUtil.decodeToBytes(s.getAvatarBase64());
                img = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(imageBytes));
            } else {
                img = new javafx.scene.image.Image(getClass().getResourceAsStream("/com/auction/client/images/default_avatar.png"));
            }
            applyCenterCrop(this.imgAvatar, img);
        }
    }

    // ==========================================================
    //  PROFILE POPUP
    // ==========================================================

    @FXML
    private void handleProfileClick(MouseEvent event) {
        if (profilePopup == null) profilePopup = buildProfilePopup();
        if (profilePopup.isShowing()) { profilePopup.hide(); return; }
        Node source  = (Node) event.getSource();
        double ax = source.localToScreen(source.getBoundsInLocal()).getMinX();
        double ay = source.localToScreen(source.getBoundsInLocal()).getMaxY() + 6;
        profilePopup.show(source, ax, ay);
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

        // [MỚI] Load ảnh to từ Session thay vì luôn để mặc định
        if (session.getAvatarBase64() != null && !session.getAvatarBase64().isBlank()) {
            byte[] imageBytes = com.auction.client.util.ImageUtil.decodeToBytes(session.getAvatarBase64());
            javafx.scene.image.Image img = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(imageBytes));
            applyCenterCrop(popupAvatar, img);
        } else {
            try {
                javafx.scene.image.Image defaultImg = new javafx.scene.image.Image(getClass().getResourceAsStream("/com/auction/client/images/default_avatar.png"));
                applyCenterCrop(popupAvatar, defaultImg);
            } catch (Exception ignored) {}
        }
        popupAvatar.setClip(new Circle(32, 32, 32));

        DropShadow avatarGlow = new DropShadow();
        avatarGlow.setColor(Color.rgb(155, 89, 182, 0.70));
        avatarGlow.setRadius(14);
        popupAvatar.setEffect(avatarGlow);

        // ========================================================
        // [MỚI] TẠO HIỆU ỨNG HOVER NHẬN DIỆN CHO AVATAR
        // ========================================================
        StackPane avatarContainer = new StackPane();
        avatarContainer.setMaxSize(64, 64); // Khóa kích thước khớp với ImageView

        // 1. Tạo lớp phủ màu đen bán trong suốt
        StackPane overlay = new StackPane();
        // Bo góc 32 (một nửa của 64) để khớp tuyệt đối với hình tròn của avatar
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.6); -fx-background-radius: 32;");
        overlay.setOpacity(0); // Mặc định ẩn đi

        // 2. Tạo Icon Camera
        Label editIcon = new Label("📷");
        editIcon.setStyle("-fx-text-fill: white; -fx-font-size: 24px;");
        overlay.getChildren().add(editIcon);

        // Ghép ImageView và Overlay vào chung một Container
        avatarContainer.getChildren().addAll(popupAvatar, overlay);

        // 3. Gắn sự kiện Hover và Click
        avatarContainer.setCursor(javafx.scene.Cursor.HAND);

        // Khi di chuột vào: Hiện lớp phủ mờ + Đổi viền sáng thành màu Xanh Cyan cho nổi bật
        avatarContainer.setOnMouseEntered(e -> {
            overlay.setOpacity(1);
            popupAvatar.setEffect(new DropShadow(javafx.scene.effect.BlurType.THREE_PASS_BOX, Color.web("#00ffff"), 12, 0.3, 0, 0));
        });

        // Khi chuột rời đi: Ẩn lớp phủ + Trả lại viền Tím ban đầu
        avatarContainer.setOnMouseExited(e -> {
            overlay.setOpacity(0);
            popupAvatar.setEffect(avatarGlow);
        });

        // Khi click chuột: Gọi menu Tải ảnh
        avatarContainer.setOnMouseClicked(e -> {
            handleAvatarClick(popupAvatar);
        });
        // ========================================================

        Label lblFullName = new Label(nullSafe(session.getFullName(), session.getUsername()));
        lblFullName.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #f0e6ff; -fx-padding: 8 0 2 0;");

        Label lblAtUsername = new Label("@" + nullSafe(session.getUsername(), "—"));
        lblAtUsername.setStyle("-fx-font-size: 13px; -fx-text-fill: #9b59b6;");

        // LƯU Ý: Thêm 'avatarContainer' thay vì 'popupAvatar'
        topSection.getChildren().addAll(avatarContainer, lblFullName, lblAtUsername);

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
        Label keyLbl  = new Label(labelText + ":"); keyLbl.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #9b59b6; -fx-min-width: 70;");
        Label valLbl  = new Label(value); valLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #dcd0ff; -fx-font-weight: bold;"); valLbl.setWrapText(true);
        HBox row = new HBox(8, iconLbl, keyLbl, valLbl); row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Separator styledDivider() {
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: rgba(155,89,182,0.30); -fx-padding: 0 20;");
        VBox.setMargin(sep, new Insets(0, 20, 0, 20));
        return sep;
    }

    private String nullSafe(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    // ==========================================================
    //  CHANGE PASSWORD
    // ==========================================================

    private void handleChangePassword() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/com/auction/client/fxml/change-password.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage modalStage = new javafx.stage.Stage();
            modalStage.setTitle("Đổi mật khẩu");
            modalStage.setScene(new javafx.scene.Scene(root));
            modalStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            if (lblUser != null && lblUser.getScene() != null)
                modalStage.initOwner(lblUser.getScene().getWindow());
            modalStage.setResizable(false);
            modalStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.showError("Lỗi", "Không thể mở màn hình đổi mật khẩu: " + e.getMessage());
        }
    }

    // ==========================================================
    //  THIẾT LẬP CỘT BẢNG
    // ==========================================================

    private void setupTableColumns() {
        if (colNo != null)
            colNo.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(tbAuctions.getItems().indexOf(data.getValue()) + 1)));

        if (colName != null)
            colName.setCellValueFactory(data ->
                new SimpleStringProperty(getJsonString(data.getValue(), "title")));

        if (colSeller != null)
            colSeller.setCellValueFactory(data ->
                new SimpleStringProperty(getJsonString(data.getValue(), "sellerId")));

        if (colPrice != null)
            colPrice.setCellValueFactory(data -> {
                long price = data.getValue().has("currentPrice")
                    ? data.getValue().get("currentPrice").getAsLong() : 0;
                return new SimpleStringProperty(formatMoney(price));
            });

        if (colEndTime != null)
            colEndTime.setCellValueFactory(data ->
                new SimpleStringProperty(getJsonString(data.getValue(), "endTime")));

        if (colBidCount != null)
            colBidCount.setCellValueFactory(data ->
                new SimpleStringProperty(getJsonString(data.getValue(), "bidCount")));

        // [MỚI] Cột Ảnh: hiện 📷 nếu có, — nếu không
        if (colHasImage != null) {
            colHasImage.setCellValueFactory(data ->
                new SimpleStringProperty(hasImage(data.getValue()) ? "📷" : "—"));
            colHasImage.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) { setText(null); return; }
                    setText(item);
                    setStyle("📷".equals(item)
                        ? "-fx-text-fill: #27ae60; -fx-font-size: 16px;"
                        : "-fx-text-fill: #bdc3c7;");
                }
            });
        }

        if (colStatus != null) {
            colStatus.setCellValueFactory(data ->
                new SimpleStringProperty(getJsonString(data.getValue(), "status")));
            colStatus.setCellFactory(col -> new TableCell<>() {
                @Override protected void updateItem(String status, boolean empty) {
                    super.updateItem(status, empty);
                    if (empty || status == null) { setText(null); setStyle(""); return; }
                    setText(status);
                    String color = switch (status) {
                        case "OPEN"     -> "#3498db";
                        case "RUNNING"  -> "#27ae60";
                        case "FINISHED" -> "#e67e22";
                        case "PAID"     -> "#8e44ad";
                        case "CANCELED" -> "#95a5a6";
                        default         -> "#555555";
                    };
                    setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
                }
            });
        }

        if (colAction != null) {
            colAction.setCellFactory(new Callback<>() {
                @Override
                public TableCell<JsonObject, Void> call(TableColumn<JsonObject, Void> param) {
                    return new TableCell<>() {
                        private final Button btn = new Button("Xem");
                        {
                            btn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white;" +
                                "-fx-font-size: 12px; -fx-padding: 5 14;" +
                                "-fx-background-radius: 8; -fx-cursor: hand;");
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
    //  MỞ CHI TIẾT PHIÊN
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
            ViewLoader.ViewResult<AuctionDetailController> result =
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
    //  [MỚI] BỘ LỌC NÂNG CAO — handlers
    // ==========================================================

    /**
     * Đọc các trường lọc nâng cao, validate rồi áp dụng.
     */
    @FXML
    private void handleApplyAdvancedFilter(ActionEvent event) {
        // Parse giá từ
        filterPriceMin = -1;
        if (txtPriceMin != null && !txtPriceMin.getText().trim().isEmpty()) {
            try {
                filterPriceMin = Double.parseDouble(txtPriceMin.getText().trim().replace(",", ""));
                if (filterPriceMin < 0) { AlertUtil.showError("Lỗi", "Giá từ không được âm."); return; }
            } catch (NumberFormatException ex) {
                AlertUtil.showError("Lỗi định dạng", "Giá tối thiểu không hợp lệ. Chỉ nhập số.");
                return;
            }
        }

        // Parse giá đến
        filterPriceMax = -1;
        if (txtPriceMax != null && !txtPriceMax.getText().trim().isEmpty()) {
            try {
                filterPriceMax = Double.parseDouble(txtPriceMax.getText().trim().replace(",", ""));
                if (filterPriceMax < 0) { AlertUtil.showError("Lỗi", "Giá đến không được âm."); return; }
            } catch (NumberFormatException ex) {
                AlertUtil.showError("Lỗi định dạng", "Giá tối đa không hợp lệ. Chỉ nhập số.");
                return;
            }
        }

        // Validate khoảng giá
        if (filterPriceMin >= 0 && filterPriceMax >= 0 && filterPriceMin > filterPriceMax) {
            AlertUtil.showError("Lỗi", "Giá từ không được lớn hơn giá đến.");
            return;
        }

        // Parse thời gian còn lại
        filterMinMinutes = -1;
        if (txtMinMinutes != null && !txtMinMinutes.getText().trim().isEmpty()) {
            try {
                filterMinMinutes = Integer.parseInt(txtMinMinutes.getText().trim());
                if (filterMinMinutes < 0) { AlertUtil.showError("Lỗi", "Số phút không được âm."); return; }
            } catch (NumberFormatException ex) {
                AlertUtil.showError("Lỗi định dạng", "Số phút không hợp lệ. Chỉ nhập số nguyên.");
                return;
            }
        }

        // Có ảnh
        filterHasImage = (cboHasImage != null && cboHasImage.getValue() != null)
            ? cboHasImage.getValue() : "Tất cả";

        applyAuctionFilter();
        updateFilterStatusLabel();
    }

    /**
     * Xóa toàn bộ bộ lọc nâng cao, reset về mặc định.
     */
    @FXML
    private void handleClearAdvancedFilter(ActionEvent event) {
        filterPriceMin   = -1;
        filterPriceMax   = -1;
        filterMinMinutes = -1;
        filterHasImage   = "Tất cả";

        if (txtPriceMin   != null) txtPriceMin.clear();
        if (txtPriceMax   != null) txtPriceMax.clear();
        if (txtMinMinutes != null) txtMinMinutes.clear();
        if (cboHasImage   != null) cboHasImage.setValue("Tất cả");
        if (lblFilterStatus != null) { lblFilterStatus.setText(""); lblFilterStatus.setVisible(false); }

        applyAuctionFilter();
    }

    /** Cập nhật label thông báo bộ lọc đang hoạt động. */
    private void updateFilterStatusLabel() {
        if (lblFilterStatus == null) return;
        StringBuilder sb = new StringBuilder();
        if (filterPriceMin >= 0 || filterPriceMax >= 0) {
            sb.append("💰 Giá: ");
            if (filterPriceMin >= 0) sb.append(formatMoney((long) filterPriceMin));
            sb.append(" → ");
            if (filterPriceMax >= 0) sb.append(formatMoney((long) filterPriceMax));
            else sb.append("∞");
            sb.append("\n");
        }
        if (filterMinMinutes >= 0)
            sb.append("⏱ Còn ≥ ").append(filterMinMinutes).append(" phút\n");
        if (!"Tất cả".equals(filterHasImage))
            sb.append("📷 ").append(filterHasImage).append("\n");

        String text = sb.toString().trim();
        lblFilterStatus.setText(text.isEmpty() ? "" : "Đang lọc:\n" + text);
        lblFilterStatus.setVisible(!text.isEmpty());
    }

    // ==========================================================
    //  LỌC DỮ LIỆU — predicate tổng hợp
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

            // ── 1. Lọc danh mục (sidebar) ──────────────────
            if (!"ALL".equals(currentCategory)) {
                String category = getJsonString(auction, "category");
                if (!currentCategory.equalsIgnoreCase(category)) return false;
            }

            // ── 2. Lọc trạng thái (ComboBox toolbar) ───────
            if (statusFilter != null && !statusFilter.isEmpty() && !"Tất cả".equals(statusFilter)) {
                String s = getJsonString(auction, "status").toLowerCase();
                if (!s.contains(statusFilter.toLowerCase())) return false;
            }

            // ── 3. Tìm kiếm text ────────────────────────────
            if (q != null && !q.isEmpty()) {
                String title  = getJsonString(auction, "title").toLowerCase();
                String seller = getJsonString(auction, "sellerId").toLowerCase();
                if (!title.contains(q) && !seller.contains(q)) return false;
            }

            // ── 4. [MỚI] Lọc khoảng giá ─────────────────────
            double price = auction.has("currentPrice")
                ? auction.get("currentPrice").getAsDouble() : 0;
            if (filterPriceMin >= 0 && price < filterPriceMin) return false;
            if (filterPriceMax >= 0 && price > filterPriceMax) return false;

            // ── 5. [MỚI] Lọc thời gian còn lại ─────────────
            if (filterMinMinutes >= 0) {
                // timeRemaining từ server trả về đơn vị giây
                long timeRemainingSeconds = auction.has("timeRemaining")
                    ? auction.get("timeRemaining").getAsLong() : 0;
                long minutesLeft = timeRemainingSeconds / 60;
                if (minutesLeft < filterMinMinutes) return false;
            }

            // ── 6. [MỚI] Lọc có ảnh ─────────────────────────
            if (!"Tất cả".equals(filterHasImage)) {
                boolean imgExists = hasImage(auction);
                if ("Có ảnh".equals(filterHasImage) && !imgExists) return false;
                if ("Không có ảnh".equals(filterHasImage) && imgExists) return false;
            }

            return true;
        };
    }

    private void updateAuctionCount() {
        if (lblAuctionCount != null) {
            int shown = filteredAuctions == null ? auctionList.size() : filteredAuctions.size();
            int total = auctionList.size();
            String text = (shown == total)
                ? String.format("%d phiên", total)
                : String.format("%d / %d phiên (đã lọc)", shown, total);
            lblAuctionCount.setText(text);
        }
    }

    // ==========================================================
    //  HANDLERS — MENU DANH MỤC SIDEBAR
    // ==========================================================

    @FXML public void showAllItems()    { currentCategory = "ALL";         setTitle("Danh sách phiên đấu giá");             applyAuctionFilter(); }
    @FXML public void showArts()        { currentCategory = "Art";         setTitle("Danh sách: Nghệ thuật (Art)");          applyAuctionFilter(); }
    @FXML public void showElectronics() { currentCategory = "Electronics"; setTitle("Danh sách: Đồ điện tử (Electronics)"); applyAuctionFilter(); }
    @FXML public void showVehicles()    { currentCategory = "Vehicle";     setTitle("Danh sách: Phương tiện (Vehicle)");     applyAuctionFilter(); }
    @FXML public void showOthers()      { currentCategory = "Other";       setTitle("Danh sách: Tài sản khác");              applyAuctionFilter(); }

    private void setTitle(String title) {
        if (lblMainTitle != null) lblMainTitle.setText(title);
    }

    // ==========================================================
    //  HANDLERS — TOOLBAR
    // ==========================================================

    @FXML private void handleSearch(KeyEvent event)    { applyAuctionFilter(); }
    @FXML private void handleFilter(ActionEvent event) { applyAuctionFilter(); }

    @FXML
    private void handleRefresh(ActionEvent event) {
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
                            applyAuctionFilter();
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

    /**
     * Kiểm tra xem phiên có ảnh sản phẩm không (dựa vào prefix [IMGS:] hoặc [IMG:] trong description).
     */
    private boolean hasImage(JsonObject auction) {
        if (auction == null) return false;
        String desc = getJsonString(auction, "description");
        return desc.startsWith("[IMGS:") || desc.startsWith("[IMG:");
    }

    private String getJsonString(JsonObject obj, String key) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull())
            ? obj.get(key).getAsString() : "";
    }

    private String formatMoney(long amount) {
        return String.format("%,d VNĐ", amount);
    }

    @FXML
    private void handleToggleTheme(ActionEvent event) {
        com.auction.client.util.ThemeManager tm = com.auction.client.util.ThemeManager.getInstance();
        tm.toggle();
        if (btnTheme != null) btnTheme.setText(tm.getToggleIcon());
    }

    // ========================================================
    // 1. HÀM GỌI MENU
    // ========================================================
    private void handleAvatarClick(ImageView popupAvatar) {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();

        javafx.scene.control.MenuItem uploadItem = new javafx.scene.control.MenuItem("📷  Tải ảnh lên...");
        uploadItem.setOnAction(e -> {
            // [ĐÃ SỬA BỆNH MINIMIZE]: Truyền null vào đây thay vì lấy Window của Popup
            // Điều này tách FileChooser ra khỏi Popup, giúp App không bị văng xuống Taskbar nữa.
            String base64 = com.auction.client.util.ImageUtil.pickAndEncodeAvatar(null);
            if (base64 == null) return;

            // Truyền luôn cái khung ảnh (popupAvatar) vào để tí nữa có cái mà cập nhật
            sendAvatarUpdate(base64, popupAvatar);
        });

        javafx.scene.control.MenuItem removeItem = new javafx.scene.control.MenuItem("🗑  Xóa ảnh");
        removeItem.setOnAction(e -> sendAvatarUpdate(null, popupAvatar)); // null = xóa ảnh

        menu.getItems().addAll(uploadItem, removeItem);
        menu.show(popupAvatar, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    // ========================================================
    // 2. HÀM GỬI LÊN SERVER VÀ CẬP NHẬT GIAO DIỆN
    // ========================================================
    private void sendAvatarUpdate(String base64, javafx.scene.image.ImageView popupAvatar) {
        com.auction.client.model.UserSession session = com.auction.client.model.UserSession.getInstance();
        if (session.getUserId() == null) return;

        com.auction.shared.dto.request.UpdateAvatarRequest req =
                new com.auction.shared.dto.request.UpdateAvatarRequest(session.getUserId(), base64);

        new Thread(() -> {
            try {
                String serverMsg = com.auction.client.network.SocketClient.getInstance()
                        .send(com.auction.shared.network.protocol.Actions.UPDATE_AVATAR, req, String.class);

                javafx.application.Platform.runLater(() -> {
                    session.setAvatarBase64(base64);

                    // 1. Tạo đối tượng Image mới từ Base64
                    javafx.scene.image.Image newImg;
                    if (base64 == null) {
                        newImg = new javafx.scene.image.Image(getClass().getResourceAsStream("/com/auction/client/images/default_avatar.png"));
                    } else {
                        byte[] imageBytes = com.auction.client.util.ImageUtil.decodeToBytes(base64);
                        newImg = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(imageBytes));
                    }

                    // 2. Cập nhật ảnh to trong Popup (Áp dụng Center Crop chống méo)
                    applyCenterCrop(popupAvatar, newImg);

                    // 3. Cập nhật ảnh nhỏ bên ngoài
                    if (this.imgAvatar != null) {
                        applyCenterCrop(this.imgAvatar, newImg);
                    }

                    // 🌟 LỰA CHỌN 1: Đã tắt thông báo thành công (Silent UX)
                    // com.auction.client.util.AlertUtil.showInfo("Thành công", serverMsg);
                });

            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    // Tuy ẩn thông báo thành công, nhưng lỗi thì vẫn nên giữ để người dùng biết nếu mất mạng nhé!
                    com.auction.client.util.AlertUtil.showError("Lỗi", "Không thể cập nhật ảnh: " + ex.getMessage());
                });
            }
        }).start();
    }

    /**
     * Hàm tự động cắt ảnh (Center Crop) thành hình vuông chuẩn trước khi hiển thị.
     * Hoạt động giống hệt thuộc tính 'object-fit: cover' trong CSS.
     */
    private void applyCenterCrop(javafx.scene.image.ImageView imageView, javafx.scene.image.Image img) {
        imageView.setImage(img);
        double w = img.getWidth();
        double h = img.getHeight();
        if (w > 0 && h > 0) {
            // Lấy kích thước cạnh nhỏ nhất để tạo hình vuông
            double size = Math.min(w, h);
            // Tính tọa độ cắt để lấy đúng tâm bức ảnh
            double x = (w - size) / 2.0;
            double y = (h - size) / 2.0;
            imageView.setViewport(new javafx.geometry.Rectangle2D(x, y, size, size));
        }
    }
}