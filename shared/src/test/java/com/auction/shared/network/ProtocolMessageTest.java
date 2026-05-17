package com.auction.shared.network;

import com.auction.shared.dto.request.BidRequest;
import com.auction.shared.dto.request.LoginRequest;
import com.auction.shared.dto.response.AuthResponse;
import com.auction.shared.enums.UserRole;
import com.auction.shared.network.protocol.Actions;
import com.auction.shared.network.protocol.Message;
import com.auction.shared.network.protocol.PushMessage;
import com.auction.shared.network.protocol.ServerResponse;
import com.auction.shared.util.JsonUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm tra toàn bộ lớp giao thức mạng:
 *   - Message       (Client → Server request)
 *   - ServerResponse (Server → Client response)
 *   - PushMessage   (Server → Client push event)
 *   - Actions       (hằng số action)
 */
@DisplayName("Protocol Message Tests")
class ProtocolMessageTest {

    // =========================================================
    // Message (Client → Server)
    // =========================================================

    @Nested
    @DisplayName("Message – Client gửi lên Server")
    class MessageTests {

        @Test
        @DisplayName("Constructor rỗng khởi tạo được – cần cho Gson deserialize")
        void defaultConstructor_noException() {
            assertDoesNotThrow(() -> new Message());
        }

        @Test
        @DisplayName("Constructor đầy đủ thiết lập đúng tất cả field")
        void fullConstructor_setsAllFields() {
            LoginRequest data = new LoginRequest("alice", "pass");
            Message msg = new Message(Actions.LOGIN, "token-abc", "req-001", "payload", data);

            assertEquals(Actions.LOGIN, msg.getAction());
            assertEquals("token-abc",  msg.getToken());
            assertEquals("req-001",    msg.getRequestId());
            assertEquals("payload",    msg.getPayload());
            assertEquals(data,         msg.getData());
        }

        @Test
        @DisplayName("Constructor 4 tham số (không payload) thiết lập đúng")
        void constructorWithoutPayload_setsFields() {
            LoginRequest data = new LoginRequest("bob", "secret");
            Message msg = new Message(Actions.LOGIN, "tok", "req-002", data);

            assertEquals(Actions.LOGIN, msg.getAction());
            assertEquals("tok",        msg.getToken());
            assertEquals("req-002",    msg.getRequestId());
            assertNull(msg.getPayload(), "Payload phải null khi dùng constructor 4 tham số");
            assertEquals(data,         msg.getData());
        }

        @Test
        @DisplayName("Setter/Getter hoạt động đúng cho tất cả field")
        void settersAndGetters_workCorrectly() {
            Message msg = new Message();
            msg.setAction(Actions.PLACE_BID);
            msg.setToken("my-token");
            msg.setRequestId("req-999");
            msg.setData(new BidRequest("a1", "b1", 500_000.0, false));

            assertEquals(Actions.PLACE_BID, msg.getAction());
            assertEquals("my-token",       msg.getToken());
            assertEquals("req-999",        msg.getRequestId());
            assertNotNull(msg.getData());
        }

        @Test
        @DisplayName("getData(Class) phải ép kiểu từ Object thô sang DTO đúng")
        void getData_withClassArg_convertsToDto() {
            LoginRequest original = new LoginRequest("charlie", "pw123");
            // Mô phỏng round-trip JSON qua mạng: serialize → deserialize → getData
            String json = JsonUtil.toJson(new Message(Actions.LOGIN, null, "req-10", original));
            Message received = JsonUtil.fromJson(json, Message.class);

            LoginRequest extracted = received.getData(LoginRequest.class);

            assertNotNull(extracted);
            assertEquals("charlie", extracted.getUsername());
            assertEquals("pw123",   extracted.getPassword());
        }

        @Test
        @DisplayName("Message có thể được serialize/deserialize qua JSON")
        void message_jsonRoundTrip_isConsistent() {
            BidRequest bid = new BidRequest("auction-5", "bidder-9", 1_200_000.0, false);
            Message original = new Message(Actions.PLACE_BID, "tok-xyz", "req-42", bid);

            String json     = JsonUtil.toJson(original);
            Message restored = JsonUtil.fromJson(json, Message.class);

            assertNotNull(restored);
            assertEquals(original.getAction(),    restored.getAction());
            assertEquals(original.getToken(),     restored.getToken());
            assertEquals(original.getRequestId(), restored.getRequestId());
        }

        @Test
        @DisplayName("Message không cần token (anonymous request) vẫn hợp lệ")
        void message_nullToken_isValid() {
            Message msg = new Message(Actions.REGISTER, null, "req-reg", new LoginRequest());
            assertNull(msg.getToken());
            assertEquals(Actions.REGISTER, msg.getAction());
        }
    }

    // =========================================================
    // ServerResponse (Server → Client)
    // =========================================================

