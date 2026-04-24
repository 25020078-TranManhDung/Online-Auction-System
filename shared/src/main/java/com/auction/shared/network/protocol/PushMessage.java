package com.auction.shared.network.protocol;

import com.auction.shared.util.JsonUtil;

public class PushMessage {

    // ĐÃ XÓA: Dùng chung một đối tượng Gson tĩnh cục bộ, thay bằng JsonUtil tập trung

    private String type = "PUSH";
    private String event;
    private Object data;


    public PushMessage() {}

    // Constructor tiện ích cho Server dễ dàng tạo thông báo
    public PushMessage(String event, Object data) {
        this.event = event;
        this.data = data;
    }

    // Hàm ma thuật được viết bằng logic của Gson (Chuyển xử lý sang JsonUtil)
    public <T> T getData(Class<T> clazz) {
        // JsonUtil biến Object thành JsonTree, sau đó ép kiểu sang DTO một cách an toàn và nhẹ nhàng
        return JsonUtil.convertData(this.data, clazz);
    }


    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}