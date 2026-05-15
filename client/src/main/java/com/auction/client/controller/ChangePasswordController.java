package com.auction.client.controller;

import com.auction.client.model.UserSession;
import com.auction.client.network.SocketClient;
import com.auction.client.util.AlertUtil;
import com.auction.shared.dto.request.ChangePasswordRequest;
import com.auction.shared.network.protocol.Actions;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;
import java.util.Map;

/**
 * Controller for change-password.fxml.
 *
 * Opened as a new window via:
 *   ViewLoader.openInNewWindow("change-password.fxml", "Đổi mật khẩu")
 *
 * Flow:
 *   1. User fills in oldPassword, newPassword, confirmPassword.
 *   2. Client-side validation runs before any network call.
 *   3. A {@link ChangePasswordRequest} DTO is sent to the server via SocketClient.
 *   4. On success  → show inline success feedback, then close window after 1.5 s.
 *   5. On failure  → show inline error feedback, re-enable the form.
 */
public class ChangePasswordController {

    // ── FXML controls ─────────────────────────────────────────────────────────
    @FXML private PasswordField txtOldPassword;
    @FXML private PasswordField txtNewPassword;
    @FXML private PasswordField txtConfirmPassword;

    @FXML private Label  lblFeedback;
    @FXML private Button btnConfirm;
    @FXML private Button btnCancel;

    // ── Inline style constants (the window is outside the main CSS scope) ──────
    private static final String STYLE_ERROR =
            "-fx-font-size: 12.5px; -fx-padding: 10 28 0 28; -fx-text-alignment: center;" +
                    "-fx-text-fill: #e74c3c;";

    private static final String STYLE_SUCCESS =
            "-fx-font-size: 12.5px; -fx-padding: 10 28 0 28; -fx-text-alignment: center;" +
                    "-fx-text-fill: #2ecc71;";

    // ── Hover styles applied programmatically ─────────────────────────────────
    private static final String STYLE_BTN_CONFIRM_DEFAULT =
            "-fx-background-color: linear-gradient(to right, #6c3483, #8e44ad);" +
                    "-fx-text-fill: white; -fx-font-size: 13.5px; -fx-font-weight: bold;" +
                    "-fx-background-radius: 10; -fx-border-radius: 10; -fx-cursor: hand;" +
                    "-fx-padding: 10 20;" +
                    "-fx-effect: dropshadow(gaussian, rgba(142,68,173,0.55), 10, 0, 0, 3);";

    private static final String STYLE_BTN_CONFIRM_HOVER =
            "-fx-background-color: linear-gradient(to right, #7d3c98, #9b59b6);" +
                    "-fx-text-fill: white; -fx-font-size: 13.5px; -fx-font-weight: bold;" +
                    "-fx-background-radius: 10; -fx-border-radius: 10; -fx-cursor: hand;" +
                    "-fx-padding: 10 20;" +
                    "-fx-effect: dropshadow(gaussian, rgba(142,68,173,0.80), 14, 0, 0, 4);";

    private static final String STYLE_BTN_CANCEL_DEFAULT =
            "-fx-background-color: rgba(255,255,255,0.08);" +
                    "-fx-text-fill: rgba(176,136,230,0.90); -fx-font-size: 13.5px; -fx-font-weight: bold;" +
                    "-fx-background-radius: 10; -fx-border-color: rgba(155,89,182,0.40);" +
                    "-fx-border-width: 1.5; -fx-border-radius: 10; -fx-cursor: hand; -fx-padding: 10 20;";

    private static final String STYLE_BTN_CANCEL_HOVER =
            "-fx-background-color: rgba(155,89,182,0.18);" +
                    "-fx-text-fill: #d2b4de; -fx-font-size: 13.5px; -fx-font-weight: bold;" +
                    "-fx-background-radius: 10; -fx-border-color: rgba(155,89,182,0.65);" +
                    "-fx-border-width: 1.5; -fx-border-radius: 10; -fx-cursor: hand; -fx-padding: 10 20;";

    // ==========================================================================
    //  INITIALISATION
    // ==========================================================================

    @FXML
    public void initialize() {
        // ── Enter-key navigation through fields ──────────────────────────────
        txtOldPassword.setOnAction(e -> txtNewPassword.requestFocus());
        txtNewPassword.setOnAction(e -> txtConfirmPassword.requestFocus());
        txtConfirmPassword.setOnAction(e -> btnConfirm.fire());

        // ── Live feedback: clear error as soon as the user edits any field ───
        txtOldPassword.textProperty().addListener((o, prev, cur) -> hideFeedback());
        txtNewPassword.textProperty().addListener((o, prev, cur) -> hideFeedback());
        txtConfirmPassword.textProperty().addListener((o, prev, cur) -> hideFeedback());

        // ── Button hover effects (programmatic — window has no stylesheet) ───
        attachHoverEffect(btnConfirm, STYLE_BTN_CONFIRM_DEFAULT, STYLE_BTN_CONFIRM_HOVER);
        attachHoverEffect(btnCancel,  STYLE_BTN_CANCEL_DEFAULT,  STYLE_BTN_CANCEL_HOVER);
    }

    // ==========================================================================
    //  HANDLERS
    // ==========================================================================

