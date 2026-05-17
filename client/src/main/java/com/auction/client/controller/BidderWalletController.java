package com.auction.client.controller;

import com.auction.client.controller.AuctionDetailController;
import com.auction.client.model.UserSession;
import com.auction.client.network.SocketClient;
import com.auction.client.util.AlertUtil;
import com.auction.shared.dto.request.TopUpRequest;
import com.auction.shared.dto.response.WalletResponse;
import com.auction.shared.model.WalletTransaction;
import com.auction.shared.network.protocol.Actions;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.util.HashMap;

/**
 * Controller ví điện tử dành cho Bidder.
 * Chức năng:
 *   - Xem số dư hiện tại
 *   - Nạp tiền vào ví
 *   - Xem lịch sử giao dịch (BID_DEDUCT, BID_REFUND, TOP_UP, AUCTION_WIN)
 *   - Hiển thị cảnh báo khi số dư không đủ (gọi từ BiddingController)
 */
public class BidderWalletController {

    // ===== Header =====
    @FXML private Label lblUser;
    @FXML private Label lblRole;

    // ===== Thẻ số dư =====
    @FXML private Label lblBalance;
    @FXML private Label lblWalletStatus;
    @FXML private HBox hboxInsufficientWarning;  // Hiện/ẩn cảnh báo số dư không đủ
    @FXML private Label lblRequiredAmount;        // Số tiền cần để tham gia đấu giá

    // ===== Form nạp tiền =====
    @FXML private TextField txtTopUpAmount;
    @FXML private Button btnTopUp100k;
    @FXML private Button btnTopUp500k;
    @FXML private Button btnTopUp1M;
    @FXML private Button btnTopUp5M;
    @FXML private Button btnConfirmTopUp;
    @FXML private Label lblTopUpMessage;

    // ===== Bảng lịch sử =====
    @FXML private TableView<WalletTransaction> tvTransactions;
    @FXML private TableColumn<WalletTransaction, String> colTxType;
    @FXML private TableColumn<WalletTransaction, String> colTxAmount;
    @FXML private TableColumn<WalletTransaction, String> colTxBalance;
    @FXML private TableColumn<WalletTransaction, String> colTxDesc;
    @FXML private TableColumn<WalletTransaction, String> colTxTime;
    @FXML private Label lblTxCount;

    @FXML private Button btnClose;
    @FXML private Button btnTheme;  // Dark/Light mode toggle

    @FXML private Button btnRefresh;

    @FXML private Label lblAvailableBalance;

    @FXML private javafx.scene.layout.HBox headerUserArea;
    @FXML private javafx.scene.image.ImageView imgAvatar;

    // Nội bộ
    private double requiredAmount = 0;   // Số tiền cần để đặt giá (nếu mở từ BiddingController)
    private double currentBalance = 0;
    private String returnAuctionId = null; // ID phiên cần quay lại sau khi đóng ví

    private final ObservableList<WalletTransaction> txList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        if (btnTheme != null) btnTheme.setText(com.auction.client.util.ThemeManager.getInstance().getToggleIcon());
        com.auction.client.util.ProfileHeaderUtil.bindHeaderProfile(headerUserArea, imgAvatar);

        loadUserInfo();
        setupTable();
        loadWallet();

