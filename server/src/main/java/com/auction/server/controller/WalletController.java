package com.auction.server.controller;

import com.auction.server.service.WalletService;
import com.auction.shared.dto.request.TopUpRequest;
import com.auction.shared.dto.request.WithdrawRequest;
import com.auction.shared.dto.response.WalletResponse;
import com.auction.shared.network.protocol.Message;
import com.auction.shared.network.protocol.ServerResponse;

/**
 * WalletController – nhận Message từ MessageRouter và ủy thác cho WalletService.
 */
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    /** GET_WALLET: Xem số dư và lịch sử giao dịch */
    public ServerResponse getWallet(Message msg) {
        WalletResponse resp = walletService.getWallet(msg.getToken());
        return ServerResponse.ok(msg.getRequestId(), resp);
    }

    /** TOP_UP: Bidder nạp tiền vào ví */
    public ServerResponse topUp(Message msg) {
        TopUpRequest req = msg.getData(TopUpRequest.class);
        WalletResponse resp = walletService.topUp(req, msg.getToken());
        return ServerResponse.ok(msg.getRequestId(), resp);
    }

    /** WITHDRAW: Seller rút tiền từ ví doanh thu */
    public ServerResponse withdraw(Message msg) {
        WithdrawRequest req = msg.getData(WithdrawRequest.class);
        WalletResponse resp = walletService.withdraw(req, msg.getToken());
        return ServerResponse.ok(msg.getRequestId(), resp);
    }
}