    @Nested
    @DisplayName("ServerResponse – Server phản hồi Client")
    class ServerResponseTests {

        @Test
        @DisplayName("ok() tạo response thành công với data đúng")
        void ok_createsSuccessResponse() {
            AuthResponse authData = new AuthResponse("user-1", "alice", UserRole.BIDDER, "tok-123");
            ServerResponse response = ServerResponse.ok("req-001", authData);

            assertTrue(response.isSuccess(),        "isSuccess phải true");
            assertEquals("req-001", response.getRequestId());
            assertNotNull(response.getData(),        "data không được null");
            assertNull(response.getError(),          "error phải null khi success");
        }

        @Test
        @DisplayName("fail() tạo response lỗi với error payload đúng")
        void fail_createsErrorResponse() {
            ServerResponse response = ServerResponse.fail("req-002", "AUTH_FAILED", "Sai mật khẩu");

            assertFalse(response.isSuccess(),             "isSuccess phải false");
            assertEquals("req-002", response.getRequestId());
            assertNull(response.getData(),                "data phải null khi fail");
            assertNotNull(response.getError(),            "error không được null");
            assertEquals("AUTH_FAILED", response.getError().getCode());
            assertEquals("Sai mật khẩu", response.getError().getMessage());
        }

        @Test
        @DisplayName("getData(Class) phải ép kiểu từ raw object sang DTO đúng")
        void getData_withClassArg_convertsToDto() {
            AuthResponse original = new AuthResponse("u1", "bob", UserRole.SELLER, "t1");
            ServerResponse response = ServerResponse.ok("req-003", original);

            // Round-trip qua JSON (mô phỏng truyền qua mạng)
            String json = JsonUtil.toJson(response);
            ServerResponse received = JsonUtil.fromJson(json, ServerResponse.class);

            AuthResponse extracted = received.getData(AuthResponse.class);

            assertNotNull(extracted);
            assertEquals("u1",           extracted.getUserId());
            assertEquals("t1",           extracted.getToken());
            assertEquals(UserRole.SELLER, extracted.getRole());
        }

        @Test
        @DisplayName("Constructor rỗng tồn tại – cần cho Gson deserialize")
        void defaultConstructor_noException() {
            assertDoesNotThrow(() -> new ServerResponse());
        }

        @Test
        @DisplayName("Setters và Getters hoạt động đúng")
        void settersAndGetters_workCorrectly() {
            ServerResponse response = new ServerResponse();
            response.setRequestId("req-set");
            response.setSuccess(true);
            response.setData("some data");

            assertEquals("req-set", response.getRequestId());
            assertTrue(response.isSuccess());
            assertEquals("some data", response.getData());
        }

        @Test
        @DisplayName("ErrorPayload giữ đúng code và message")
        void errorPayload_storesCodeAndMessage() {
            ServerResponse.ErrorPayload err = new ServerResponse.ErrorPayload("NOT_FOUND", "Không tìm thấy");

            assertEquals("NOT_FOUND",   err.getCode());
            assertEquals("Không tìm thấy", err.getMessage());
        }

        @Test
        @DisplayName("ErrorPayload setter/getter hoạt động đúng")
        void errorPayload_settersAndGetters() {
            ServerResponse.ErrorPayload err = new ServerResponse.ErrorPayload();
            err.setCode("BID_TOO_LOW");
            err.setMessage("Giá đấu thấp hơn giá hiện tại");

            assertEquals("BID_TOO_LOW", err.getCode());
            assertEquals("Giá đấu thấp hơn giá hiện tại", err.getMessage());
        }

        @Test
        @DisplayName("ServerResponse round-trip JSON giữ nguyên trạng thái success/fail")
        void serverResponse_jsonRoundTrip_preservesSuccessState() {
            ServerResponse original = ServerResponse.fail("req-rt", "CLOSED", "Phiên đã đóng");
            String json = JsonUtil.toJson(original);
            ServerResponse restored = JsonUtil.fromJson(json, ServerResponse.class);

            assertFalse(restored.isSuccess());
            assertEquals("req-rt",     restored.getRequestId());
            assertEquals("CLOSED",     restored.getError().getCode());
            assertEquals("Phiên đã đóng", restored.getError().getMessage());
        }
    }

    // =========================================================
    // PushMessage (Server → Client broadcast)
    // =========================================================

    @Nested
    @DisplayName("PushMessage – Server broadcast đến tất cả Client")
    class PushMessageTests {

        @Test
        @DisplayName("Constructor rỗng tồn tại – cần cho Gson deserialize")
        void defaultConstructor_noException() {
            assertDoesNotThrow(() -> new PushMessage());
        }

