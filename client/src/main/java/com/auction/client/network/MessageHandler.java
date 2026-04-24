package com.auction.client.network;

import com.auction.client.observer.AuctionUpdateListener;
import com.auction.client.observer.BidUpdateListener;
import com.auction.shared.network.protocol.Actions;
import com.auction.shared.network.protocol.PushMessage;
import com.auction.shared.network.protocol.ServerResponse;
import com.auction.shared.util.JsonUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import javafx.application.Platform;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class MessageHandler implements Runnable {

    private final InputStream inputStream;
    private final SocketClient client;

    // Listeners đăng ký để nhận push event (Dùng CopyOnWriteArrayList an toàn cho đa luồng)
    private final List<BidUpdateListener> bidListeners = new CopyOnWriteArrayList<>();
    private final List<AuctionUpdateListener> auctionListeners = new CopyOnWriteArrayList<>();

    public MessageHandler(InputStream inputStream, SocketClient client) {
        this.inputStream = inputStream;
        this.client = client;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            // Liên tục lắng nghe dữ liệu từ Server gửi xuống
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                dispatch(line);
            }
        } catch (IOException e) {
            System.out.println("[MessageHandler] Mất kết nối server");
            // Gọi reconnect trên thread riêng, không block luồng reader hiện tại
            new Thread(client::reconnect).start();
        }
    }

    private void dispatch(String json) {
        try {
            // ĐỒNG BỘ 1: Dùng JsonParser của Gson để đọc cấu trúc JSON nhẹ nhàng
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();

            // Phân loại 1: Là thông báo PUSH từ Server
            if (jsonObject.has("type") && "PUSH".equals(jsonObject.get("type").getAsString())) {
                // ĐỒNG BỘ 2: Sử dụng JsonUtil thay cho GSON cục bộ
                PushMessage push = JsonUtil.fromJson(json, PushMessage.class);

                // Truyền cả PushMessage (để lấy event) và JsonObject (để ném cho UI)
                handlePush(push, jsonObject);
            }
            // Phân loại 2: Là câu trả lời (RESPONSE) cho một Request Client vừa gửi
            else if (jsonObject.has("requestId")) {
                // ĐỒNG BỘ 2: Sử dụng JsonUtil
                ServerResponse resp = JsonUtil.fromJson(json, ServerResponse.class);
                client.completeRequest(resp.getRequestId(), resp);
            }
            else {
                System.err.println("[MessageHandler] JSON không rõ format Protocol: " + json);
            }
        } catch (JsonSyntaxException e) {
            System.err.println("[MessageHandler] Lỗi parse JSON từ Server: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[MessageHandler] Lỗi xử lý luồng tin nhắn: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handlePush(PushMessage push, JsonObject rawJsonObject) {
        String event = push.getEvent();

        // ĐỒNG BỘ 3: Dùng hằng số từ file Actions thay vì gõ chữ cứng
        switch (event) {
            case Actions.BID_PLACED, Actions.AUTO_BID_PLACED, Actions.AUTO_BID_FAILED -> {
                // ĐỒNG BỘ 4: Giao diện yêu cầu truyền thẳng JsonObject thay vì DTO
                // Platform.runLater — update UI từ background thread một cách an toàn cho JavaFX
                Platform.runLater(() -> bidListeners.forEach(l -> l.onBidUpdated(rawJsonObject)));
            }

            case Actions.AUCTION_CLOSED, Actions.AUCTION_EXTENDED -> {
                // ĐỒNG BỘ 4: Truyền JsonObject xuống cho UI tự xử lý bóc tách dữ liệu
                Platform.runLater(() -> auctionListeners.forEach(l -> l.onAuctionStatusChanged(rawJsonObject)));
            }

            default -> System.out.println("[MessageHandler] Sự kiện Push chưa được hỗ trợ: " + event);
        }
    }

    // Quản lý các Listener cho UI

    public void addBidListener(BidUpdateListener l) {
        if (!bidListeners.contains(l)) bidListeners.add(l);
    }

    public void removeBidListener(BidUpdateListener l) {
        bidListeners.remove(l);
    }

    public void addAuctionListener(AuctionUpdateListener l) {
        if (!auctionListeners.contains(l)) auctionListeners.add(l);
    }

    public void removeAuctionListener(AuctionUpdateListener l) {
        auctionListeners.remove(l);
    }
}