        // Mặc định ẩn cảnh báo số dư không đủ
        if (hboxInsufficientWarning != null) {
            hboxInsufficientWarning.setVisible(false);
            hboxInsufficientWarning.setManaged(false);
        }
    }

    /**
     * Gọi từ BiddingController để truyền auctionId, nhằm quay đúng về phiên đó khi đóng ví.
     * @param auctionId ID phiên đấu giá cần quay lại
     */
    public void setReturnAuctionId(String auctionId) {
        this.returnAuctionId = auctionId;
    }

    /**
     * Gọi từ BiddingController khi số dư không đủ để đặt giá.
     * @param required Số tiền tối thiểu cần có
     */
    public void setInsufficientMode(double required) {
        this.requiredAmount = required;
        Platform.runLater(() -> {
            if (hboxInsufficientWarning != null) {
                hboxInsufficientWarning.setVisible(true);
                hboxInsufficientWarning.setManaged(true);
            }
            if (lblRequiredAmount != null) {
                lblRequiredAmount.setText("Bạn cần tối thiểu " + formatVnd(required)
                    + " để tham gia đấu giá. Vui lòng nạp thêm tiền.");
            }
        });
    }

    // ───────────────────────────────────────────────────────────────
    // Nút nhanh nạp tiền
    // ───────────────────────────────────────────────────────────────
    @FXML void handleQuick100k(ActionEvent e) { txtTopUpAmount.setText("100000"); }
    @FXML void handleQuick500k(ActionEvent e) { txtTopUpAmount.setText("500000"); }
    @FXML void handleQuick1M(ActionEvent e)   { txtTopUpAmount.setText("1000000"); }
    @FXML void handleQuick5M(ActionEvent e)   { txtTopUpAmount.setText("5000000"); }

    @FXML
    void handleConfirmTopUp(ActionEvent event) {
        String input = txtTopUpAmount.getText();
        if (input == null || input.trim().isEmpty()) {
            showTopUpMsg("Vui lòng nhập số tiền muốn nạp!", true);
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(input.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            showTopUpMsg("Số tiền không hợp lệ. Chỉ nhập số nguyên.", true);
            return;
        }
        if (amount <= 0) {
            showTopUpMsg("Số tiền nạp phải lớn hơn 0.", true);
            return;
        }

        btnConfirmTopUp.setDisable(true);
        btnConfirmTopUp.setText("Đang nạp...");

        final double capturedAmount = amount;
        new Thread(() -> {
            try {
                TopUpRequest req = new TopUpRequest(capturedAmount);
                WalletResponse resp = SocketClient.getInstance()
                    .send(Actions.TOP_UP, req, WalletResponse.class);

                Platform.runLater(() -> {
                    btnConfirmTopUp.setDisable(false);
                    btnConfirmTopUp.setText("NẠP TIỀN");
                    if (resp != null) {
                        currentBalance = resp.getBalance();
                        updateBalanceDisplay(resp.getBalance(), resp.getAvailableBalance());
                        txtTopUpAmount.clear();
                        showTopUpMsg("✅ Nạp thành công " + formatVnd(capturedAmount)
                            + "! Số dư: " + formatVnd(resp.getBalance()), false);
                        loadWallet(); // Reload lịch sử
                        // Ẩn cảnh báo nếu đã đủ tiền
                        if (requiredAmount > 0 && resp.getBalance() >= requiredAmount) {
                            if (hboxInsufficientWarning != null) {
                                hboxInsufficientWarning.setVisible(false);
                                hboxInsufficientWarning.setManaged(false);
                            }
                        }
                    } else {
                        showTopUpMsg("Không nhận được phản hồi từ server.", true);
                    }
                });
            } catch (Exception ex) {
                String msg = ex.getMessage() != null ? ex.getMessage() : "Lỗi không xác định.";
                Platform.runLater(() -> {
                    btnConfirmTopUp.setDisable(false);
                    btnConfirmTopUp.setText("NẠP TIỀN");
                    showTopUpMsg("❌ Lỗi: " + msg, true);
                });
            }
        }, "topup-thread").start();
    }

    @FXML
    void handleRefresh(ActionEvent event) {
        loadWallet();
    }

    @FXML
    void handleClose(ActionEvent event) {
        Stage stage = (Stage) btnClose.getScene().getWindow();
        try {
            if (returnAuctionId != null && !returnAuctionId.isEmpty()) {
                // TRƯỜNG HỢP 1: Mở ví từ bên trong Chi tiết một phiên đấu giá
                // -> Quay đúng về màn hình Chi tiết của phiên đó
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/auction/client/fxml/auction-detail.fxml"));
                javafx.scene.Parent root = loader.load();

                // Ép kiểu lấy Controller để truyền ID phiên vào lại
                AuctionDetailController controller = loader.getController();
                controller.initData(returnAuctionId);

                stage.getScene().setRoot(root);
                stage.setTitle("Chi tiết phiên đấu giá");
            } else {
                // TRƯỜNG HỢP 2: Mở ví từ nút ngoài màn hình Danh sách chính
                // -> Quay về màn hình Danh sách phiên
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/auction/client/fxml/auction-list.fxml"));
                javafx.scene.Parent root = loader.load();

                stage.getScene().setRoot(root);
                stage.setTitle("Danh sách phiên đấu giá - Online Auction System");
            }
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.showError("Lỗi điều hướng", "Không thể quay lại màn hình trước: " + e.getMessage());
        }
    }

    // ───────────────────────────────────────────────────────────────
    // Internal helpers
    // ───────────────────────────────────────────────────────────────
    private void loadWallet() {
        new Thread(() -> {
            try {
                WalletResponse resp = SocketClient.getInstance()
                    .send(Actions.GET_WALLET, new HashMap<>(), WalletResponse.class);

                Platform.runLater(() -> {
                    if (resp != null) {
                        currentBalance = resp.getBalance();
                        updateBalanceDisplay(resp.getBalance(), resp.getAvailableBalance());
                        txList.clear();
                        if (resp.getTransactions() != null) {
                            txList.addAll(resp.getTransactions());
                        }
                        if (lblTxCount != null) lblTxCount.setText(txList.size() + " giao dịch");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() ->
                    AlertUtil.showError("Lỗi tải ví", e.getMessage()));
            }
        }, "wallet-load-thread").start();
    }

    private void setupTable() {
        colTxType.setCellValueFactory(cd -> {
            WalletTransaction tx = cd.getValue();
            String label = switch (tx.getType()) {
                case TOP_UP       -> "💰 Nạp tiền";
                case BID_HOLD     -> "🔒 Tạm giữ (Đặt giá)";     // Luồng mới
                case BID_RELEASE  -> "🔓 Hủy tạm giữ";           // Luồng mới
                case AUCTION_WIN  -> "🏆 Thanh toán thắng đấu giá";
                case BID_DEDUCT   -> "🔴 Trừ (Luồng cũ)";        // Giữ lại để tương thích dữ liệu cũ
                case BID_REFUND   -> "🔵 Hoàn tiền (Luồng cũ)";  // Giữ lại để tương thích dữ liệu cũ
                default           -> tx.getType().name();
            };
            return new SimpleStringProperty(label);
        });
        colTxAmount.setCellValueFactory(cd ->
            new SimpleStringProperty(formatVnd(cd.getValue().getAmount())));
        colTxBalance.setCellValueFactory(cd ->
            new SimpleStringProperty(formatVnd(cd.getValue().getBalanceAfter())));
        colTxDesc.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getDescription()));
        colTxTime.setCellValueFactory(cd -> {
            String ts = cd.getValue().getCreatedAt() != null
                ? cd.getValue().getCreatedAt().toString().replace("T", " ") : "";
            if (ts.length() > 19) ts = ts.substring(0, 19);
            return new SimpleStringProperty(ts);
        });

        tvTransactions.setItems(txList);
        tvTransactions.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void updateBalanceDisplay(double balance, double availableBalance) {
        if (lblBalance != null) {
            lblBalance.setText(formatVnd(balance));
            // Đổi màu theo mức số dư tổng
            if (balance < 100_000) {
                lblBalance.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 28px;");
            } else if (balance < 1_000_000) {
                lblBalance.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold; -fx-font-size: 28px;");
            } else {
                lblBalance.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-font-size: 28px;");
            }
        }

        // Hiển thị số dư khả dụng
        if (lblAvailableBalance != null) {
            lblAvailableBalance.setText(formatVnd(availableBalance));
        }

        if (lblWalletStatus != null) {
            if (balance < 100_000) {
                lblWalletStatus.setText("⚠️ Số dư thấp – Hãy nạp thêm để tham gia đấu giá");
                lblWalletStatus.setStyle("-fx-text-fill: #e74c3c;");
            } else {
                lblWalletStatus.setText("✅ Ví hoạt động bình thường");
                lblWalletStatus.setStyle("-fx-text-fill: #27ae60;");
            }
        }
    }

    private void loadUserInfo() {
        UserSession s = UserSession.getInstance();
        if (lblUser != null) lblUser.setText(s.getUsername() != null ? s.getUsername() : "Bidder");
        if (lblRole != null) lblRole.setText("Người đặt giá");
    }

    private void showTopUpMsg(String msg, boolean isError) {
        if (lblTopUpMessage == null) return;
        lblTopUpMessage.setText(msg);
        lblTopUpMessage.setStyle(isError
            ? "-fx-text-fill: #e74c3c;"
            : "-fx-text-fill: #27ae60;");
        lblTopUpMessage.setVisible(true);
    }

    private String formatVnd(double amount) {
        return String.format("%,.0f VNĐ", amount);
    }

    @FXML
    private void handleToggleTheme(javafx.event.ActionEvent event) {
        com.auction.client.util.ThemeManager tm =
            com.auction.client.util.ThemeManager.getInstance();
        tm.toggle();
        if (btnTheme != null) btnTheme.setText(tm.getToggleIcon());
    }

}