    /**
     * Validates input, builds the DTO, and sends the request to the server
     * on a background thread so the UI never freezes.
     */
    @FXML
    private void handleConfirm(ActionEvent event) {
        String oldPw      = safeText(txtOldPassword);
        String newPw      = safeText(txtNewPassword);
        String confirmPw  = safeText(txtConfirmPassword);

        // ── 1. Client-side validation ─────────────────────────────────────────
        if (oldPw.isEmpty() || newPw.isEmpty() || confirmPw.isEmpty()) {
            showError("Vui lòng điền đầy đủ tất cả các trường.");
            return;
        }

        if (newPw.length() < 6) {
            showError("Mật khẩu mới phải có ít nhất 6 ký tự.");
            txtNewPassword.requestFocus();
            return;
        }

        if (!newPw.equals(confirmPw)) {
            showError("Mật khẩu xác nhận không trùng khớp.");
            txtConfirmPassword.requestFocus();
            return;
        }

        if (newPw.equals(oldPw)) {
            showError("Mật khẩu mới không được trùng với mật khẩu hiện tại.");
            txtNewPassword.requestFocus();
            return;
        }

        // ── 2. Lock UI during network call ────────────────────────────────────
        setFormLocked(true);

        // ── 3. Build DTO ──────────────────────────────────────────────────────
        String userId = UserSession.getInstance().getUserId();
        ChangePasswordRequest request = new ChangePasswordRequest(userId, oldPw, newPw);

        // ── 4. Background network thread ──────────────────────────────────────
        new Thread(() -> {
            try {
                /*
                 * SocketClient.send() is expected to:
                 *   - Wrap the DTO in a standard ClientRequest envelope.
                 *   - Throw a RuntimeException whose getMessage() contains the
                 *     server's error description on failure.
                 *   - Return a response object on success (we only need the
                 *     message field, so String.class is sufficient here).
                 *
                 * Adjust the return type / Actions constant to match your
                 * actual server protocol if needed.
                 */
                Map responseMap = SocketClient.getInstance().send(
                        Actions.CHANGE_PASSWORD,
                        request,
                        Map.class
                );

                // Lấy câu thông báo từ key "message" trong Map
                String serverMessage = (responseMap != null && responseMap.containsKey("message"))
                        ? String.valueOf(responseMap.get("message"))
                        : null;

                // ── 5a. Success ───────────────────────────────────────────────
                Platform.runLater(() -> {
                    String msg = (serverMessage != null && !serverMessage.isBlank())
                            ? serverMessage
                            : "Đổi mật khẩu thành công!";
                    showSuccess("✅  " + msg);

                    // Re-enable the form but disable Confirm so user can't
                    // re-submit the same request accidentally.
                    setFormLocked(false);
                    btnConfirm.setDisable(true);

                    // Auto-close after 1.8 seconds so the user can read the message.
                    closeAfterDelay(1800);
                });

            } catch (RuntimeException ex) {
                // ── 5b. Server-side failure (wrong old password, etc.) ────────
                Platform.runLater(() -> {
                    String msg = (ex.getMessage() != null && !ex.getMessage().isBlank())
                            ? ex.getMessage()
                            : "Đổi mật khẩu thất bại. Vui lòng thử lại.";
                    showError(msg);
                    setFormLocked(false);
                });

            } catch (Exception ex) {
                // ── 5c. Network / unexpected error ────────────────────────────
                ex.printStackTrace();
                Platform.runLater(() -> {
                    showError("Lỗi kết nối: " + ex.getMessage());
                    setFormLocked(false);
                });
            }
        }).start();
    }

    /** Closes the window without making any server call. */
    @FXML
    private void handleCancel(ActionEvent event) {
        closeWindow();
    }

    // ==========================================================================
    //  UI HELPERS
    // ==========================================================================

    /** Shows an error message in the inline feedback label. */
    private void showError(String message) {
        lblFeedback.setStyle(STYLE_ERROR);
        lblFeedback.setText(message);
        lblFeedback.setVisible(true);
        lblFeedback.setManaged(true);
    }

    /** Shows a success message in the inline feedback label. */
    private void showSuccess(String message) {
        lblFeedback.setStyle(STYLE_SUCCESS);
        lblFeedback.setText(message);
        lblFeedback.setVisible(true);
        lblFeedback.setManaged(true);
    }

    /** Hides the inline feedback label (called on any keystroke). */
    private void hideFeedback() {
        lblFeedback.setVisible(false);
        lblFeedback.setManaged(false);
    }

    /**
     * Disables / re-enables the entire form during a network call.
     * The Confirm button text changes to give a loading indication.
     */
    private void setFormLocked(boolean locked) {
        txtOldPassword.setDisable(locked);
        txtNewPassword.setDisable(locked);
        txtConfirmPassword.setDisable(locked);
        btnCancel.setDisable(locked);
        btnConfirm.setDisable(locked);
        btnConfirm.setText(locked ? "Đang xử lý…" : "🔐  Xác nhận");
    }

    /** Attaches mouse-enter / mouse-exit style handlers to a Button. */
    private void attachHoverEffect(Button btn, String defaultStyle, String hoverStyle) {
        btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle));
        btn.setOnMouseExited(e  -> btn.setStyle(defaultStyle));
    }

    /**
     * Closes this modal window.
     * Works whether the root is a StackPane (modal) or any other Node.
     */
    private void closeWindow() {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }

    /**
     * Schedules the window to close after {@code delayMs} milliseconds.
     * Uses a daemon thread so it never blocks JVM shutdown.
     */
    private void closeAfterDelay(long delayMs) {
        Thread closer = new Thread(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            Platform.runLater(this::closeWindow);
        });
        closer.setDaemon(true);
        closer.start();
    }

    /** Null-safe trimmed text from a PasswordField. */
    private String safeText(PasswordField pf) {
        try {
            return pf == null || pf.getText() == null ? "" : pf.getText().trim();
        } catch (Exception e) {
            return "";
        }
    }
}
