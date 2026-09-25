package com.finai.analysis;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class StatementExtract {
    List<ExtractedLine> lines;
    List<PolicyNote> policyNotes;
}
