package com.auction.shared.dto.request;

/**
 * DTO sent from Client → Server for the UPDATE_AVATAR action.
 *
 * JSON payload shape expected by the server:
 * {
 *   "userId"       : "u-bid-01",
 *   "avatarBase64" : "<Base64-encoded JPEG string, or null to remove>"
 * }
 *
 * When {@code avatarBase64} is {@code null} the server interprets the request
 * as "remove avatar" and reverts the user to the default profile picture.
 *
 * using the same resize-to-600px + JPEG-0.80 quality pipeline used throughout
 * the seller dashboard for product images.
 */
public class UpdateAvatarRequest {

    /**
     * The authenticated user's ID, taken from
     */
    private String userId;

    /**
     * Base64-encoded JPEG byte array of the resized/compressed avatar image.
     * Set to {@code null} to signal a "remove avatar" request.
     */
    private String avatarBase64;

    // ── Constructors ──────────────────────────────────────────────────────────

    public UpdateAvatarRequest() {}

    /**
     * Convenience constructor for an upload request.
     *
     * @param userId       the current user's ID
     * @param avatarBase64 Base64 JPEG string produced by ImageUtil, or {@code null}
     *                     to remove the avatar
     */
    public UpdateAvatarRequest(String userId, String avatarBase64) {
        this.userId       = userId;
        this.avatarBase64 = avatarBase64;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getUserId()       { return userId;       }
    public String getAvatarBase64() { return avatarBase64; }

    public void setUserId(String userId)             { this.userId       = userId;       }
    public void setAvatarBase64(String avatarBase64) { this.avatarBase64 = avatarBase64; }

    /** Returns {@code true} when this is a "remove avatar" request. */
    public boolean isRemoveRequest() { return avatarBase64 == null; }

    @Override
    public String toString() {
        // Do not print the full Base64 string — it would flood logs.
        String preview = avatarBase64 == null ? "null"
                : "[" + avatarBase64.length() + " chars]";
        return "UpdateAvatarRequest{userId='" + userId + "', avatarBase64=" + preview + "}";
    }
}
