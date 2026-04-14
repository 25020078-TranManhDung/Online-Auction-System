package com.auction.shared.network.protocol;

import com.google.gson.Gson;

public class Message {
    private static final Gson GSON = new Gson();

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
        if (this.data == null) return null;
        return GSON.fromJson(GSON.toJsonTree(this.data), clazz);
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
