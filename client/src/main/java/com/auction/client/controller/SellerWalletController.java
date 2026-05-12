package com.auction.client.controller;

import com.auction.client.model.UserSession;
import com.auction.client.network.SocketClient;
import com.auction.client.util.AlertUtil;
import com.auction.shared.dto.request.WithdrawRequest;
import com.auction.shared.dto.response.WalletResponse;
import com.auction.shared.model.WalletTransaction;
import com.auction.shared.network.protocol.Actions;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.HashMap;

/**
 * Controller ví doanh thu dành cho Seller.
 * Chức năng:
 *   - Xem số dư doanh thu hiện tại
 *   - Rút tiền (nhập số tiền cụ thể, số dư trừ tương ứng)
 *   - Xem lịch sử: SELLER_RECEIVE (nhận 95%), WITHDRAW (rút)
 */
public class SellerWalletController {

    // ===== Header =====
    @FXML private Label lblUser;
    @FXML private Label lblRole;

    // ===== Thẻ số dư =====
    @FXML private Label lblBalance;
    @FXML private Label lblTotalEarned;     // Tổng đã nhận (toàn lịch sử SELLER_RECEIVE)
    @FXML private Label lblTotalWithdrawn;  // Tổng đã rút

    // ===== Form rút tiền =====
    @FXML private TextField txtWithdrawAmount;
    @FXML private Button btnWithdrawAll;    // Nút rút toàn bộ
    @FXML private Button btnWithdraw50;     // Rút 50%
    @FXML private Button btnConfirmWithdraw;
    @FXML private Label lblWithdrawMessage;
    @FXML private Label lblAvailableBalance; // Hiển thị số dư có thể rút

    // ===== Bảng lịch sử =====
    @FXML private TableView<WalletTransaction> tvTransactions;
    @FXML private TableColumn<WalletTransaction, String> colTxType;
    @FXML private TableColumn<WalletTransaction, String> colTxAmount;
    @FXML private TableColumn<WalletTransaction, String> colTxBalance;
    @FXML private TableColumn<WalletTransaction, String> colTxDesc;
    @FXML private TableColumn<WalletTransaction, String> colTxTime;
    @FXML private Label lblTxCount;

    @FXML private Button btnClose;
    @FXML private Button btnRefresh;

