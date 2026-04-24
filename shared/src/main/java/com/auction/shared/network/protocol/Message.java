package com.auction.shared.network.protocol;

import com.auction.shared.util.JsonUtil;

public class Message {

    private String action;
    private String token;
    private String requestId;
    private Object data;

    public Message() {}

    public Message(String action, String token, String requestId, Object data) {
        this.action = action;
        this.token = token;
        this.requestId = requestId;
        this.data = data;
    }

    public <T> T getData(Class<T> clazz) {
        return JsonUtil.convertData(this.data, clazz);
    }


    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}