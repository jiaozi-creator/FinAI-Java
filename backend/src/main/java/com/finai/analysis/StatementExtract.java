package com.finai.analysis;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class StatementExtract {
    /** Final selected values, one per field. */
    List<ExtractedLine> lines;
    /** Every located candidate, including rejected rows and their reasons. */
    List<ExtractedLine> candidates;
    List<PolicyNote> policyNotes;
}
