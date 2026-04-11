package com.auction.shared.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Lớp tiện ích hỗ trợ chuyển đổi dữ liệu giữa Java Object và JSON.
 * Phục vụ cho việc giao tiếp Client - Server qua mạng.
 */
public class JsonUtil {

    // Khởi tạo một instance Gson duy nhất (Singleton pattern cơ bản) để tái sử dụng
    private static final Gson gson = new GsonBuilder()
            .create();

    private JsonUtil() {
        throw new UnsupportedOperationException("Utility class không được khởi tạo");
    }

    /**
     * Chuyển đổi một đối tượng Java thành chuỗi JSON.
     * * @param object Đối tượng cần chuyển đổi
     */
    public static String toJson(Object object) {
        if (object == null) {
            return null;
        }
        return gson.toJson(object);
    }

    /**
     * Chuyển đổi một chuỗi JSON thành đối tượng Java.
     * * @param json  Chuỗi JSON nhận được từ Socket/REST
     * @param clazz Class của đối tượng đích
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return gson.fromJson(json, clazz);
    }
}
