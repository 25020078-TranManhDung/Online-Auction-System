package com.auction.shared.network.protocol;

import com.auction.shared.util.JsonUtil;

public class ServerResponse {
    // Tối ưu hóa: Đã chuyển việc cấu hình Gson về class dùng chung là JsonUtil

    private String requestId;
    private boolean success;
    private Object data;
    private ErrorPayload error;

    public ServerResponse() {}

    public static ServerResponse ok(String reqId, Object data) {
        ServerResponse r = new ServerResponse();
        r.requestId = reqId;
        r.success = true;
        r.data = data;
        r.error = null;
        return r;
    }

    public static ServerResponse fail(String reqId, String code, String msg) {
        ServerResponse r = new ServerResponse();
        r.requestId = reqId;
        r.success = false;
        r.data = null;
        r.error = new ErrorPayload(code, msg);
        return r;
    }

    // Hàm ép kiểu dùng JsonUtil
    public <T> T getData(Class<T> clazz) {
        return JsonUtil.convertData(this.data, clazz);
    }


    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
    public ErrorPayload getError() { return error; }
    public void setError(ErrorPayload error) { this.error = error; }

    public static class ErrorPayload {
        private String code;
        private String message;
        public ErrorPayload() {}
        public ErrorPayload(String code, String message) {
            this.code = code;
            this.message = message;
        }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}