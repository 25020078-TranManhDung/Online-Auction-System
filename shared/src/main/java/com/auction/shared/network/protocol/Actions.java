package com.auction.shared.network.protocol;

public final class Actions {
    private Actions() {}

    // Auth
    public static final String LOGIN    = "LOGIN";
    public static final String REGISTER = "REGISTER";
    public static final String LOGOUT   = "LOGOUT";

    // Auction
    public static final String GET_AUCTIONS       = "GET_AUCTIONS";
    public static final String GET_AUCTION_DETAIL = "GET_AUCTION_DETAIL";
    public static final String CREATE_AUCTION     = "CREATE_AUCTION";
    public static final String START_AUCTION      = "START_AUCTION";
    public static final String CLOSE_AUCTION      = "CLOSE_AUCTION";
    public static final String ADMIN_CLOSE_AUCTION = "ADMIN_CLOSE_AUCTION";

    // [MERGE] Giữ MARK_AS_PAID (Admin dùng) + thêm CONFIRM_PAYMENT (Winner tự xác nhận + settle ví)
    public static final String MARK_AS_PAID    = "MARK_AS_PAID";    // Admin xác nhận thanh toán thủ công
    public static final String CONFIRM_PAYMENT = "CONFIRM_PAYMENT"; // Winner xác nhận → trừ tiền Hold, cộng cho Seller

    public static final String CANCEL_AUCTION  = "CANCEL_AUCTION";  // Admin/Seller hủy phiên

    // Bid
    public static final String PLACE_BID       = "PLACE_BID";
    public static final String GET_BID_HISTORY = "GET_BID_HISTORY";
    public static final String GET_ALL_BIDS    = "GET_ALL_BIDS";    // [GIỮ LẠI] Admin: toàn bộ lịch sử đặt giá
    public static final String SET_AUTO_BID    = "SET_AUTO_BID";
    public static final String CANCEL_AUTO_BID = "CANCEL_AUTO_BID";

    // Item
    public static final String CREATE_ITEM = "CREATE_ITEM";
    public static final String GET_ITEM    = "GET_ITEM";
    public static final String UPDATE_ITEM = "UPDATE_ITEM";
    public static final String DELETE_ITEM = "DELETE_ITEM";

    // Admin / User Management
    public static final String GET_ALL_USERS      = "GET_ALL_USERS";
    public static final String TOGGLE_USER_STATUS = "TOGGLE_USER_STATUS";

    // Push events (Server → Client)
    public static final String BID_PLACED             = "BID_PLACED";
    public static final String AUCTION_CLOSED         = "AUCTION_CLOSED";
    public static final String AUCTION_EXTENDED       = "AUCTION_EXTENDED";
    public static final String AUTO_BID_PLACED        = "AUTO_BID_PLACED";
    public static final String AUTO_BID_FAILED        = "AUTO_BID_FAILED";
    public static final String AUCTION_STATUS_CHANGED = "AUCTION_STATUS_CHANGED"; // PAID / CANCELED

    // Wallet
    public static final String GET_WALLET = "GET_WALLET"; // Xem số dư & lịch sử
    public static final String TOP_UP     = "TOP_UP";     // Bidder nạp tiền
    public static final String WITHDRAW   = "WITHDRAW";   // Seller rút tiền
}