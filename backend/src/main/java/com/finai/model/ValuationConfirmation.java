package com.finai.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** 每次确认保存独立版本，重算时冻结到运行记录。 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValuationConfirmation {
    @Id private String id;
    @Column(nullable = false) private String taskId;
    @Column(nullable = false) private String confirmedBy;
    @Column(nullable = false) private LocalDateTime confirmedAt;
    @Lob private String parametersJson;
    @Lob private String rationale;
    private String source;
}
