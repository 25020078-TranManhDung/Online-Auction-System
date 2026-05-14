package com.auction.client.controller;

import com.auction.client.model.UserSession;
import com.auction.client.network.SocketClient;
import com.auction.client.network.MessageHandler;
import com.auction.client.observer.BidUpdateListener;
import com.auction.client.observer.AuctionUpdateListener;
import com.auction.client.util.AlertUtil;
import com.auction.client.util.ViewLoader;
import com.auction.shared.dto.response.AuctionResponse;
import com.auction.shared.enums.AuctionStatus;
import com.auction.shared.enums.ItemCategory;
import com.auction.shared.network.protocol.Actions;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.scene.control.DatePicker;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.geometry.Pos;
import java.util.ArrayList;
import java.util.Base64;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import javafx.scene.control.ButtonType;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import java.lang.reflect.Field;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SellerDashboardController implements BidUpdateListener, AuctionUpdateListener {

    // ===== Form đăng sản phẩm (cột trái) =====
    @FXML private TextField txtProductName;
    @FXML private TextArea  txtDescription;
    @FXML private TextField txtStartPrice;
    @FXML private TextField txtMinIncrement;
    @FXML private DatePicker dpStartDate;   // [GIỮ LẠI] Ngày bắt đầu (lên lịch)
    @FXML private TextField txtStartTime;   // [GIỮ LẠI] Giờ bắt đầu (HH:mm)
    @FXML private TextField txtDuration;
    @FXML private ComboBox<String> cboCategory;
    @FXML private Button    btnAddItem;
    @FXML private Label     lblFormMessage;

    // ===== Ảnh sản phẩm - nhiều ảnh =====
    @FXML private HBox  hboxImageRow;
    @FXML private Label lblImageName;
    private final List<String> selectedImagesBase64 = new ArrayList<>();

    // ===== Header =====
    @FXML private Label  lblUser;
    @FXML private Label  lblRole;
    @FXML private Button btnLogout;

    // ===== Thống kê nhanh =====
    @FXML private Label lblTotalItems;
    @FXML private Label lblActiveItems;
    @FXML private Label lblClosedItems;
    @FXML private Label lblTotalRevenue;

    // ===== Danh sách sản phẩm (cột phải) — [GIỮ LẠI] đầy đủ cột =====
    @FXML private TableView<AuctionResponse>              tvSellerItems;
    @FXML private TableColumn<AuctionResponse, Integer>  colNo;
    @FXML private TableColumn<AuctionResponse, String>   colName;
    @FXML private TableColumn<AuctionResponse, Double>   colStart;
    @FXML private TableColumn<AuctionResponse, Double>   colPrice;
    @FXML private TableColumn<AuctionResponse, Integer>  colBidCount;
    @FXML private TableColumn<AuctionResponse, AuctionStatus> colStatus;
    @FXML private TableColumn<AuctionResponse, String>   colEndTime;
    @FXML private TableColumn<AuctionResponse, Void>     colAction;

    @FXML private ComboBox<String> cboStatusFilter;
    @FXML private Button btnRefresh;
    @FXML private Label  lblItemCount;
    @FXML private Label  lblTableMessage;

    // Data
    private final ObservableList<AuctionResponse> sellerAuctions = FXCollections.observableArrayList();
    private FilteredList<AuctionResponse> filteredSellerAuctions;

    public static class GetAuctionsResponse {
        public List<AuctionResponse> auctions;
        public int total;
    }

    @FXML
    public void initialize() {
        if (lblUser != null) {
            String username = UserSession.getInstance().getUsername();
            lblUser.setText(username != null ? username : "Người bán");
        }

        DateTimeFormatter dtFmt = DateTimeFormatter.ofPattern("dd/MM HH:mm");

        // Cột số thứ tự động
        if (colNo != null) {
            colNo.setCellFactory(col -> new TableCell<>() {
                @Override protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : String.valueOf(getIndex() + 1));
                }
            });
        }

        if (colName != null)
            colName.setCellValueFactory(new PropertyValueFactory<>("title"));

        if (colStart != null) {
            colStart.setCellValueFactory(new PropertyValueFactory<>("startingPrice"));
            colStart.setCellFactory(col -> new TableCell<>() {
                @Override protected void updateItem(Double v, boolean empty) {
                    super.updateItem(v, empty);
                    setText(empty || v == null ? null : String.format("%,.0f", v));
                }
            });
        }

        if (colPrice != null) {
            colPrice.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
            colPrice.setCellFactory(col -> new TableCell<>() {
                @Override protected void updateItem(Double v, boolean empty) {
                    super.updateItem(v, empty);
                    setText(empty || v == null ? null : String.format("%,.0f", v));
                }
            });
        }

        if (colBidCount != null)
            colBidCount.setCellValueFactory(new PropertyValueFactory<>("bidCount"));

        if (colStatus != null) {
            colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
            colStatus.setCellFactory(col -> new TableCell<>() {
                @Override protected void updateItem(AuctionStatus status, boolean empty) {
                    super.updateItem(status, empty);
                    if (empty || status == null) { setText(null); setStyle(""); return; }
                    String s = status.name();
                    setText(s);
                    String color = switch (s) {
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

        if (colEndTime != null) {
            colEndTime.setCellValueFactory(cellData -> {
                AuctionResponse a = cellData.getValue();
                if (a == null || a.getEndTime() == null)
                    return new javafx.beans.property.SimpleStringProperty("—");
                return new javafx.beans.property.SimpleStringProperty(a.getEndTime().format(dtFmt));
            });
            colEndTime.setCellFactory(col -> new TableCell<>() {
                @Override protected void updateItem(String s, boolean empty) {
                    super.updateItem(s, empty);
                    setText(empty || s == null ? null : s);
                }
            });
        }

        setupActionColumn();

        filteredSellerAuctions = new FilteredList<>(sellerAuctions, p -> true);
        SortedList<AuctionResponse> sorted = new SortedList<>(filteredSellerAuctions);
        sorted.comparatorProperty().bind(tvSellerItems.comparatorProperty());
        tvSellerItems.setItems(sorted);

        if (cboStatusFilter != null) {
            cboStatusFilter.getItems().clear();
            cboStatusFilter.getItems().addAll("Tất cả", "OPEN", "RUNNING", "FINISHED", "PAID", "CANCELED");
            cboStatusFilter.setValue("Tất cả");
        }

        try {
            MessageHandler handler = getMessageHandlerSecurely();
            if (handler != null) {
                handler.addBidListener(this);
                handler.addAuctionListener(this);
            }
        } catch (Exception e) {
            System.err.println("Cảnh báo: Không thể đăng ký Real-time update do giới hạn truy cập.");
        }
        if (cboCategory != null) {
            cboCategory.getItems().clear();
            // Tự động lấy toàn bộ Tên hiển thị từ Enum nhét vào ComboBox
            for (ItemCategory cat : ItemCategory.values()) {
                cboCategory.getItems().add(cat.getDisplayName());
            }
            // Set mặc định là Tài sản khác
            cboCategory.setValue(ItemCategory.OTHER.getDisplayName());
        }
        loadMyAuctions();
        // Khởi tạo khung chọn ảnh
        Platform.runLater(this::setupImageAddButton);
    }

    private MessageHandler getMessageHandlerSecurely() throws Exception {
        Field field = SocketClient.class.getDeclaredField("messageHandler");
        field.setAccessible(true);
        return (MessageHandler) field.get(SocketClient.getInstance());
    }

    @FXML
    void handleImagePicker(javafx.scene.input.MouseEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh sản phẩm (có thể chọn nhiều)");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
        );
        javafx.stage.Stage stage = (javafx.stage.Stage) hboxImageRow.getScene().getWindow();
        List<java.io.File> files = chooser.showOpenMultipleDialog(stage);
        if (files == null || files.isEmpty()) return;
        int added = 0;
        for (java.io.File file : files) {
            try {
                java.awt.image.BufferedImage original = javax.imageio.ImageIO.read(file);
                if (original == null) continue;
                int maxSize = 600;
                int w = original.getWidth(), h = original.getHeight();
                if (w > maxSize || h > maxSize) {
                    double ratio = Math.min((double)maxSize/w, (double)maxSize/h);
                    w = (int)(w*ratio); h = (int)(h*ratio);
                }
                java.awt.image.BufferedImage resized = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g2d = resized.createGraphics();
                g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.drawImage(original, 0, 0, w, h, null); g2d.dispose();
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next();
                javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.80f);
                writer.setOutput(javax.imageio.ImageIO.createImageOutputStream(baos));
                writer.write(null, new javax.imageio.IIOImage(resized, null, null), param);
                writer.dispose();
                byte[] compressed = baos.toByteArray();
                int currentIdx = selectedImagesBase64.size();
                selectedImagesBase64.add(Base64.getEncoder().encodeToString(compressed));
                addImageThumbnail(compressed, currentIdx);
                added++;
            } catch (Exception e) {
                AlertUtil.showError("Lỗi", "Không thể xử lý: " + file.getName());
            }
        }
        if (added > 0) updateImageLabel();
    }

    @FXML
    void handleAddItem(ActionEvent event) {
        try {
            String title = txtProductName.getText().trim();
            if (title.isEmpty()) { AlertUtil.showError("Lỗi", "Vui lòng nhập tên sản phẩm."); return; }

            double startPrice = Double.parseDouble(txtStartPrice.getText().trim());
            double minIncrement;
            try {
                minIncrement = Double.parseDouble(txtMinIncrement.getText().trim());
            } catch (Exception ex) {
                minIncrement = Math.max(1, Math.round(startPrice * 0.05));
            }
            int duration = Integer.parseInt(txtDuration.getText().trim());

            // [GIỮ LẠI] Hỗ trợ lên lịch bắt đầu
            String startTimeStr = null;
            LocalDate date     = dpStartDate != null ? dpStartDate.getValue() : null;
            String   timeText  = txtStartTime != null ? txtStartTime.getText().trim() : "";

            if (date != null) {
                LocalTime time = LocalTime.of(0, 0);
                if (!timeText.isEmpty()) {
                    try {
                        time = LocalTime.parse(timeText, DateTimeFormatter.ofPattern("HH:mm"));
                    } catch (DateTimeParseException e) {
                        AlertUtil.showError("Lỗi", "Định dạng giờ không hợp lệ. Vui lòng nhập HH:mm (ví dụ: 14:30).");
                        return;
                    }
                }
                LocalDateTime startDt = LocalDateTime.of(date, time);
                if (startDt.isBefore(LocalDateTime.now())) {
                    AlertUtil.showError("Lỗi", "Thời gian bắt đầu phải ở tương lai.");
                    return;
                }
                startTimeStr = startDt.toString();
            }

            Map<String, Object> data = new HashMap<>();
            data.put("title",           title);
            String rawDesc = txtDescription.getText();
            String descWithImage;
            if (!selectedImagesBase64.isEmpty()) {
                String joined = String.join("|", selectedImagesBase64);
                descWithImage = "[IMGS:" + joined + "]" + rawDesc;
            } else {
                descWithImage = rawDesc;
            }
            data.put("description",     descWithImage);
            data.put("startingPrice",   startPrice);
            data.put("minBidIncrement", minIncrement);
            data.put("durationMinutes", duration);
            // 1. Lấy tên hiển thị mà người dùng chọn (mặc định là OTHER)
            String selectedDisplayName = (cboCategory != null && cboCategory.getValue() != null)
                    ? cboCategory.getValue() : ItemCategory.OTHER.getDisplayName();

            // 2. Quy đổi ngược từ Tên hiển thị -> Mã Enum chuẩn (ART, VEHICLE...)
            String categoryToSend = ItemCategory.OTHER.name();
            for (ItemCategory cat : ItemCategory.values()) {
                if (cat.getDisplayName().equals(selectedDisplayName)) {
                    categoryToSend = cat.name();
                    break;
                }
            }

            // 3. Gửi mã Enum chuẩn lên Server (Cực kỳ an toàn)
            data.put("category", categoryToSend);
            if (startTimeStr != null) {
                data.put("startTime", startTimeStr);
            }

            AuctionResponse response = SocketClient.getInstance().send(
                    Actions.CREATE_AUCTION, data, AuctionResponse.class);

            if (response != null) {
                String msg = startTimeStr != null
                        ? "Đã đăng phiên đấu giá. Sẽ bắt đầu lúc " + startTimeStr.replace("T", " ")
                        : "Đã đăng phiên đấu giá. Bắt đầu ngay lập tức.";
                AlertUtil.showInfo("Thành công", msg);
                clearFields();
                loadMyAuctions();
            }
        } catch (NumberFormatException nfe) {
            AlertUtil.showError("Lỗi", "Vui lòng nhập đúng định dạng số cho giá và thời lượng.");
        } catch (Exception e) {
            AlertUtil.showError("Lỗi", e.getMessage());
        }
    }

    @Override
    public void onBidUpdated(JsonObject rawData) {
        if (rawData.has("data")) {
            JsonObject data = rawData.getAsJsonObject("data");
            String auctionId = data.get("auctionId").getAsString();
            double newPrice  = data.get("newCurrentPrice").getAsDouble();
            Platform.runLater(() -> {
                for (AuctionResponse item : sellerAuctions) {
                    if (item.getAuctionId().equals(auctionId)) {
                        item.setCurrentPrice(newPrice);
                        tvSellerItems.refresh();
                        break;
                    }
                }
                updateStats();
            });
        }
    }

    @Override
    public void onAuctionStatusChanged(JsonObject rawData) {
        Platform.runLater(this::loadMyAuctions);
    }

    private void loadMyAuctions() {
        new Thread(() -> {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("status", "ALL");

                GetAuctionsResponse response = SocketClient.getInstance().send(
                        Actions.GET_AUCTIONS, params, GetAuctionsResponse.class);

                if (response != null && response.auctions != null) {
                    String myId = UserSession.getInstance().getUserId();
                    Platform.runLater(() -> {
                        sellerAuctions.clear();
                        for (AuctionResponse a : response.auctions) {
                            if (myId != null && myId.equals(a.getSellerId())) {
                                sellerAuctions.add(a);
                            }
                        }
                        updateStats();
                        lblTableMessage.setVisible(sellerAuctions.isEmpty());
                    });
                } else {
                    Platform.runLater(() -> {
                        sellerAuctions.clear();
                        updateStats();
                        lblTableMessage.setText("Không tìm thấy dữ liệu sản phẩm.");
                        lblTableMessage.setVisible(true);
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    lblTableMessage.setText("Lỗi tải dữ liệu: " + e.getMessage());
                    lblTableMessage.setVisible(true);
                });
                e.printStackTrace();
            }
        }).start();
    }

    private void setupActionColumn() {
        colAction.setCellFactory(param -> new TableCell<>() {
            private final Button btn = new Button("Hủy");
            {
                btn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
                btn.setOnAction(e -> handleCloseAuction(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
    }

    // [GIỮ LẠI] Dialog xác nhận + dùng CANCEL_AUCTION (đúng với Seller)
    private void handleCloseAuction(AuctionResponse item) {
        String status = item.getStatus() != null ? item.getStatus().name() : "";
        if ("PAID".equals(status) || "CANCELED".equals(status)) {
            AlertUtil.showInfo("Thông báo", "Phiên này không thể hủy (trạng thái: " + status + ").");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận hủy phiên");
        confirm.setHeaderText("Bạn có chắc muốn HỦY phiên đấu giá này?");
        confirm.setContentText("\"" + item.getTitle() + "\"\nHành động này không thể hoàn tác.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (!result.isPresent() || result.get() != ButtonType.OK) return;

        try {
            Map<String, String> data = new HashMap<>();
            data.put("auctionId", item.getAuctionId());
            SocketClient.getInstance().send(Actions.CANCEL_AUCTION, data, Void.class);
            loadMyAuctions();
        } catch (Exception e) {
            AlertUtil.showError("Lỗi", e.getMessage());
        }
    }

    @FXML
    private void handleStatusFilter(ActionEvent event) {
        applyStatusFilter();
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadMyAuctions();
        // Khởi tạo khung chọn ảnh
        Platform.runLater(this::setupImageAddButton);
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            MessageHandler handler = getMessageHandlerSecurely();
            if (handler != null) {
                handler.removeBidListener(this);
                handler.removeAuctionListener(this);
            }
        } catch (Exception ignored) {}

        UserSession.getInstance().cleanUserSession();
        try {
            ViewLoader.load(event, "login.fxml", "Đăng nhập hệ thống");
        } catch (Exception e) {
            AlertUtil.showError("Lỗi", "Không thể quay lại màn hình đăng nhập.");
        }
    }

    // [MERGE] Dùng phiên bản tốt hơn từ bạn bè: điều hướng trong cùng 1 cửa sổ
    // (tránh mở cửa sổ mới gây khó đóng listener)
    @FXML
    private void handleOpenWallet(ActionEvent event) {
        try {
            MessageHandler handler = getMessageHandlerSecurely();
            if (handler != null) {
                handler.removeBidListener(this);
                handler.removeAuctionListener(this);
            }
        } catch (Exception ignored) {}

        try {
            ViewLoader.ViewResult<SellerWalletController> result =
                    ViewLoader.loadViewWithController("seller-wallet.fxml");

            if (result != null) {
                javafx.stage.Stage stage = (javafx.stage.Stage)
                        ((javafx.scene.Node) event.getSource()).getScene().getWindow();
                stage.getScene().setRoot(result.getView());
                stage.setTitle("💵 Ví Doanh Thu – Seller");
            } else {
                AlertUtil.showError("Lỗi giao diện", "Không thể khởi tạo màn hình Ví doanh thu.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.showError("Lỗi giao diện", "Không thể mở màn hình Ví doanh thu: " + e.getMessage());
        }
    }

    @FXML
    private void applyStatusFilter() {
        String status = (cboStatusFilter == null || cboStatusFilter.getValue() == null)
                ? "Tất cả" : cboStatusFilter.getValue();
        filteredSellerAuctions.setPredicate(a -> {
            if (a == null) return false;
            if ("Tất cả".equals(status)) return true;
            AuctionStatus st = a.getStatus();
            String s = (st == null) ? "" : st.name();
            return s.contains(status.toUpperCase());
        });
        updateStats();
    }

    private void updateStats() {
        int total = sellerAuctions.size();

        long active = sellerAuctions.stream()
                .filter(a -> a.getStatus() != null && AuctionStatus.RUNNING.equals(a.getStatus()))
                .count();

        long closed = sellerAuctions.stream()
                .filter(a -> a.getStatus() != null
                        && (AuctionStatus.FINISHED.equals(a.getStatus()) || AuctionStatus.PAID.equals(a.getStatus())))
                .count();

        long revenue = sellerAuctions.stream()
                .filter(a -> a.getCurrentPrice() != null)
                .mapToLong(a -> Math.round(a.getCurrentPrice()))
                .sum();

        if (lblTotalItems  != null) lblTotalItems.setText(String.valueOf(total));
        if (lblActiveItems != null) lblActiveItems.setText(String.valueOf(active));
        if (lblClosedItems != null) lblClosedItems.setText(String.valueOf(closed));
        if (lblTotalRevenue != null) lblTotalRevenue.setText(String.format("%,d VNĐ", revenue));
        if (lblItemCount != null)
            lblItemCount.setText(String.format("%d sản phẩm",
                    filteredSellerAuctions == null ? total : filteredSellerAuctions.size()));
    }

    // ─────────────────── Multi-image helpers ───────────────────

    private void setupImageAddButton() {
        if (hboxImageRow == null) return;
        hboxImageRow.getChildren().clear();
        hboxImageRow.getChildren().add(createAddButton());
    }

    private StackPane createAddButton() {
        StackPane pane = new StackPane();
        pane.setPrefSize(128, 128); pane.setMinSize(128, 128);
        pane.setStyle("-fx-border-color:#9b59b6;-fx-border-style:dashed;-fx-border-width:2;"
                + "-fx-border-radius:10;-fx-background-radius:10;"
                + "-fx-background-color:#9b59b610;-fx-cursor:hand;");
        VBox inner = new VBox(6); inner.setAlignment(Pos.CENTER);
        Label icon = new Label("📷"); icon.setStyle("-fx-font-size:30px;");
        Label txt  = new Label("Thêm ảnh"); txt.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#9b59b6;");
        Label hint = new Label("(Click để chọn)"); hint.setStyle("-fx-font-size:10px;-fx-text-fill:#aaaaaa;-fx-font-style:italic;");
        inner.getChildren().addAll(icon, txt, hint);
        pane.getChildren().add(inner);
        pane.setOnMouseClicked(e -> handleImagePicker(e));
        return pane;
    }

    private void addImageThumbnail(byte[] bytes, int index) {
        StackPane thumb = buildThumb(bytes, index);
        int insertPos = hboxImageRow.getChildren().size() - 1;
        hboxImageRow.getChildren().add(Math.max(0, insertPos), thumb);
    }

    private void refreshImageRow() {
        if (hboxImageRow == null) return;
        hboxImageRow.getChildren().clear();
        for (int i = 0; i < selectedImagesBase64.size(); i++) {
            try {
                byte[] b = Base64.getDecoder().decode(selectedImagesBase64.get(i));
                hboxImageRow.getChildren().add(buildThumb(b, i));
            } catch (Exception ignored) {}
        }
        hboxImageRow.getChildren().add(createAddButton());
        updateImageLabel();
    }

    private StackPane buildThumb(byte[] bytes, int index) {
        StackPane thumb = new StackPane();
        thumb.setPrefSize(128, 128); thumb.setMinSize(128, 128);
        String border = index == 0 ? "#9b59b6" : "#44444460";
        thumb.setStyle("-fx-background-color:#111;-fx-background-radius:10;"
                + "-fx-border-color:" + border + ";-fx-border-radius:10;-fx-border-width:2;");
        Image img = new Image(new java.io.ByteArrayInputStream(bytes));
        ImageView iv = new ImageView(img);
        iv.setFitWidth(128); iv.setFitHeight(128); iv.setPreserveRatio(true);
        Label badge = new Label(index == 0 ? "Đại diện" : "");
        badge.setStyle("-fx-background-color:#9b59b6;-fx-text-fill:white;"
                + "-fx-font-size:10px;-fx-padding:2 6;-fx-background-radius:0 0 8 0;");
        StackPane.setAlignment(badge, Pos.TOP_LEFT);
        final int idx = index;
        Button del = new Button("×");
        del.setStyle("-fx-background-color:#e74c3c;-fx-text-fill:white;"
                + "-fx-font-size:13px;-fx-padding:0 5;-fx-cursor:hand;"
                + "-fx-background-radius:0 8 0 8;-fx-border-width:0;");
        StackPane.setAlignment(del, Pos.TOP_RIGHT);
        del.setOnAction(e -> { selectedImagesBase64.remove(idx); refreshImageRow(); });
        thumb.getChildren().addAll(iv, badge, del);
        return thumb;
    }

    private void updateImageLabel() {
        if (lblImageName == null) return;
        int n = selectedImagesBase64.size();
        lblImageName.setText(n == 0 ? "" : "✓ " + n + " ảnh đã chọn");
    }


    private void clearFields() {
        txtProductName.clear();
        txtDescription.clear();
        txtStartPrice.clear();
        txtMinIncrement.clear();
        if (dpStartDate  != null) dpStartDate.setValue(null);
        if (txtStartTime != null) txtStartTime.clear();
        txtDuration.clear();
        lblFormMessage.setVisible(false);
        // Reset ảnh
        selectedImagesBase64.clear();
        setupImageAddButton();
        if (lblImageName != null) lblImageName.setText("");
    }
}