        @Test
        @DisplayName("Constructor (event, data) thiết lập đúng field")
        void constructor_withEventAndData_setsFields() {
            PushMessage push = new PushMessage(Actions.BID_PLACED, "some-bid-data");

            assertEquals("PUSH",            push.getType(),  "type phải luôn là PUSH");
            assertEquals(Actions.BID_PLACED, push.getEvent());
            assertEquals("some-bid-data",   push.getData());
        }

        @Test
        @DisplayName("type phải mặc định là 'PUSH'")
        void type_defaultValueIsPush() {
            PushMessage push = new PushMessage();
            assertEquals("PUSH", push.getType());
        }

        @Test
        @DisplayName("Setters và Getters hoạt động đúng")
        void settersAndGetters_workCorrectly() {
            PushMessage push = new PushMessage();
            push.setEvent(Actions.AUCTION_CLOSED);
            push.setData("auction-data");
            push.setType("PUSH");

            assertEquals(Actions.AUCTION_CLOSED, push.getEvent());
            assertEquals("auction-data",         push.getData());
            assertEquals("PUSH",                 push.getType());
        }

        @Test
        @DisplayName("getData(Class) phải ép kiểu từ raw object sang DTO đúng")
        void getData_withClassArg_convertsToDto() {
            BidRequest bid = new BidRequest("auction-push", "bidder-push", 750_000.0, false);
            PushMessage push = new PushMessage(Actions.BID_PLACED, bid);

            // Round-trip JSON mô phỏng truyền qua mạng
            String json   = JsonUtil.toJson(push);
            PushMessage received = JsonUtil.fromJson(json, PushMessage.class);

            BidRequest extracted = received.getData(BidRequest.class);

            assertNotNull(extracted);
            assertEquals("auction-push", extracted.getAuctionId());
            assertEquals(750_000.0,      extracted.getAmount(), 0.001);
        }

        @Test
        @DisplayName("PushMessage round-trip JSON giữ nguyên event và type")
        void pushMessage_jsonRoundTrip_preservesEventAndType() {
            PushMessage original = new PushMessage(Actions.AUCTION_EXTENDED, "extend-data");
            String json     = JsonUtil.toJson(original);
            PushMessage restored = JsonUtil.fromJson(json, PushMessage.class);

            assertEquals("PUSH",                   restored.getType());
            assertEquals(Actions.AUCTION_EXTENDED,  restored.getEvent());
        }

        @Test
        @DisplayName("Server có thể tạo push event AUCTION_CLOSED và Client nhận đúng")
        void scenario_serverSendsAuctionClosedEvent_clientReceivesCorrectly() {
            // Server side
            String auctionId = "auction-closed-001";
            PushMessage push = new PushMessage(Actions.AUCTION_CLOSED, auctionId);
            String json = JsonUtil.toJson(push);

            // Client side – nhận và đọc
            PushMessage received = JsonUtil.fromJson(json, PushMessage.class);

            assertNotNull(received);
            assertEquals("PUSH",                 received.getType());
            assertEquals(Actions.AUCTION_CLOSED,  received.getEvent());
            String receivedId = received.getData(String.class);
            assertEquals(auctionId, receivedId);
        }
    }

    // =========================================================
    // Actions – hằng số action
    // =========================================================

    @Nested
    @DisplayName("Actions – Hằng số giao thức")
    class ActionsTests {

        @Test
        @DisplayName("Các action Auth phải có giá trị đúng")
        void authActions_haveCorrectValues() {
            assertEquals("LOGIN",    Actions.LOGIN);
            assertEquals("REGISTER", Actions.REGISTER);
            assertEquals("LOGOUT",   Actions.LOGOUT);
        }

        @Test
        @DisplayName("Các action Auction phải có giá trị đúng")
        void auctionActions_haveCorrectValues() {
            assertEquals("GET_AUCTIONS",        Actions.GET_AUCTIONS);
            assertEquals("GET_AUCTION_DETAIL",  Actions.GET_AUCTION_DETAIL);
            assertEquals("CREATE_AUCTION",      Actions.CREATE_AUCTION);
            assertEquals("PLACE_BID",           Actions.PLACE_BID);
            assertEquals("GET_BID_HISTORY",     Actions.GET_BID_HISTORY);
        }

        @Test
        @DisplayName("Các Push event phải có giá trị đúng")
        void pushEvents_haveCorrectValues() {
            assertEquals("BID_PLACED",              Actions.BID_PLACED);
            assertEquals("AUCTION_CLOSED",          Actions.AUCTION_CLOSED);
            assertEquals("AUCTION_EXTENDED",        Actions.AUCTION_EXTENDED);
            assertEquals("AUCTION_STATUS_CHANGED",  Actions.AUCTION_STATUS_CHANGED);
        }

        @Test
        @DisplayName("Actions là utility class – constructor private không thể gọi ngoài")
        void actions_isUtilityClass_constructorIsPrivate() throws NoSuchMethodException {
            var ctor = Actions.class.getDeclaredConstructor();
            assertFalse(ctor.canAccess(null), "Constructor phải private");
        }

