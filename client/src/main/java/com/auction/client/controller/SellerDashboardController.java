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
import javafx.scene.input.MouseEvent;
import javafx.stage.Popup;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.geometry.Insets;

public class SellerDashboardController implements BidUpdateListener, AuctionUpdateListener {

    // ===== Form đăng sản phẩm (cột trái) =====
    @FXML private TextField txtProductName;
    @FXML private TextArea  txtDescription;
    @FXML private TextField txtStartPrice;
    @FXML private TextField txtMinIncrement;
    @FXML private DatePicker dpStartDate;
    @FXML private TextField txtStartTime;
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

    // ===== Danh sách sản phẩm (cột phải) =====
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

    // ===== Profile Popup =====
    @FXML private HBox headerUserArea;
    private Popup profilePopup;

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
            // Hiệu ứng hover cho vùng Avatar
            if (headerUserArea != null) {
                headerUserArea.setOnMouseEntered(e ->
                        headerUserArea.setStyle("-fx-cursor: hand; -fx-padding: 4 10; -fx-background-radius: 12; -fx-background-color: rgba(155,89,182,0.12);"));
                headerUserArea.setOnMouseExited(e ->
                        headerUserArea.setStyle("-fx-cursor: hand; -fx-padding: 4 10; -fx-background-radius: 12; -fx-background-color: transparent;"));
            }
        }

        DateTimeFormatter dtFmt = DateTimeFormatter.ofPattern("dd/MM HH:mm");

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
            for (ItemCategory cat : ItemCategory.values()) {
                cboCategory.getItems().add(cat.getDisplayName());
            }
            cboCategory.setValue(ItemCategory.OTHER.getDisplayName());
        }
        loadMyAuctions();
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
            String selectedDisplayName = (cboCategory != null && cboCategory.getValue() != null)
                ? cboCategory.getValue() : ItemCategory.OTHER.getDisplayName();

            String categoryToSend = ItemCategory.OTHER.name();
            for (ItemCategory cat : ItemCategory.values()) {
                if (cat.getDisplayName().equals(selectedDisplayName)) {
                    categoryToSend = cat.name();
                    break;
                }
            }

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

    // =========================================================================
    // Action column: nút [✏ Sửa] (chỉ OPEN) + [Hủy]
    // =========================================================================
    private void setupActionColumn() {
        colAction.setCellFactory(param -> new TableCell<>() {

            private final Button btnEdit   = new Button("✏ Sửa");
            private final Button btnCancel = new Button("Hủy");
            private final HBox   box       = new HBox(6, btnEdit, btnCancel);

            {
                box.setAlignment(Pos.CENTER);

                btnEdit.setStyle(
                    "-fx-background-color: #2980b9; -fx-text-fill: white;" +
                        "-fx-font-size: 11px; -fx-padding: 4 10;" +
                        "-fx-background-radius: 6; -fx-cursor: hand;");
                btnEdit.setOnAction(e -> {
                    AuctionResponse item = getTableView().getItems().get(getIndex());
                    handleEditAuction(item);
                });

                btnCancel.setStyle(
                    "-fx-background-color: #e74c3c; -fx-text-fill: white;" +
                        "-fx-font-size: 11px; -fx-padding: 4 10;" +
                        "-fx-background-radius: 6; -fx-cursor: hand;");
                btnCancel.setOnAction(e -> {
                    AuctionResponse item = getTableView().getItems().get(getIndex());
                    handleCloseAuction(item);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                AuctionResponse auction = getTableView().getItems().get(getIndex());
                String status = auction.getStatus() != null ? auction.getStatus().name() : "";

                // Chỉ hiện nút Sửa khi OPEN
                btnEdit.setVisible("OPEN".equals(status));
                btnEdit.setManaged("OPEN".equals(status));

                // Ẩn nút Hủy khi đã PAID hoặc CANCELED
                boolean canCancel = !"PAID".equals(status) && !"CANCELED".equals(status);
                btnCancel.setVisible(canCancel);
                btnCancel.setManaged(canCancel);

                setGraphic(box);
            }
        });
    }

    // =========================================================================
    // Dialog sửa phiên đấu giá (chỉ OPEN)
    // =========================================================================
    private void handleEditAuction(AuctionResponse auction) {
        if (auction.getStatus() != AuctionStatus.OPEN) {
            AlertUtil.showInfo("Không thể sửa",
                "Chỉ có thể sửa phiên đấu giá khi còn ở trạng thái OPEN.\n"
                    + "Trạng thái hiện tại: " + auction.getStatus());
            return;
        }

        // ── Build Dialog ──────────────────────────────────────────────────────
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle("✏ Chỉnh sửa phiên đấu giá");
        dialog.setHeaderText("Sản phẩm: " + auction.getTitle()
            + "\n⚠ Chỉ có thể sửa khi phiên còn OPEN.");

        // Các trường nhập
        TextField    fTitle    = new TextField(auction.getTitle());
        TextArea     fDesc     = new TextArea(stripImagePrefix(
            auction.getDescription() != null ? auction.getDescription() : ""));
        fDesc.setPrefRowCount(3); fDesc.setWrapText(true);

        TextField    fPrice    = new TextField(String.format("%.0f", auction.getStartingPrice()));
        TextField    fIncr     = new TextField(String.format("%.0f",
            auction.getMinBidIncrement() > 0 ? auction.getMinBidIncrement() : 0));

        ComboBox<String> fCat  = new ComboBox<>();
        for (ItemCategory cat : ItemCategory.values()) fCat.getItems().add(cat.getDisplayName());
        fCat.setValue(ItemCategory.OTHER.getDisplayName());
        if (auction.getCategory() != null) {
            for (ItemCategory cat : ItemCategory.values()) {
                if (cat.name().equals(auction.getCategory())) {
                    fCat.setValue(cat.getDisplayName()); break;
                }
            }
        }

        DateTimeFormatter dtFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        DatePicker fStartDate = new DatePicker();
        TextField  fStartHour = new TextField("00:00");
        DatePicker fEndDate   = new DatePicker();
        TextField  fEndHour   = new TextField("23:59");

        // Điền giá trị hiện tại
        if (auction.getStartTime() != null) {
            fStartDate.setValue(auction.getStartTime().toLocalDate());
            fStartHour.setText(auction.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        }
        if (auction.getEndTime() != null) {
            fEndDate.setValue(auction.getEndTime().toLocalDate());
            fEndHour.setText(auction.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        }

        // Layout
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(16, 24, 8, 24));

        int row = 0;
        grid.add(new Label("Tên sản phẩm *"),   0, row); grid.add(fTitle, 1, row++);
        grid.add(new Label("Mô tả"),             0, row); grid.add(fDesc,  1, row++);
        grid.add(new Label("Danh mục"),          0, row); grid.add(fCat,   1, row++);
        grid.add(new Label("Giá khởi điểm *"),  0, row); grid.add(fPrice,  1, row++);
        grid.add(new Label("Bước giá *"),        0, row); grid.add(fIncr,   1, row++);

        HBox startBox = new HBox(8, fStartDate, new Label("Giờ:"), fStartHour);
        startBox.setAlignment(Pos.CENTER_LEFT);
        grid.add(new Label("Ngày bắt đầu"),      0, row); grid.add(startBox, 1, row++);

        HBox endBox = new HBox(8, fEndDate, new Label("Giờ:"), fEndHour);
        endBox.setAlignment(Pos.CENTER_LEFT);
        grid.add(new Label("Ngày kết thúc *"),   0, row); grid.add(endBox,  1, row++);

        // ── Section ảnh sản phẩm ───────────────────────────────────────────
        // Load ảnh hiện tại từ description prefix vào selectedImagesBase64
        selectedImagesBase64.clear();
        String existingDesc = auction.getDescription() != null ? auction.getDescription() : "";
        if (existingDesc.startsWith("[IMGS:")) {
            int end = existingDesc.indexOf("]");
            if (end > 6) {
                for (String p : existingDesc.substring(6, end).split("\\|")) {
                    if (!p.isBlank()) selectedImagesBase64.add(p);
                }
            }
        } else if (existingDesc.startsWith("[IMG:")) {
            int end = existingDesc.indexOf("]");
            if (end > 5) selectedImagesBase64.add(existingDesc.substring(5, end));
        }

        // HBox preview ảnh (tái dùng refreshImageRow)
        HBox editImgRow = new HBox(8);
        editImgRow.setAlignment(Pos.CENTER_LEFT);
        // Nút thêm ảnh
        Button btnAddImg = new Button("+ Thêm ảnh");
        btnAddImg.setStyle("-fx-background-color:#8e44ad;-fx-text-fill:white;-fx-cursor:hand;-fx-background-radius:8;");
        btnAddImg.setOnAction(ev -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Chọn ảnh sản phẩm");
            fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Ảnh", "*.jpg","*.jpeg","*.png","*.gif","*.webp"));
            fc.setSelectedExtensionFilter(fc.getExtensionFilters().get(0));
            java.util.List<java.io.File> files = fc.showOpenMultipleDialog(dialog.getDialogPane().getScene().getWindow());
            if (files != null) {
                for (java.io.File f : files) {
                    try {
                        // FIX Bug 2: compress ảnh trước khi lưu (giống main form)
                        java.awt.image.BufferedImage original = javax.imageio.ImageIO.read(f);
                        if (original == null) continue;
                        int MAX = 800;
                        int w = original.getWidth(), h = original.getHeight();
                        if (w > MAX || h > MAX) {
                            double ratio = Math.min((double) MAX / w, (double) MAX / h);
                            w = (int)(w * ratio); h = (int)(h * ratio);
                        }
                        java.awt.image.BufferedImage resized = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
                        java.awt.Graphics2D g2d = resized.createGraphics();
                        g2d.drawImage(original, 0, 0, w, h, null); g2d.dispose();
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next();
                        javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
                        param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                        param.setCompressionQuality(0.75f);
                        writer.setOutput(javax.imageio.ImageIO.createImageOutputStream(baos));
                        writer.write(null, new javax.imageio.IIOImage(resized, null, null), param);
                        selectedImagesBase64.add(Base64.getEncoder().encodeToString(baos.toByteArray()));
                    } catch (Exception ignored) {}
                }
                refreshEditImgRow(editImgRow, btnAddImg);
            }
        });
        // FIX Bug 1: refreshEditImgRow đã tự thêm addBtn — không add thủ công nữa
        refreshEditImgRow(editImgRow, btnAddImg);

        ScrollPane imgScroll = new ScrollPane(editImgRow);
        imgScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        imgScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        imgScroll.setPrefHeight(100); imgScroll.setFitToHeight(true);
        imgScroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");

        grid.add(new Label("Ảnh sản phẩm"), 0, row); grid.add(imgScroll, 1, row++);

        // Ghi chú
        Label note = new Label("* Bắt buộc. Sau khi phiên chuyển sang RUNNING sẽ không thể sửa nữa.");
        note.setStyle("-fx-font-size: 11px; -fx-text-fill: #e67e22; -fx-font-style: italic;");
        grid.add(note, 0, row, 2, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(520);

        ButtonType btnSave   = new ButtonType("💾 Lưu thay đổi", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel = new ButtonType("Hủy bỏ",          ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnSave, btnCancel);

        // Style nút Lưu
        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(btnSave);
        saveBtn.setStyle(
            "-fx-background-color: #2980b9; -fx-text-fill: white;" +
                "-fx-font-weight: bold; -fx-padding: 8 20; -fx-background-radius: 8;");

        // Result converter
        dialog.setResultConverter(bt -> {
            if (bt != btnSave) return null;

            // ── Validate ────────────────────────────────────────────────
            String title = fTitle.getText().trim();
            if (title.isEmpty()) {
                AlertUtil.showError("Lỗi", "Tên sản phẩm không được để trống.");
                return null;
            }

            double price, incr;
            try { price = Double.parseDouble(fPrice.getText().trim()); }
            catch (Exception ex) { AlertUtil.showError("Lỗi", "Giá khởi điểm không hợp lệ."); return null; }
            try { incr  = Double.parseDouble(fIncr.getText().trim());  }
            catch (Exception ex) { AlertUtil.showError("Lỗi", "Bước giá không hợp lệ."); return null; }

            if (price <= 0) { AlertUtil.showError("Lỗi", "Giá khởi điểm phải lớn hơn 0."); return null; }
            // FIX Bug 3: cho phép bước giá = 0 (giữ nguyên giá trị cũ hoặc không có bước tối thiểu)
            if (incr < 0) { AlertUtil.showError("Lỗi", "Bước giá không được âm."); return null; }

            if (fEndDate.getValue() == null) {
                AlertUtil.showError("Lỗi", "Vui lòng chọn ngày kết thúc."); return null;
            }
            LocalTime endTime;
            try { endTime = LocalTime.parse(fEndHour.getText().trim(), DateTimeFormatter.ofPattern("HH:mm")); }
            catch (Exception ex) { AlertUtil.showError("Lỗi", "Định dạng giờ kết thúc không hợp lệ (HH:mm)."); return null; }

            LocalDateTime endDt = LocalDateTime.of(fEndDate.getValue(), endTime);
            if (endDt.isBefore(LocalDateTime.now().plusMinutes(5))) {
                AlertUtil.showError("Lỗi", "Thời gian kết thúc phải ít nhất 5 phút từ bây giờ.");
                return null;
            }

            // Thời gian bắt đầu (tùy chọn)
            LocalDateTime startDt = null;
            if (fStartDate.getValue() != null) {
                try {
                    LocalTime st = LocalTime.parse(fStartHour.getText().trim(), DateTimeFormatter.ofPattern("HH:mm"));
                    startDt = LocalDateTime.of(fStartDate.getValue(), st);
                    if (startDt.isAfter(endDt)) {
                        AlertUtil.showError("Lỗi", "Thời gian bắt đầu phải trước thời gian kết thúc.");
                        return null;
                    }
                } catch (Exception ex) {
                    AlertUtil.showError("Lỗi", "Định dạng giờ bắt đầu không hợp lệ (HH:mm).");
                    return null;
                }
            }

            // Mô tả: build lại với ảnh mới từ selectedImagesBase64 + text mới
            String plainText = fDesc.getText().trim();
            String newDesc;
            if (!selectedImagesBase64.isEmpty()) {
                newDesc = "[IMGS:" + String.join("|", selectedImagesBase64) + "]" + plainText;
            } else {
                newDesc = plainText;
            }

            // Lấy category enum name
            String catEnumName = ItemCategory.OTHER.name();
            String selectedDisplay = fCat.getValue();
            for (ItemCategory cat : ItemCategory.values()) {
                if (cat.getDisplayName().equals(selectedDisplay)) {
                    catEnumName = cat.name(); break;
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("auctionId",      auction.getAuctionId());
            result.put("title",          title);
            result.put("description",    newDesc);
            result.put("category",       catEnumName);
            result.put("startingPrice",  price);
            result.put("minBidIncrement", incr);
            result.put("endTime",        endDt.toString());
            if (startDt != null) result.put("startTime", startDt.toString());
            return result;
        });

        Optional<Map<String, Object>> result = dialog.showAndWait();
        result.ifPresent(data -> sendUpdateAuction(data));
    }

    /** Gửi request UPDATE_AUCTION lên server trong thread riêng */
    private void sendUpdateAuction(Map<String, Object> data) {
        new Thread(() -> {
            try {
                AuctionResponse resp = SocketClient.getInstance().send(
                    Actions.UPDATE_AUCTION, data, AuctionResponse.class);

                Platform.runLater(() -> {
                    if (resp != null) {
                        AlertUtil.showInfo("Thành công",
                            "Đã cập nhật phiên đấu giá \"" + resp.getTitle() + "\" thành công!");
                        loadMyAuctions();
                    } else {
                        AlertUtil.showError("Thất bại",
                            "Không nhận được phản hồi từ Server. Vui lòng thử lại.");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() ->
                    AlertUtil.showError("Lỗi cập nhật", e.getMessage()));
            }
        }).start();
    }

    // =========================================================================
    // Cancel auction
    // =========================================================================
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

    /** Rebuild ảnh preview trong dialog edit — luôn đặt addBtn ở cuối */
    private void refreshEditImgRow(HBox row, Button addBtn) {
        row.getChildren().clear();
        for (int i = 0; i < selectedImagesBase64.size(); i++) {
            try {
                byte[] b = Base64.getDecoder().decode(selectedImagesBase64.get(i));
                final int idx = i;
                StackPane thumb = buildThumb(b, idx);
                // Override nút xóa để rebuild editImgRow thay vì hboxImageRow
                Button del = new Button("✕");
                del.setStyle("-fx-background-color:#e74c3c;-fx-text-fill:white;-fx-font-size:11px;-fx-padding:0 4;-fx-background-radius:0 8 8 0;-fx-border-width:0;");
                StackPane.setAlignment(del, javafx.geometry.Pos.TOP_RIGHT);
                del.setOnAction(e -> { selectedImagesBase64.remove(idx); refreshEditImgRow(row, addBtn); });
                thumb.getChildren().removeIf(n -> n instanceof Button);
                thumb.getChildren().add(del);
                row.getChildren().add(thumb);
            } catch (Exception ignored) {}
        }
        // ← FIX Bug 1: luôn thêm addBtn ở cuối — không để mất sau khi xóa ảnh
        if (!row.getChildren().contains(addBtn)) {
            row.getChildren().add(addBtn);
        }
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

    // ─────────────────── String helpers ───────────────────

    /** Bỏ prefix [IMGS:...] hoặc [IMG:...] để lấy phần mô tả thuần */
    private String stripImagePrefix(String raw) {
        if (raw == null) return "";
        if (raw.startsWith("[IMGS:") || raw.startsWith("[IMG:")) {
            int end = raw.indexOf("]");
            if (end > 0) return raw.substring(end + 1).trim();
        }
        return raw;
    }

    /** Trích prefix [IMGS:...] hoặc [IMG:...] để ghép lại khi chỉ sửa text */
    private String extractImagePrefix(String raw) {
        if (raw == null) return "";
        if (raw.startsWith("[IMGS:") || raw.startsWith("[IMG:")) {
            int end = raw.indexOf("]");
            if (end > 0) return raw.substring(0, end + 1);
        }
        return "";
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
        selectedImagesBase64.clear();
        setupImageAddButton();
        if (lblImageName != null) lblImageName.setText("");
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
        double anchorX = source.localToScreen(source.getBoundsInLocal()).getMinX() - 40; // Dịch sang trái một chút cho cân
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
}