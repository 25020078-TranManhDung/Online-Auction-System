package com.auction.shared.network.protocol;

import com.google.gson.Gson;

public class PushMessage {

    // TỐI ƯU HÓA: Dùng chung một đối tượng Gson tĩnh cho toàn bộ ứng dụng
    private static final Gson GSON = new Gson();

    private String type = "PUSH";
    private String event;
    private Object data;


    public PushMessage() {}

    // Constructor tiện ích cho Server dễ dàng tạo thông báo
    public PushMessage(String event, Object data) {
        this.event = event;
        this.data = data;
    }

    // Hàm ma thuật được viết bằng logic của Gson
    public <T> T getData(Class<T> clazz) {
        if (this.data == null) return null;
        // Gson biến Object thành JsonTree, sau đó ép kiểu sang DTO một cách an toàn và nhẹ nhàng
        return GSON.fromJson(GSON.toJsonTree(this.data), clazz);
    }


    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
