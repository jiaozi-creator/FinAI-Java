package com.finai.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ChatAnswerFormatterTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ChatAnswerFormatter.Fact profit = new ChatAnswerFormatter.Fact("net_profit", "归母净利润", new BigDecimal("74734071550.75"), "元", "2023A", "consolidated", "VALUE_AVAILABLE");
    @Test void allowsConvertedAmountAndRejectsWrongUnitsSignsAndFabrication() {
        assertThat(ChatAnswerFormatter.matchesValue(profit, "747.34", "亿元")).isTrue();
        assertThat(ChatAnswerFormatter.matchesValue(profit, "747.34", "万元")).isFalse();
        assertThat(ChatAnswerFormatter.matchesValue(profit, "-747.34", "亿元")).isFalse();
        assertThat(ChatAnswerFormatter.matchesValue(profit, "748.34", "亿元")).isFalse();
        var ratio = new ChatAnswerFormatter.Fact("net_margin", "净利率", new BigDecimal("0.50600750"), "%", "2023A", "consolidated", "COMPUTED");
        assertThat(ChatAnswerFormatter.matchesValue(ratio, "50.60", "%")).isTrue();
    }
    @Test void rendersInternalClaimsAsShortChineseAndNeverLeaksJson() {
        var output = ChatAnswerFormatter.render("{\"claims\":[{\"field\":\"net_profit\",\"text\":\"该项反映归母盈利规模\",\"unit\":\"亿元\"}]}", Map.of("net_profit", profit), false, mapper);
        assertThat(output.text()).contains("747.34亿元").doesNotContain("{").hasSizeLessThan(300);
        assertThatThrownBy(() -> ChatAnswerFormatter.render("{\"claims\":[{\"field\":\"net_profit\",\"text\":\"增长99%\"}]}", Map.of("net_profit", profit), false, mapper)).hasMessageContaining("NUMBER_REJECTED");
        assertThatThrownBy(() -> ChatAnswerFormatter.render("", Map.of(), false, mapper)).hasMessage("EMPTY_REPLY");
        assertThatThrownBy(() -> ChatAnswerFormatter.render("{\"coreMetrics\":{}}", Map.of(), false, mapper)).hasMessage("FORMAT_ERROR");
        assertThat(ChatAnswerFormatter.fallback(Map.of("net_profit", profit)).text()).contains("747.34亿元").doesNotContain("{");
    }
}
