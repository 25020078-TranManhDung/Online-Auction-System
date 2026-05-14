package com.auction.client.controller;

import com.auction.client.network.SocketClient;
import com.auction.client.util.AlertUtil;
import com.auction.client.util.ViewLoader;
import com.auction.shared.network.protocol.Actions;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the two-step registration screen.
 *
 * Step 1 – Role selection  (stepRoleSelection VBox is visible)
 *   • User clicks "Register as Bidder" OR "Register as Seller"
 *   → selectedRole is set, UI transitions to Step 2
 *
 * Step 2 – Form entry (stepForm VBox is visible)
 *   • User fills in their details and submits
 *   → Network call is made; on success the login screen is shown
 */
public class RegisterController {

    // ── Step containers ──────────────────────────────────────
    @FXML private VBox stepRoleSelection;
    @FXML private VBox stepForm;

    // ── Step-2 header badge ──────────────────────────────────
    @FXML private Label lblRoleBadge;

    // ── Form fields ──────────────────────────────────────────
    @FXML private TextField     txtFullname;
    @FXML private TextField     txtUsername;
    @FXML private TextField     txtEmail;
    @FXML private PasswordField txtPassword;
    @FXML private PasswordField txtConfirmPassword;

    // Hidden radio buttons kept for compatibility
    @FXML private RadioButton radioBidder;
    @FXML private RadioButton radioSeller;
    @FXML private ToggleGroup roleGroup;

    // ── Other controls ───────────────────────────────────────
    @FXML private Button btnRegister;
    @FXML private Label  lblError;

    /** Role chosen during Step 1. Defaults to BIDDER. */
    private String selectedRole = "BIDDER";

    // ─────────────────────────────────────────────────────────
    //  Initialisation
    // ─────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Start on Step 1
        showStep(1);

        // Hide error label
        if (lblError != null) lblError.setVisible(false);

