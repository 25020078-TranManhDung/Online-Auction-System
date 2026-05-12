package com.auction.client.controller;

import com.auction.client.model.UserSession;
import com.auction.client.network.SocketClient;
import com.auction.client.util.AlertUtil;
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
 * Controller ví quản trị viên.
 * Chức năng: xem hoa hồng 5% từ các phiên đấu giá thành công.
 * Admin không nạp/rút tiền, chỉ xem doanh thu hoa hồng.
 */
public class AdminWalletController {

    // ===== Header =====
    @FXML private Label lblUser;
    @FXML private Label lblRole;

    // ===== Thẻ số dư =====
    @FXML private Label lblBalance;
    @FXML private Label lblTotalCommission;  // Tổng hoa hồng đã nhận
    @FXML private Label lblAuctionCount;     // Số phiên đấu giá đã thu hoa hồng

    // ===== Bảng lịch sử =====
    @FXML private TableView<WalletTransaction> tvTransactions;
    @FXML private TableColumn<WalletTransaction, String> colTxType;
    @FXML private TableColumn<WalletTransaction, String> colTxAmount;
    @FXML private TableColumn<WalletTransaction, String> colTxBalance;
    @FXML private TableColumn<WalletTransaction, String> colTxAuction;
    @FXML private TableColumn<WalletTransaction, String> colTxTime;
    @FXML private Label lblTxCount;

    @FXML private Button btnClose;
    @FXML private Button btnRefresh;

    private final ObservableList<WalletTransaction> txList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        loadUserInfo();
        setupTable();
        loadWallet();
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
    private void loadWallet() {
        new Thread(() -> {
            try {
                WalletResponse resp = SocketClient.getInstance()
                        .send(Actions.GET_WALLET, new HashMap<>(), WalletResponse.class);

                Platform.runLater(() -> {
                    if (resp != null) {
                        // Số dư
                        if (lblBalance != null) {
                            lblBalance.setText(formatVnd(resp.getBalance()));
                            lblBalance.setStyle("-fx-text-fill: #8e44ad; -fx-font-weight: bold; -fx-font-size: 28px;");
                        }

                        txList.clear();
                        if (resp.getTransactions() != null) {
                            txList.addAll(resp.getTransactions());

                            // Thống kê hoa hồng
                            double totalComm = resp.getTransactions().stream()
                                    .filter(t -> t.getType() == WalletTransaction.TransactionType.COMMISSION)
                                    .mapToDouble(WalletTransaction::getAmount).sum();
                            long auctionCnt = resp.getTransactions().stream()
                                    .filter(t -> t.getType() == WalletTransaction.TransactionType.COMMISSION)
                                    .count();

                            if (lblTotalCommission != null) lblTotalCommission.setText(formatVnd(totalComm));
                            if (lblAuctionCount != null) lblAuctionCount.setText(auctionCnt + " phiên");
                        }
                        if (lblTxCount != null) lblTxCount.setText(txList.size() + " giao dịch");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> AlertUtil.showError("Lỗi tải ví", e.getMessage()));
            }
        }, "admin-wallet-load").start();
    }

    private void setupTable() {
        colTxType.setCellValueFactory(cd ->
                new SimpleStringProperty("💼 Hoa hồng 5%"));
        colTxAmount.setCellValueFactory(cd ->
                new SimpleStringProperty(formatVnd(cd.getValue().getAmount())));
        colTxBalance.setCellValueFactory(cd ->
                new SimpleStringProperty(formatVnd(cd.getValue().getBalanceAfter())));
        colTxAuction.setCellValueFactory(cd -> {
            String aid = cd.getValue().getAuctionId();
            return new SimpleStringProperty(aid != null ? aid : "—");
        });
        colTxTime.setCellValueFactory(cd -> {
            String ts = cd.getValue().getCreatedAt() != null
                    ? cd.getValue().getCreatedAt().toString().replace("T", " ") : "";
            if (ts.length() > 19) ts = ts.substring(0, 19);
            return new SimpleStringProperty(ts);
        });
        tvTransactions.setItems(txList);
    }

    private void loadUserInfo() {
        UserSession s = UserSession.getInstance();
        if (lblUser != null) lblUser.setText(s.getUsername() != null ? s.getUsername() : "Admin");
        if (lblRole != null) lblRole.setText("Quản trị viên");
    }

    private String formatVnd(double amount) {
        return String.format("%,.0f VNĐ", amount);
    }
}