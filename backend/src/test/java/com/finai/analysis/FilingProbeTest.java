package com.finai.analysis;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FilingProbeTest {

    @Test
    void dumpMengbaiheFields() throws Exception {
        Path pdf = Path.of("..", "test-data", "filings", "603313_2025_annual.pdf");
        StatementExtract extract = new StatementExtractor().extract(pdf.toString());
        StringBuilder out = new StringBuilder();
        out.append("lines=").append(extract.getLines().size())
                .append(" pages=").append(extract.getPageCount())
                .append(" sha256=").append(extract.getSha256())
                .append('\n');
        for (ExtractedLine line : extract.getLines()) {
            out.append(line.getFieldId())
                    .append('\t').append(line.getFieldName())
                    .append('\t').append(line.getCurrent())
                    .append('\t').append(line.getPrior())
                    .append('\t').append(line.getUnit())
                    .append('\t').append(line.getScope())
                    .append('\t').append(line.getPage())
                    .append('\n');
        }
        Path target = Path.of("..", "eval", "gold", "603313_2025_extracted.tsv");
        Files.writeString(target, out.toString());

        Map<String, ExtractedLine> byId = extract.getLines().stream()
                .collect(Collectors.toMap(ExtractedLine::getFieldId, Function.identity(), (a, b) -> a));
        assertAmount(byId, "net_profit", "-9822497.95", "-151420324.40", "consolidated");
        assertAmount(byId, "net_profit_deducted", "-39312271.78", "-279702121.97", "summary");
        assertAmount(byId, "cash", "1088855303.96", "1006282974.26", "consolidated");
        assertAmount(byId, "inventory", "1781225167.08", "1643949298.23", "consolidated");
        assertAmount(byId, "minority_interest", "41232492.55", "57869285.87", "consolidated");
        assertAmount(byId, "net_assets", "3499230196.18", "3628152636.36", "consolidated");
        assertAmount(byId, "basic_eps", "-0.02", "-0.27", "consolidated");
        assertAmount(byId, "revenue", "8912075395.97", "8449386211.83", "consolidated");
    }

    private static void assertAmount(Map<String, ExtractedLine> byId, String id, String current, String prior, String scope) {
        ExtractedLine line = byId.get(id);
        assertThat(line).as(id).isNotNull();
        assertThat(line.getCurrent()).as(id + ".current").isEqualByComparingTo(current);
        assertThat(line.getPrior()).as(id + ".prior").isEqualByComparingTo(prior);
        assertThat(line.getScope()).as(id + ".scope").isEqualTo(scope);
    }
}