        @Test
        @DisplayName("Tất cả action String phải không null và không rỗng")
        void allActions_areNonNullAndNonEmpty() {
            String[] actions = {
                    Actions.LOGIN, Actions.REGISTER, Actions.LOGOUT,
                    Actions.GET_AUCTIONS, Actions.CREATE_AUCTION, Actions.UPDATE_AUCTION,
                    Actions.START_AUCTION, Actions.CLOSE_AUCTION, Actions.CANCEL_AUCTION,
                    Actions.PLACE_BID, Actions.GET_BID_HISTORY, Actions.GET_ALL_BIDS,
                    Actions.SET_AUTO_BID, Actions.CANCEL_AUTO_BID,
                    Actions.CREATE_ITEM, Actions.GET_ITEM, Actions.UPDATE_ITEM, Actions.DELETE_ITEM,
                    Actions.GET_ALL_USERS, Actions.TOGGLE_USER_STATUS, Actions.BAN_USER,
                    Actions.GET_WALLET, Actions.TOP_UP, Actions.WITHDRAW,
                    Actions.BID_PLACED, Actions.AUCTION_CLOSED, Actions.AUCTION_EXTENDED,
                    Actions.AUCTION_STATUS_CHANGED, Actions.ACCOUNT_LOCKED
            };

            for (String action : actions) {
                assertNotNull(action,          "Action không được null: " + action);
                assertFalse(action.isBlank(),  "Action không được rỗng: " + action);
            }
        }

        @Test
        @DisplayName("Action strings không có khoảng trắng thừa")
        void allActions_noExtraWhitespace() {
            String[] actions = {
                    Actions.LOGIN, Actions.REGISTER, Actions.PLACE_BID,
                    Actions.BID_PLACED, Actions.AUCTION_CLOSED
            };
            for (String action : actions) {
                assertEquals(action.trim(), action, "Action không được có khoảng trắng: '" + action + "'");
            }
        }
    }

    // =========================================================
    // Integration scenario: luồng đặt giá end-to-end
    // =========================================================

    @Nested
    @DisplayName("Integration – Luồng đấu giá end-to-end")
    class IntegrationTests {

        @Test
        @DisplayName("Client gửi PLACE_BID → Server phản hồi ok → Server broadcast BID_PLACED")
        void scenario_placeBidFullFlow() {
            // 1. Client tạo Message và serialize
            BidRequest bidReq = new BidRequest("auction-E2E", "bidder-E2E", 2_000_000.0, false);
            Message clientMsg = new Message(Actions.PLACE_BID, "valid-token", "req-E2E", bidReq);
            String clientJson = JsonUtil.toJson(clientMsg);

            // 2. Server nhận và parse
            Message received = JsonUtil.fromJson(clientJson, Message.class);
            assertEquals(Actions.PLACE_BID, received.getAction());
            BidRequest parsedBid = received.getData(BidRequest.class);
            assertEquals("auction-E2E", parsedBid.getAuctionId());
            assertEquals(2_000_000.0,   parsedBid.getAmount(), 0.001);

            // 3. Server tạo response ok
            ServerResponse response = ServerResponse.ok("req-E2E", "Đặt giá thành công");
            String responseJson = JsonUtil.toJson(response);
            ServerResponse clientReceived = JsonUtil.fromJson(responseJson, ServerResponse.class);
            assertTrue(clientReceived.isSuccess());

            // 4. Server broadcast push đến tất cả client
            PushMessage push = new PushMessage(Actions.BID_PLACED, parsedBid);
            String pushJson  = JsonUtil.toJson(push);
            PushMessage allClientsReceived = JsonUtil.fromJson(pushJson, PushMessage.class);
            assertEquals("PUSH",             allClientsReceived.getType());
            assertEquals(Actions.BID_PLACED, allClientsReceived.getEvent());
            BidRequest broadcastBid = allClientsReceived.getData(BidRequest.class);
            assertEquals("auction-E2E", broadcastBid.getAuctionId());
        }

        @Test
        @DisplayName("Server từ chối bid không hợp lệ → Client nhận ServerResponse fail")
        void scenario_invalidBid_serverReturnsFail() {
            // Server từ chối
            ServerResponse failResp = ServerResponse.fail("req-low", "BID_TOO_LOW",
                    "Giá đặt phải cao hơn giá hiện tại");
            String json = JsonUtil.toJson(failResp);

            // Client nhận
            ServerResponse received = JsonUtil.fromJson(json, ServerResponse.class);

            assertFalse(received.isSuccess());
            assertNotNull(received.getError());
            assertEquals("BID_TOO_LOW", received.getError().getCode());
        }
    }
}