    private double currentBalance = 0;
    private final ObservableList<WalletTransaction> txList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        loadUserInfo();
        setupTable();
        loadWallet();
    }

    // ───────────────────────────────────────────────────────────────
    // Nút phụ trợ điền số tiền rút
    // ───────────────────────────────────────────────────────────────
    @FXML
    void handleWithdrawAll(ActionEvent event) {
        txtWithdrawAmount.setText(String.format("%.0f", currentBalance));
    }

    @FXML
    void handleWithdraw50(ActionEvent event) {
        txtWithdrawAmount.setText(String.format("%.0f", currentBalance * 0.5));
    }

    @FXML
    void handleConfirmWithdraw(ActionEvent event) {
        String input = txtWithdrawAmount.getText();
        if (input == null || input.trim().isEmpty()) {
            showWithdrawMsg("Vui lòng nhập số tiền muốn rút!", true);
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(input.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            showWithdrawMsg("Số tiền không hợp lệ.", true);
            return;
        }
        if (amount <= 0) {
            showWithdrawMsg("Số tiền rút phải lớn hơn 0.", true);
            return;
        }
        if (amount > currentBalance) {
            showWithdrawMsg("Số dư không đủ! Bạn chỉ có " + formatVnd(currentBalance), true);
            return;
        }

        // Xác nhận trước khi rút
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận rút tiền");
        confirm.setHeaderText("Rút " + formatVnd(amount) + " từ ví doanh thu?");
        confirm.setContentText("Số dư còn lại sẽ là: " + formatVnd(currentBalance - amount));
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                doWithdraw(amount);
            }
        });
    }

    @FXML
    void handleRefresh(ActionEvent event) {
        loadWallet();
    }

    @FXML
    void handleClose(ActionEvent event) {
        try {
            Stage stage = (Stage) btnClose.getScene().getWindow();
            stage.close();
        } catch (Exception ignored) {}
    }

    // ───────────────────────────────────────────────────────────────
    // Internal
    // ───────────────────────────────────────────────────────────────
    private void doWithdraw(double amount) {
        btnConfirmWithdraw.setDisable(true);
        btnConfirmWithdraw.setText("Đang rút...");

        new Thread(() -> {
            try {
                WithdrawRequest req = new WithdrawRequest(amount);
                WalletResponse resp = SocketClient.getInstance()
                        .send(Actions.WITHDRAW, req, WalletResponse.class);

                Platform.runLater(() -> {
                    btnConfirmWithdraw.setDisable(false);
                    btnConfirmWithdraw.setText("RÚT TIỀN");
                    if (resp != null) {
                        currentBalance = resp.getBalance();
                        updateBalanceDisplay(resp.getBalance());
                        txtWithdrawAmount.clear();
                        showWithdrawMsg("✅ Rút thành công " + formatVnd(amount)
                                + "! Số dư còn: " + formatVnd(resp.getBalance()), false);
                        loadWallet();
                    } else {
                        showWithdrawMsg("Không nhận được phản hồi từ server.", true);
                    }
                });
            } catch (Exception ex) {
                String msg = ex.getMessage() != null ? ex.getMessage() : "Lỗi không xác định.";
                Platform.runLater(() -> {
                    btnConfirmWithdraw.setDisable(false);
                    btnConfirmWithdraw.setText("RÚT TIỀN");
                    showWithdrawMsg("❌ " + msg, true);
                });
            }
        }, "withdraw-thread").start();
    }

    private void loadWallet() {
        new Thread(() -> {
            try {
                WalletResponse resp = SocketClient.getInstance()
                        .send(Actions.GET_WALLET, new HashMap<>(), WalletResponse.class);

                Platform.runLater(() -> {
                    if (resp != null) {
                        currentBalance = resp.getBalance();
                        updateBalanceDisplay(resp.getBalance());
                        txList.clear();
                        if (resp.getTransactions() != null) {
                            txList.addAll(resp.getTransactions());
                            // Tính tổng doanh thu và tổng rút
                            double totalEarned = resp.getTransactions().stream()
                                    .filter(t -> t.getType() == WalletTransaction.TransactionType.SELLER_RECEIVE)
                                    .mapToDouble(WalletTransaction::getAmount).sum();
                            double totalWithdrawn = resp.getTransactions().stream()
                                    .filter(t -> t.getType() == WalletTransaction.TransactionType.WITHDRAW)
                                    .mapToDouble(WalletTransaction::getAmount).sum();
                            if (lblTotalEarned != null) lblTotalEarned.setText(formatVnd(totalEarned));
                            if (lblTotalWithdrawn != null) lblTotalWithdrawn.setText(formatVnd(totalWithdrawn));
                        }
                        if (lblTxCount != null) lblTxCount.setText(txList.size() + " giao dịch");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> AlertUtil.showError("Lỗi tải ví", e.getMessage()));
            }
        }, "seller-wallet-load").start();
    }

    private void setupTable() {
        colTxType.setCellValueFactory(cd -> {
            String label = switch (cd.getValue().getType()) {
                case SELLER_RECEIVE -> "💵 Nhận doanh thu (95%)";
                case WITHDRAW       -> "🏧 Rút tiền";
                default             -> cd.getValue().getType().name();
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
    }

    private void updateBalanceDisplay(double balance) {
        if (lblBalance != null) {
            lblBalance.setText(formatVnd(balance));
            lblBalance.setStyle(balance > 0
                    ? "-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-font-size: 28px;"
                    : "-fx-text-fill: #7f8c8d; -fx-font-weight: bold; -fx-font-size: 28px;");
        }
        if (lblAvailableBalance != null) {
            lblAvailableBalance.setText("Có thể rút: " + formatVnd(balance));
        }
    }

    private void loadUserInfo() {
        UserSession s = UserSession.getInstance();
        if (lblUser != null) lblUser.setText(s.getUsername() != null ? s.getUsername() : "Seller");
        if (lblRole != null) lblRole.setText("Người bán");
    }

    private void showWithdrawMsg(String msg, boolean isError) {
        if (lblWithdrawMessage == null) return;
        lblWithdrawMessage.setText(msg);
        lblWithdrawMessage.setStyle(isError ? "-fx-text-fill: #e74c3c;" : "-fx-text-fill: #27ae60;");
        lblWithdrawMessage.setVisible(true);
    }

    private String formatVnd(double amount) {
        return String.format("%,.0f VNĐ", amount);
    }
}