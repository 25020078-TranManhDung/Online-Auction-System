package com.auction.shared.dto.request;

/**
 * DTO sent from Client → Server for the CHANGE_PASSWORD action.
 *
 * JSON payload shape expected by the server:
 * {
 *   "userId"      : "u-bid-01",
 *   "oldPassword" : "currentSecret",
 *   "newPassword" : "newSecret123"
 * }
 *
 * The server must:
 *  1. Verify that oldPassword matches the stored hash for userId.
 *  2. Hash newPassword and persist it.
 *  3. Return a standard ServerResponse (success/failure + message).
 */
public class ChangePasswordRequest {

    private String userId;

    /** The user's current (old) password in plain-text.
     *  The server hashes and compares this against the stored hash. */
    private String oldPassword;

    /** The desired new password in plain-text.
     *  The server validates complexity, then hashes and stores it. */
    private String newPassword;

    // ── Constructors ─────────────────────────────────────────────

    public ChangePasswordRequest() {}

    public ChangePasswordRequest(String userId, String oldPassword, String newPassword) {
        this.userId      = userId;
        this.oldPassword = oldPassword;
        this.newPassword = newPassword;
    }

    // ── Getters & Setters ────────────────────────────────────────

    public String getUserId()      { return userId;      }
    public String getOldPassword() { return oldPassword; }
    public String getNewPassword() { return newPassword; }

    public void setUserId(String userId)           { this.userId      = userId;      }
    public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }

    @Override
    public String toString() {
        // Never print passwords in logs — intentional omission.
        return "ChangePasswordRequest{userId='" + userId + "'}";
    }
}
