package com.cmbc.mds.forex.quotes.receiver.impl;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import com.cmbc.mds.forex.provider.service.ForeignBankConnectionService;
import com.cmbc.mds.forex.quotes.QuoteRoutingContext;
import com.cmbc.mds.forex.quotes.adapter.QuoteAdapterRouter;
import com.cmbc.mds.forex.quotes.dto.CmdsQuotePayload;
import com.cmbc.mds.forex.subscription.core.SubscriptionCoreService;
import com.cmbc.mds.forex.subscription.core.model.topic.MarketDataTopic;
import com.cmbc.mds.monitor.QuotePerformanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jms.annotation.JmsListener;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class CmdsMqQuoteReceiverTest {

    /**
     * 测试语义：验证 CMDS 接收器绑定到独立 MQ 队列配置。
     * 关注重点：CMDS 不走外资行通用队列，source/provider 身份由专属队列固定为 CMDS。
     */
    @Test
    @DisplayName("CMDS-RECEIVER-01 监听器应绑定 CMDS 专属队列")
    void listenerIsBoundToDedicatedCmdsQueue() throws Exception {
        Method method = CmdsMqQuoteReceiver.class.getMethod("onMessage", jakarta.jms.Message.class);
        JmsListener listener = method.getAnnotation(JmsListener.class);

        assertThat(listener).isNotNull();
        assertThat(listener.destination()).isEqualTo("${ibm.mq.queue.cmds}");
    }

    /**
     * 测试语义：验证 CMDS JSON 文本一次反序列化为 DTO 后，以固定 source/provider 路由到 Adapter。
     * 关注重点：路由 payload 必须是 CmdsQuotePayload，确保接收链路不再创建 JsonNode 中间树。
     */
    @Test
    @DisplayName("CMDS-RECEIVER-02 有效 CMDS 行情应以 DTO 路由")
    void routesQuoteWithFixedCmdsSourceAndProvider() {
        TestContext context = createContext();

        context.receiver.processMessage(cmdsQuoteJson("\"exnm\": \"USDCNY\","));

        assertThat(context.connectionService.source).isEqualTo(BaseConstants.SERVICE_NAME_CMDS);
        assertThat(context.subscriptionService.topicKey)
                .isEqualTo(MarketDataTopic.buildTopicKey("CMDS", "CMDS", "USD/CNY"));
        assertThat(context.router.source).isEqualTo("CMDS");
        assertThat(context.router.provider).isEqualTo("CMDS");
        assertThat(context.router.payload).isInstanceOf(CmdsQuotePayload.class);
        assertThat(((CmdsQuotePayload) context.router.payload).getExnm()).isEqualTo("USDCNY");
    }

    /**
     * 测试语义：验证 CMDS 有效 DTO 在没有下游订阅时，会停在接收器的订阅前置过滤阶段。
     * 关注重点：即使已经完成一次 DTO 绑定，也不应再进入 Adapter 转换和 clean 队列，保护无订阅行情的低成本丢弃路径。
     */
    @Test
    @DisplayName("CMDS-RECEIVER-03 无订阅 CMDS 行情不应进入 Adapter")
    void skipsSubscribedCheckFailedQuoteBeforeAdapter() {
        TestContext context = createContext(false);

        context.receiver.processMessage(cmdsQuoteJson("\"exnm\": \"USDCNY\","));

        assertThat(context.connectionService.source).isEqualTo(BaseConstants.SERVICE_NAME_CMDS);
        assertThat(context.subscriptionService.topicKey)
                .isEqualTo(MarketDataTopic.buildTopicKey("CMDS", "CMDS", "USD/CNY"));
        assertThat(context.router.payload).isNull();
        assertThat(context.router.source).isNull();
        assertThat(context.router.provider).isNull();
    }

    /**
     * 测试语义：验证 CMDS 报文中的误导性 serviceId 不参与 source/provider 判定，交易对仅按 exnm 规范化。
     * 关注重点：CMDS 来源身份由队列绑定决定，不能被报文字段覆盖，否则会路由到错误 Adapter 或订阅主题。
     */
    @Test
    @DisplayName("CMDS-RECEIVER-04 忽略报文 serviceId 并规范化 exnm")
    void ignoresMisleadingServiceIdAndNormalizesExnm() {
        TestContext context = createContext();

        context.receiver.processMessage(cmdsQuoteJson("""
                "serviceId": "GS-FIX",
                "exnm": "EURUSD",
                """));

        assertThat(context.subscriptionService.topicKey)
                .isEqualTo(MarketDataTopic.buildTopicKey("CMDS", "CMDS", "EUR/USD"));
        assertThat(context.router.source).isEqualTo("CMDS");
        assertThat(context.router.provider).isEqualTo("CMDS");
    }

    /**
     * 测试语义：验证缺少 gradsPrices 的 CMDS 报文在接收预检阶段直接丢弃。
     * 关注重点：无价格档位时不进入订阅校验和 Adapter，避免为无效行情产生后续对象和队列事件。
     */
    @Test
    @DisplayName("CMDS-RECEIVER-05 缺少 gradsPrices 时应丢弃")
    void skipsQuoteWithoutGradsPrices() {
        TestContext context = createContext();

        context.receiver.processMessage("""
                {
                  "exnm": "USDCNY",
                  "quoteType": "QDM"
                }
                """);

        assertThat(context.subscriptionService.topicKey).isNull();
        assertThat(context.router.payload).isNull();
    }

    /**
     * 测试语义：验证缺少 exnm 的 CMDS 报文无法构建 clean topic，应在接收预检阶段丢弃。
     * 关注重点：symbol 是订阅过滤和队列 key 的必要字段，缺失时不得继续路由。
     */
    @Test
    @DisplayName("CMDS-RECEIVER-06 缺少 exnm 时应丢弃")
    void skipsQuoteWithoutSymbol() {
        TestContext context = createContext();

        context.receiver.processMessage(cmdsQuoteJson(""));

        assertThat(context.subscriptionService.topicKey).isNull();
        assertThat(context.router.payload).isNull();
    }

    private static TestContext createContext() {
        return createContext(true);
    }

    private static TestContext createContext(boolean hasSubscribers) {
        CmdsMqQuoteReceiver receiver = new CmdsMqQuoteReceiver();
        CapturingRouter router = new CapturingRouter();
        CapturingSubscriptionService subscriptionService = new CapturingSubscriptionService(hasSubscribers);
        CapturingConnectionService connectionService = new CapturingConnectionService();

        inject(receiver, "objectMapper", new ObjectMapper());
        inject(receiver, "quoteAdapterRouter", router);
        inject(receiver, "subscriptionCoreService", subscriptionService);
        inject(receiver, "foreignBankConnectionService", connectionService);
        inject(receiver, "quotePerformanceService", new QuotePerformanceService());

        return new TestContext(receiver, router, subscriptionService, connectionService);
    }

    private static String cmdsQuoteJson(String identifyingFields) {
        return """
                {
                  %s
                  "quoteType": "QDM",
                  "prcd": "M0501",
                  "gradsPrices": [
                    {
                      "bid": "7.1000",
                      "bidSize": "1000000",
                      "ask": "7.2000",
                      "askSize": "1000000"
                    }
                  ]
                }
                """.formatted(identifyingFields);
    }

    private static void inject(Object target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to inject field " + fieldName, e);
        }
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private record TestContext(
            CmdsMqQuoteReceiver receiver,
            CapturingRouter router,
            CapturingSubscriptionService subscriptionService,
            CapturingConnectionService connectionService) {
    }

    private static class CapturingRouter extends QuoteAdapterRouter {
        private Object payload;
        private String source;
        private String provider;

        @Override
        public void route(Object payload, String source, String provider) {
            this.payload = payload;
            this.source = source;
            this.provider = provider;
        }

        @Override
        public void route(Object payload, QuoteRoutingContext context) {
            this.payload = payload;
            this.source = context.source();
            this.provider = context.provider();
        }
    }

    private static class CapturingSubscriptionService extends SubscriptionCoreService {
        private final boolean hasSubscribers;
        private String topicKey;

        private CapturingSubscriptionService(boolean hasSubscribers) {
            this.hasSubscribers = hasSubscribers;
        }

        @Override
        public boolean hasSubscribers(String topicKey) {
            this.topicKey = topicKey;
            return hasSubscribers;
        }
    }

    private static class CapturingConnectionService extends ForeignBankConnectionService {
        private String source;

        @Override
        public void refreshLastActive(String source, boolean connectedFlag) {
            this.source = source;
        }
    }
}
