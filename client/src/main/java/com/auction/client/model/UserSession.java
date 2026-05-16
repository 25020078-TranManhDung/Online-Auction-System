package com.auction.client.model;

/**
 * Singleton holding the authenticated user's session data.
 *
 * Fields added (v2):
 *   - fullName  : display name shown in the profile dropdown
 *   - email     : shown in the profile dropdown
 *
 * LoginController must pass these when calling initSession().
 */
public class UserSession {

    private static UserSession instance;

    private String userId;
    private String username;
    private String fullName;   // ← NEW
    private String email;      // ← NEW
    private String token;
    private String role;
    private String expiresAt;
    private String avatarBase64;

    private UserSession() {}

    public static UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    // ------------------------------------------------------------------
    //  Initialise / clear
    // ------------------------------------------------------------------

    /**
     * Original 5-parameter signature kept for backward compatibility.
     * fullName and email will be empty until the overloaded version is called.
     */
    public void initSession(String userId, String username,
                            String token, String role, String expiresAt) {
        initSession(userId, username, null, null, token, role, expiresAt);
    }

    /**
     * Full 7-parameter signature used after the login payload is updated
     * to include fullName and email.
     */
    public void initSession(String userId, String username,
                            String fullName, String email,
                            String token, String role, String expiresAt) {
        this.userId    = userId;
        this.username  = username;
        this.fullName  = fullName  != null ? fullName  : username; // fallback to username
        this.email     = email     != null ? email     : "";
        this.token     = token;
        this.role      = role;
        this.expiresAt = expiresAt;
    }

    public void cleanUserSession() {
        this.userId    = null;
        this.username  = null;
        this.fullName  = null;
        this.email     = null;
        this.token     = null;
        this.role      = null;
        this.expiresAt = null;
    }

    // ------------------------------------------------------------------
    //  Getters
    // ------------------------------------------------------------------

    public String getUserId()    { return userId;    }
    public String getUsername()  { return username;  }
    public String getFullName()  { return fullName  != null ? fullName  : username; }
    public String getEmail()     { return email     != null ? email     : "";       }
    public String getToken()     { return token;     }
    public String getRole()      { return role;      }
    public String getExpiresAt() { return expiresAt; }

    public String getAvatarBase64() {
        return avatarBase64;
    }

    public void setAvatarBase64(String avatarBase64) {
        this.avatarBase64 = avatarBase64;
    }
}