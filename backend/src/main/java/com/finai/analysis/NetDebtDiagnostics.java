package com.finai.analysis;

import com.finai.model.dto.FinancialMetricsDTO.NonRecurringItem;
import com.finai.model.dto.ValuationResultDTO;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 现金/债务诊断独立于 FCFF 和权益桥接；两种 DCF 输入模式使用同一套证据门禁。 */
final class NetDebtDiagnostics {
    private NetDebtDiagnostics() {}

    static ValuationResultDTO attach(ValuationResultDTO result, Map<String, ExtractedLine> fields, ValuationParameters p) {
        List<String> missing = new ArrayList<>();
        List<NonRecurringItem> inputs = new ArrayList<>();
        for (String id : List.of("cash", "restricted_cash", "accrued_deposit_interest", "cash_equivalents", "restricted_interbank",
                "short_term_debt", "current_portion_debt", "bonds_payable", "lease_liabilities", "long_term_debt")) {
            ExtractedLine line = fields.get(id);
            if (line != null) inputs.add(NonRecurringItem.builder().fieldId(id).name(line.getFieldName()).amount(line.getCurrent())
                    .dataQualityStatus(line.getDataQualityStatus()).period(line.getAsOfDate()).scope(line.getScope()).unit(line.getUnit())
                    .sourceFile(line.getSourceFile()).page(line.getPage()).snippet(line.getSnippet()).build());
        }
        result.setNetDebtDiagnosticInputs(inputs);
        // 手工现金/债务也须有确认政策及各自来源，不能仅凭两个金额跳过证据校验。
        if (p != null && p.isBridgePolicyConfirmed() && !blank(p.getBridgePolicy()) && p.getBridgeSources() != null
                && !blank(p.getBridgeSources().get("usableCash")) && !blank(p.getBridgeSources().get("grossDebt"))
                && nonnegative(p.getUsableCash()) && nonnegative(p.getGrossDebt())) {
            result.setNetDebtDiagnostic(p.getGrossDebt().subtract(p.getUsableCash()));
            result.setNetDebtDiagnosticFormula("确认有息债务 − 确认可用现金；来源及范围按确认版本 " + p.getVersion());
            result.setNetDebtDiagnosticStatus("COMPUTED");
            result.setNetDebtDiagnosticMissingItems(List.of());
            return result;
        }
        ExtractedLine cash = fields.get("cash");
        check("货币资金", cash, cash, missing);
        ExtractedLine restricted = fields.get("restricted_cash");
        check("受限资金", restricted, cash, missing);
        BigDecimal debt = BigDecimal.ZERO;
        for (String id : List.of("short_term_debt", "current_portion_debt", "bonds_payable", "lease_liabilities", "long_term_debt")) {
            ExtractedLine line = fields.get(id);
            check(id, line, cash, missing);
            if (line != null && line.getCurrent() != null) debt = debt.add(line.getCurrent());
        }
        ExtractedLine interest = fields.get("accrued_deposit_interest");
        if (interest != null) check("应计定期存款利息", interest, cash, missing);
        String formula = "狭义诊断：借款、到期债务、债券及租赁负债合计 −（货币资金 − 受限资金";
        formula += interest == null ? "）；应计存款利息未取得，不推定为零，现金可用性政策待确认" : " − 已披露应计存款利息）";
        result.setNetDebtDiagnosticFormula(formula + "；同业存款属拆出资金不重复扣减；不代表正式股权桥接");
        if (missing.isEmpty()) {
            BigDecimal usableCash = cash.getCurrent().subtract(restricted.getCurrent());
            if (interest != null) usableCash = usableCash.subtract(interest.getCurrent());
            if (usableCash.signum() < 0) missing.add("受限资金及应计利息超过货币资金，现金调节不成立");
            else result.setNetDebtDiagnostic(debt.subtract(usableCash));
        }
        result.setNetDebtDiagnosticStatus(missing.isEmpty() ? "COMPUTED" : "INPUT_INCOMPLETE");
        result.setNetDebtDiagnosticMissingItems(missing);
        return result;
    }

    private static void check(String name, ExtractedLine line, ExtractedLine cash, List<String> missing) {
        if (line == null || !nonnegative(line.getCurrent())) { missing.add(name + " 缺少有效金额或零值证据"); return; }
        if (!"consolidated".equals(line.getScope()) || !"point_in_time".equals(line.getPeriodType())) missing.add(name + " 非合并年末时点口径");
        if (blank(line.getUnit()) || "未标注".equals(line.getUnit()) || blank(line.getAsOfDate())) missing.add(name + " 单位或期间未标注");
        if (cash != null && (!Objects.equals(cash.getUnit(), line.getUnit()) || !Objects.equals(cash.getAsOfDate(), line.getAsOfDate()))) missing.add(name + " 与货币资金单位或期间冲突");
        if (line.getPage() == null || line.getPage() < 1 || blank(line.getSnippet()) || blank(line.getSourceFile())) missing.add(name + " 缺少来源页及原文证据");
        if (line.getDataQualityStatus() != null && !List.of("VALUE_AVAILABLE", "DISCLOSED_ZERO").contains(line.getDataQualityStatus())) missing.add(name + " 数据状态不可用");
        if ("DISCLOSED_ZERO".equals(line.getDataQualityStatus()) && line.getCurrent().signum() != 0) missing.add(name + " 零值状态与金额冲突");
    }

    private static boolean nonnegative(BigDecimal value) { return value != null && value.signum() >= 0; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
