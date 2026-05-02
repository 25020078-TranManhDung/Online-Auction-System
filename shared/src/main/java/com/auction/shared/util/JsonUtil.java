package com.auction.shared.util;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Lớp tiện ích hỗ trợ chuyển đổi dữ liệu giữa Java Object và JSON.
 * Phục vụ cho việc giao tiếp Client - Server qua mạng.
 */
public class JsonUtil {

    // Khởi tạo một instance Gson duy nhất và DẠY NÓ CÁCH ĐỌC LocalDateTime
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
                @Override
                public JsonElement serialize(LocalDateTime src, Type typeOfSrc, JsonSerializationContext context) {
                    // Chuyển LocalDateTime thành chuỗi String chuẩn ISO
                    return new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }
            })
            .registerTypeAdapter(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {
                @Override
                public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                    // Đọc chuỗi String từ mạng và chuyển ngược lại thành LocalDateTime
                    return LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
            })
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

    /**
     * Ép kiểu đối tượng data thô (Object) sang chuẩn DTO mong muốn.
     * Tận dụng JsonTree của Gson.
     */
    public static <T> T convertData(Object rawData, Class<T> clazz) {
        if (rawData == null) return null;
        return gson.fromJson(gson.toJsonTree(rawData), clazz);
    }
}