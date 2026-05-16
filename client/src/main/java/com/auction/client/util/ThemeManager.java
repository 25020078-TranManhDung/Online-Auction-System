package com.auction.client.util;

import javafx.scene.Scene;
import java.util.prefs.Preferences;

/**
 * Singleton quản lý Light / Dark theme toàn ứng dụng.
 * Lưu preference vào Java Preferences API (registry / ~/.java/...)
 * nên nhớ qua các lần khởi động.
 *
 * Cách dùng:
 *   - MainApp.start(): ThemeManager.getInstance().setScene(scene);
 *   - Mỗi Controller.initialize(): btnTheme.setText(ThemeManager.getInstance().getToggleIcon());
 *   - Nút toggle: ThemeManager.getInstance().toggle(); btnTheme.setText(...getToggleIcon());
 */
public class ThemeManager {

  private static ThemeManager instance;

  private static final String DARK_CSS_PATH =
      "/com/auction/client/css/darkmode.css";  // tên file thực tế (không có dấu gạch ngang)
  private static final String PREF_KEY = "theme_dark";

  private boolean dark;
  private Scene   currentScene;

  private ThemeManager() {
    // Đọc preference đã lưu, mặc định = Light
    Preferences prefs = Preferences.userNodeForPackage(ThemeManager.class);
    dark = prefs.getBoolean(PREF_KEY, false);
  }

  public static synchronized ThemeManager getInstance() {
    if (instance == null) instance = new ThemeManager();
    return instance;
  }

  /**
   * Gọi 1 lần từ MainApp.start() để bind với Scene chính.
   * Tự động áp dụng theme đã lưu ngay khi gọi.
   */
  public void setScene(Scene scene) {
    this.currentScene = scene;
    applyToScene(scene);
  }

  /**
   * Gọi từ nút toggle trên bất kỳ màn hình nào.
   * Toggle trạng thái → lưu preference → áp dụng ngay lên scene hiện tại.
   */
  public void toggle() {
    dark = !dark;
    Preferences.userNodeForPackage(ThemeManager.class)
        .putBoolean(PREF_KEY, dark);
    if (currentScene != null) applyToScene(currentScene);
  }

  /** Trạng thái hiện tại: true = Dark, false = Light. */
  public boolean isDark() { return dark; }

  /**
   * Nạp hoặc gỡ dark-mode.css khỏi scene.
   * Có thể gọi thủ công khi load scene mới (nếu cần).
   */
  public void applyToScene(Scene scene) {
    if (scene == null) return;
    this.currentScene = scene;

    String darkCss;
    try {
      darkCss = getClass().getResource(DARK_CSS_PATH).toExternalForm();
    } catch (Exception e) {
      System.err.println("[ThemeManager] Không tìm thấy dark-mode.css: " + e.getMessage());
      return;
    }

    if (dark) {
      if (!scene.getStylesheets().contains(darkCss))
        scene.getStylesheets().add(darkCss);
    } else {
      scene.getStylesheets().remove(darkCss);
    }
  }

  /**
   * Trả về icon phù hợp để hiện lên nút toggle.
   * Dark đang bật → hiện "☀ Sáng" (để chuyển sang Light).
   * Light đang bật → hiện "🌙 Tối"  (để chuyển sang Dark).
   */
  public String getToggleIcon() {
    return dark ? "☀ Sáng" : "🌙 Tối";
  }
}