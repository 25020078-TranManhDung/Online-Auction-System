package com.auction.client.controller;

import com.auction.client.util.ViewLoader;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class RegisterController {

    @FXML private TextField regFullname;
    @FXML private TextField regUsername;
    @FXML private PasswordField regPassword;
    @FXML private PasswordField regConfirmPassword;

    @FXML
    private void handleRegister() {
        System.out.println("Đang đăng ký cho: " + regUsername.getText());
    }

    @FXML
    private void goBackToLogin(ActionEvent event) {
        try {
            // Quay lại login.fxml
            ViewLoader.load(event, "login.fxml", "Đăng nhập");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}