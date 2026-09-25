package com.finai.analysis;

import lombok.Builder;
import lombok.Value;

/**
 * 会计政策 / 估计变更附注命中。judgement 只反映文本措辞，不代表已经确认变更影响金额。
 */
@Value
@Builder
public class PolicyNote {
    Integer page;
    String snippet;
    String judgement;
}
