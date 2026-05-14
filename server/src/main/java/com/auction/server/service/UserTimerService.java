package com.auction.server.service;

import com.auction.server.dao.UserDAO;
import com.auction.shared.model.user.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Background job: scan mỗi 60 giây, tự động mở khoá tài khoản TEMP_LOCKED
 * khi lockedUntil <= now (hết thời gian khoá).
 * Pattern tương tự AuctionTimerService.
 */
public class UserTimerService {

  private final UserDAO userDao;
  private ScheduledExecutorService scheduler;

  public UserTimerService(UserDAO userDao) {
    this.userDao = userDao;
  }

  public void start() {
    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "User-Timer-Thread");
      t.setDaemon(true);
      return t;
    });

    // Quét mỗi 60 giây
    scheduler.scheduleAtFixedRate(
        this::checkExpiredLocks,
        0, 60, TimeUnit.SECONDS
    );

    System.out.println("✅ UserTimerService đã khởi động (Quét mỗi 60s để tự động mở khoá).");
  }

  public void stop() {
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdown();
      System.out.println("🛑 UserTimerService đã dừng.");
    }
  }

  private void checkExpiredLocks() {
    try {
      LocalDateTime now = LocalDateTime.now();

      // Lấy tất cả TEMP_LOCKED có lockedUntil <= now
      List<User> toUnlock = userDao.findAll().stream()
          .filter(u -> "TEMP_LOCKED".equalsIgnoreCase(u.getStatus()))
          .filter(u -> u.getLockedUntil() != null && !u.getLockedUntil().isAfter(now))
          .collect(Collectors.toList());

      for (User user : toUnlock) {
        try {
          user.setStatus("ACTIVE");
          user.setLockedUntil(null);
          userDao.update(user);
          System.out.println("[UserTimerService] Tự động mở khoá: " + user.getUsername()
              + " (lockedUntil=" + user.getLockedUntil() + ")");
        } catch (Exception e) {
          System.err.println("[UserTimerService] Lỗi mở khoá user ["
              + user.getId() + "]: " + e.getMessage());
        }
      }
    } catch (Exception e) {
      System.err.println("[UserTimerService] Lỗi nghiêm trọng: " + e.getMessage());
    }
  }
}