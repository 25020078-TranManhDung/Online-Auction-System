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
    public static final String CREATE_AUCTION      = "CREATE_AUCTION";
    public static final String START_AUCTION       = "START_AUCTION";
    public static final String CLOSE_AUCTION       = "CLOSE_AUCTION";

    // Bid
    public static final String PLACE_BID        = "PLACE_BID";
    public static final String GET_BID_HISTORY  = "GET_BID_HISTORY";
    public static final String SET_AUTO_BID     = "SET_AUTO_BID";
    public static final String CANCEL_AUTO_BID  = "CANCEL_AUTO_BID";

    // Item
    public static final String CREATE_ITEM = "CREATE_ITEM";
    public static final String GET_ITEM    = "GET_ITEM";
    public static final String UPDATE_ITEM = "UPDATE_ITEM";
    public static final String DELETE_ITEM = "DELETE_ITEM";

    // Push events
    public static final String BID_PLACED        = "BID_PLACED";
    public static final String AUCTION_CLOSED     = "AUCTION_CLOSED";
    public static final String AUCTION_EXTENDED   = "AUCTION_EXTENDED";
    public static final String AUTO_BID_PLACED    = "AUTO_BID_PLACED";
    public static final String AUTO_BID_FAILED    = "AUTO_BID_FAILED";
}