        // Enter-key navigation through form fields
        if (txtFullname        != null) txtFullname.setOnAction(e -> safeRequestFocus(txtUsername));
        if (txtUsername        != null) txtUsername.setOnAction(e -> safeRequestFocus(txtEmail));
        if (txtEmail           != null) txtEmail.setOnAction(e -> safeRequestFocus(txtPassword));
        if (txtPassword        != null) txtPassword.setOnAction(e -> safeRequestFocus(txtConfirmPassword));
        if (txtConfirmPassword != null && btnRegister != null)
            txtConfirmPassword.setOnAction(e -> btnRegister.fire());
    }

    // ─────────────────────────────────────────────────────────
    //  Step-1 handlers  (role selection)
    // ─────────────────────────────────────────────────────────

    /** Called when user clicks anywhere on the BIDDER card. */
    @FXML
    private void selectBidderAndProceed(MouseEvent event) {
        applyRole("BIDDER");
    }

    /** Called when the "Register as Bidder" button inside the card is pressed. */
    @FXML
    private void selectBidderAndProceedBtn(ActionEvent event) {
        applyRole("BIDDER");
    }

    /** Called when user clicks anywhere on the SELLER card. */
    @FXML
    private void selectSellerAndProceed(MouseEvent event) {
        applyRole("SELLER");
    }

    /** Called when the "Register as Seller" button inside the card is pressed. */
    @FXML
    private void selectSellerAndProceedBtn(ActionEvent event) {
        applyRole("SELLER");
    }

    /** Set role, update UI badges, and advance to Step 2. */
    private void applyRole(String role) {
        selectedRole = role;

        // Sync hidden radio buttons so existing controller logic still works
        if ("SELLER".equals(role)) {
            if (radioSeller != null) radioSeller.setSelected(true);
        } else {
            if (radioBidder != null) radioBidder.setSelected(true);
        }

        // Update badge label and style in Step 2
        if (lblRoleBadge != null) {
            if ("SELLER".equals(role)) {
                lblRoleBadge.setText("🏪  Creating Seller Account");
                lblRoleBadge.getStyleClass().setAll("role-badge-seller");
            } else {
                lblRoleBadge.setText("🛒  Creating Bidder Account");
                lblRoleBadge.getStyleClass().setAll("role-badge-bidder");
            }
        }

        // Update the register button colour to match the role
        if (btnRegister != null) {
            if ("SELLER".equals(role)) {
                btnRegister.getStyleClass().setAll("btn-register-seller");
                btnRegister.setText("CREATE SELLER ACCOUNT");
            } else {
                btnRegister.getStyleClass().setAll("btn-register-bidder");
                btnRegister.setText("CREATE BIDDER ACCOUNT");
            }
        }

        showStep(2);
    }

    // ─────────────────────────────────────────────────────────
    //  Step-2 handlers  (form submission)
    // ─────────────────────────────────────────────────────────

    /**
     * DTO matching the "data" field in PROTOCOL.md ServerResponse for REGISTER.
     */
    public static class RegisterResponseData {
        public String userId;
        public String username;
        public String message;
    }

    /** Called when the CREATE ACCOUNT button is pressed. */
    @FXML
    private void handleRegister(ActionEvent event) {
        if (txtFullname == null || txtUsername == null ||
                txtPassword == null || txtConfirmPassword == null || btnRegister == null) {
            AlertUtil.showError("Error", "Registration form was not initialised correctly. Check FXML.");
            return;
        }

        String fullname         = safeGetText(txtFullname);
        String username         = safeGetText(txtUsername);
        String email            = safeGetText(txtEmail);
        String password         = safeGetText(txtPassword);
        String confirmPassword  = safeGetText(txtConfirmPassword);

        // ── Validation ───────────────────────────────────────
        if (fullname.isEmpty() || username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showInlineError("Please fill in all required fields.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showInlineError("Passwords do not match. Please try again.");
            return;
        }

        if (password.length() < 6) {
            showInlineError("Password must be at least 6 characters long.");
            return;
        }

        // ── Disable button during network call ────────────────
        setButtonLoading(true);

        // ── Background network thread ─────────────────────────
        new Thread(() -> {
            try {
                Map<String, String> requestData = new HashMap<>();
                requestData.put("fullname", fullname);
                requestData.put("username", username);
                requestData.put("password", password);
                requestData.put("email",    email.isEmpty() ? username + "@example.com" : email);
                requestData.put("role",     selectedRole);   // uses the value set in Step 1

                RegisterResponseData response = SocketClient.getInstance().send(
                        Actions.REGISTER,
                        requestData,
                        RegisterResponseData.class
                );

                Platform.runLater(() -> {
                    String msg = (response != null && response.message != null)
                            ? response.message
                            : "Account created successfully!";
                    AlertUtil.showInfo("Success", msg);

                    try {
                        ViewLoader.load(event, "login.fxml", "Sign In");
                    } catch (Exception e) {
                        e.printStackTrace();
                        AlertUtil.showError("Error", "Could not navigate to the login screen.");
                    }
                });

            } catch (RuntimeException e) {
                Platform.runLater(() -> {
                    String msg = e.getMessage() == null ? "Registration failed." : e.getMessage();
                    showInlineError(msg);
                });
            } catch (Exception e) {
                Platform.runLater(() -> showInlineError("An unexpected error occurred: " + e.getMessage()));
                e.printStackTrace();
            } finally {
                Platform.runLater(() -> setButtonLoading(false));
            }
        }).start();
    }

    // ─────────────────────────────────────────────────────────
    //  Navigation
    // ─────────────────────────────────────────────────────────

    /** Called by the "← Change account type" hyperlink in Step 2. */
    @FXML
    private void goBackToRoleSelection(ActionEvent event) {
        clearForm();
        showStep(1);
    }

    /** Called by "Sign in" hyperlinks on both steps. */
    @FXML
    private void goBackToLogin(ActionEvent event) {
        try {
            ViewLoader.load(event, "login.fxml", "Sign In");
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.showError("System Error", "Could not load the login screen.");
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────

    private void showStep(int step) {
        if (stepRoleSelection != null) {
            stepRoleSelection.setVisible(step == 1);
            stepRoleSelection.setManaged(step == 1);
        }
        if (stepForm != null) {
            stepForm.setVisible(step == 2);
            stepForm.setManaged(step == 2);
        }
    }

    private void showInlineError(String message) {
        if (lblError != null) {
            lblError.setText(message);
            lblError.setVisible(true);
        }
    }

    private void setButtonLoading(boolean loading) {
        if (btnRegister == null) return;
        btnRegister.setDisable(loading);
        if (loading) {
            btnRegister.setText("Processing…");
        } else {
            String label = "SELLER".equals(selectedRole) ? "CREATE SELLER ACCOUNT" : "CREATE BIDDER ACCOUNT";
            btnRegister.setText(label);
        }
    }

    private void clearForm() {
        if (txtFullname        != null) txtFullname.clear();
        if (txtUsername        != null) txtUsername.clear();
        if (txtEmail           != null) txtEmail.clear();
        if (txtPassword        != null) txtPassword.clear();
        if (txtConfirmPassword != null) txtConfirmPassword.clear();
        if (lblError           != null) lblError.setVisible(false);
    }

    private void safeRequestFocus(Control control) {
        if (control != null) control.requestFocus();
    }

    private String safeGetText(TextField tf) {
        try { return tf == null || tf.getText() == null ? "" : tf.getText().trim(); }
        catch (Exception e) { return ""; }
    }

    private String safeGetText(PasswordField pf) {
        try { return pf == null || pf.getText() == null ? "" : pf.getText().trim(); }
        catch (Exception e) { return ""; }
    }
}