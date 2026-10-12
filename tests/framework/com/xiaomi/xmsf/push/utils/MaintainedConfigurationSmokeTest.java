package com.xiaomi.xmsf.push.utils;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import android.text.TextUtils;
import com.elvishew.xlog.XLog;
import com.xiaomi.xmpush.thrift.*;
import org.json.JSONObject;
import org.junit.*;
import org.mockito.MockedStatic;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/** Runs synthetic messages through the actual Framework Configurations/Lisp/Walker. */
public class MaintainedConfigurationSmokeTest {
    private static final String QQ = "com.tencent.mobileqq";
    private Configurations configs;
    private MockedStatic<TextUtils> textUtils;

    @Before public void loadBundle() throws Exception {
        XLog.init();
        textUtils = mockStatic(TextUtils.class);
        textUtils.when(() -> TextUtils.isEmpty(any())).thenAnswer(invocation -> {
            CharSequence text = invocation.getArgument(0);
            return text == null || text.length() == 0;
        });
        configs = Configurations.getInstance();
        Path directory = Paths.get(System.getProperty("mipush.configDirectory"));
        try (java.util.stream.Stream<Path> paths = Files.list(directory)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".json")).sorted().collect(Collectors.toList())) {
                configs.load(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            }
        }
    }
    @After public void closeMocks() { if (textUtils != null) textUtils.close(); }

    private XmPushActionContainer message(String pkg, String title, String body, String intent) throws Exception {
        PushMessage content = new PushMessage().setId("synthetic-message").setAppId("synthetic-app").setPayload("synthetic body");
        XmPushActionSendMessage action = new XmPushActionSendMessage().setId("synthetic-send").setAppId("synthetic-app").setMessage(content);
        XmPushActionContainer container = new XmPushActionContainer();
        container.setPackageName(pkg).setAction(ActionType.SendMessage).setEncryptAction(false);
        container.setPushAction(ByteBuffer.wrap(XmPushThriftSerializeUtils.convertThriftObjectToBytes(action)));
        container.metaInfo = new PushMetaInfo().setId("synthetic-meta").setTitle(title).setDescription(body).setNotifyId(123);
        container.metaInfo.extra = new HashMap<>();
        // Dummy value prevents Android credential lookup; this fixture is unencrypted.
        container.metaInfo.extra.put("__reg_sec__", "synthetic-not-a-credential");
        if (intent != null) {
            container.metaInfo.extra.put("intent_uri", intent);
            container.metaInfo.extra.put("notify_effect", "2");
        }
        return container;
    }
    private String intent(int type, String id) {
        return "intent:#Intent;S.jumptype=" + type + ";S.fromuin=" + id + (type == 57619 ? ";S.groupcode=90001" : "") + ";end";
    }
    private Set<String> handle(XmPushActionContainer c) throws Exception { return configs.handle(c.packageName, c); }
    private void enable(String name, String pkg) throws Exception {
        JSONObject rule = new JSONObject().put("match", new JSONObject().put("packageName", "^" + java.util.regex.Pattern.quote(pkg) + "$" )).put("stop", false);
        configs.load(new JSONObject().put("version", "0.1.0").put("configs", new JSONObject().put(name, new org.json.JSONArray().put(rule))).toString());
    }
    private void noIntrusiveEffects(XmPushActionContainer c, Set<String> ops) {
        assertFalse(ops.contains("ignore")); assertFalse(ops.contains("wake")); assertFalse(ops.contains("open"));
        assertFalse(c.metaInfo.extra.containsKey("__awake"));
    }

    @Test public void allNineDependenciesResolveInActualLoader() { assertTrue(configs.getUnresolvedReferences().isEmpty()); }

    @Test public void friendKeepsOriginalIntentAndGetsHttpsAvatarAndConversation() throws Exception {
        String uri = intent(57618, "10001");
        XmPushActionContainer c = message(QQ, "Alice", "Hello", uri);
        noIntrusiveEffects(c, handle(c));
        assertEquals(uri, c.metaInfo.extra.get("intent_uri"));
        assertEquals("personal", c.metaInfo.extra.get("__mipush_tag_char_type"));
        assertEquals("contact_chat", c.metaInfo.extra.get("channel_id"));
        assertTrue(c.metaInfo.extra.containsKey("__mi_push_use_messaging_style"));
        assertEquals("https://q.qlogo.cn/g?b=qq&s=100&nk=10001", c.metaInfo.extra.get("__mi_push_conversation_sender_icon"));
        assertFalse(c.metaInfo.extra.containsKey("__mi_push_borrow_channel_id"));
    }
    @Test public void groupVoiceDoesNotBecomePersonalAndSupportsUnicodeColonAndMultiline() throws Exception {
        XmPushActionContainer c = message(QQ, "Test group", "Alice：[语音消息]\nsecond line", intent(57619, "10002"));
        noIntrusiveEffects(c, handle(c));
        assertEquals("group", c.metaInfo.extra.get("__mipush_tag_char_type"));
        assertEquals("group_chat", c.metaInfo.extra.get("channel_id"));
        assertEquals("Test group", c.metaInfo.extra.get("__mi_push_conversation_title"));
        assertEquals("Alice", c.metaInfo.extra.get("__mi_push_conversation_sender"));
        assertTrue(c.metaInfo.extra.get("__mi_push_conversation_message").contains("second line"));
        assertEquals("Test group", c.metaInfo.title);
    }
    @Test public void unknownMarketingMessageIsNotTurnedIntoFriendConversationOrFiltered() throws Exception {
        XmPushActionContainer c = message(QQ, "News", "Update", null);
        c.metaInfo.extra.put("channel_id", "105584");
        noIntrusiveEffects(c, handle(c));
        assertFalse(c.metaInfo.extra.containsKey("__mipush_tag_char_type"));
        assertFalse(c.metaInfo.extra.containsKey("__mi_push_use_messaging_style"));
    }
    @Test public void partialOrWildcardJumpTypeDoesNotMatchAConversation() throws Exception {
        for (String value : Arrays.asList("Sxjumptype=57618", "S.jumptype=576180", "S.jumptype=57619extra")) {
            XmPushActionContainer c = message(QQ, "News", "Update", "intent:#Intent;" + value + ";end");
            handle(c);
            assertFalse(value, c.metaInfo.extra.containsKey("__mipush_tag_char_type"));
        }
    }
    @Test public void sameDisplayNameDoesNotMergeDifferentFriendIdentities() throws Exception {
        XmPushActionContainer a = message(QQ, "Same name", "One", intent(57618, "10001"));
        XmPushActionContainer b = message(QQ, "Same name", "Two", intent(57618, "10002"));
        handle(a); handle(b);
        assertNotEquals(a.metaInfo.notifyId, b.metaInfo.notifyId);
        assertNotEquals(a.metaInfo.extra.get("__mi_push_conversation_id"), b.metaInfo.extra.get("__mi_push_conversation_id"));
    }
    @Test public void sameNameVoiceMessagesAlsoKeepDistinctFriendIdentities() throws Exception {
        XmPushActionContainer a = message(QQ, "Same name", "[语音消息]", intent(57618, "10001"));
        XmPushActionContainer b = message(QQ, "Same name", "[文件]", intent(57618, "10002"));
        handle(a); handle(b); assertNotEquals(a.metaInfo.notifyId, b.metaInfo.notifyId);
    }
    @Test public void previouslyMarkedGroupStillGetsItsChannelAndGroupType() throws Exception {
        XmPushActionContainer c = message(QQ, "Test group", "Alice: Hello", intent(57619, "10002"));
        c.metaInfo.extra.put("__mipush_tag_icon", "1");
        handle(c); assertEquals("group_chat", c.metaInfo.extra.get("channel_id"));
        assertEquals("group", c.metaInfo.extra.get("__mipush_tag_char_type"));
    }
    @Test public void specialFollowKeepsImportanceWithoutWakingScreen() throws Exception {
        XmPushActionContainer c = message(QQ, "[特别关心]Alice", "Hello", intent(57618, "10001"));
        noIntrusiveEffects(c, handle(c));
        assertEquals("speacial_follow", c.metaInfo.extra.get("channel_id"));
        assertTrue(c.metaInfo.extra.containsKey("__mi_push_conversation_important"));
    }
    @Test public void groupAllMembersFilterRequiresExplicitOptInAndDoesNotFilterFriends() throws Exception {
        XmPushActionContainer group = message(QQ, "Test group", "Alice: @全体成员 update", intent(57619, "10002"));
        assertFalse(handle(group).contains("ignore"));
        enable("开关-QQ全体消息过滤", QQ);
        assertTrue(handle(message(QQ, "Test group", "Alice: @全体成员 update", intent(57619, "10002"))).contains("ignore"));
        assertFalse(handle(message(QQ, "Alice", "@全体成员 update", intent(57618, "10001"))).contains("ignore"));
    }
    @Test public void optionalChannelReuseIsEffectiveOnlyWhenEnabled() throws Exception {
        enable("开关-QQ渠道复用", QQ);
        XmPushActionContainer c = message(QQ, "Alice", "Hello", intent(57618, "10001"));
        handle(c); assertEquals("QQ_Friend", c.metaInfo.extra.get("__mi_push_borrow_channel_id"));
    }
    @Test public void optionalNicknameSimplificationPreservesBodyAndOnlyChangesDelimitedGroupName() throws Exception {
        enable("开关-QQ昵称简化", QQ);
        XmPushActionContainer c = message(QQ, "Test group", " Alice ｜Team: Hello", intent(57619, "10002"));
        handle(c);
        assertEquals("Alice", c.metaInfo.extra.get("__mi_push_conversation_sender"));
        assertTrue(c.metaInfo.extra.get("__mi_push_conversation_message").contains("Hello"));
    }
    @Test public void optionalIntentRewriteRequiresExplicitOptIn() throws Exception {
        enable("开关-QQ意图重整", QQ);
        XmPushActionContainer c = message(QQ, "Alice", "Hello", intent(57618, "10001"));
        handle(c); assertEquals("mqqwpa://im/chat?chat_type=c2c&open_type=chat&uin=10001", c.metaInfo.extra.get("intent_uri"));
    }
    @Test public void optionalAwakeFlagOnlyAffectsWhitelistedPackage() throws Exception {
        enable("开关-后台唤醒应用", "com.example.selected");
        XmPushActionContainer selected = message("com.example.selected", "Update", "Body", null);
        handle(selected); assertEquals("true", selected.metaInfo.extra.get("__awake"));
        XmPushActionContainer other = message("com.example.other", "Update", "Body", null);
        noIntrusiveEffects(other, handle(other));
    }
    @Test public void corporateCallDoesNotOpenApplicationByDefault() throws Exception {
        XmPushActionContainer c = message("com.tencent.wework", "Call", "邀请你进行语音通话", null);
        noIntrusiveEffects(c, handle(c));
        assertEquals("voice_chat", c.metaInfo.extra.get("channel_id"));
    }
    @Test public void noGlobalGroupOrDirectClickOverrideByDefault() throws Exception {
        XmPushActionContainer c = message("com.example.app", "Update", "Body", null);
        noIntrusiveEffects(c, handle(c));
        assertFalse(c.metaInfo.extra.containsKey("notification_group"));
        assertFalse(c.metaInfo.extra.containsKey("use_clicked_activity"));
    }
    @Test public void globalOptionalRulesHaveRealEffectsOnlyForSelectedPackage() throws Exception {
        String selected = "com.example.selected";
        enable("开关-同标题会话", selected);
        XmPushActionContainer title = message(selected, "Same title", "Body", null);
        handle(title); assertEquals("Same title".hashCode(), title.metaInfo.notifyId);
        enable("开关-只显示一条消息", selected);
        XmPushActionContainer one = message(selected, "Other title", "Body", null);
        handle(one); assertEquals(selected.hashCode(), one.metaInfo.notifyId);
        enable("开关-直接打开意图", selected);
        XmPushActionContainer click = message(selected, "Click", "Body", "example://message");
        handle(click); assertEquals("true", click.metaInfo.extra.get("use_clicked_activity"));
        enable("开关-过滤运营消息", selected);
        assertTrue(handle(message(selected, "Promotion", "Body", null)).contains("ignore"));
        XmPushActionContainer other = message("com.example.other", "Promotion", "Body", null);
        noIntrusiveEffects(other, handle(other));
        assertEquals(123, other.metaInfo.notifyId);
        assertFalse(other.metaInfo.extra.containsKey("use_clicked_activity"));
    }
    @Test public void nativeGroupFallbackWorksWhenMessagingAndClassicFormattingAreNotSelected() throws Exception {
        configs.load("{\"version\":\"0.1.0\",\"configs\":{\"QQ-MessagingStyle\":[],\"群消息整形-小米经典样式\":[]}}");
        XmPushActionContainer c = message(QQ, "Test group", "Alice：Hello", intent(57619, "10002"));
        handle(c);
        assertEquals("Alice", c.metaInfo.title);
        assertEquals("Hello", c.metaInfo.description);
        assertEquals("Test group", c.metaInfo.extra.get("__mi_push_sub_text"));
        assertFalse(c.metaInfo.extra.containsKey("__mi_push_use_messaging_style"));
    }
}
