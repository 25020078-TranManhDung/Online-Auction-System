package com.auction.client;

import javafx.application.Application;

public class Launcher {
    public static void main(String[] args) {
        // Chạy MainApp thông qua một class trung gian để bỏ qua kiểm tra Module-path
        Application.launch(MainApp.class, args);
    }
}