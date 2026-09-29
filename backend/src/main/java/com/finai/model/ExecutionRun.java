package com.finai.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "execution_run")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionRun {
    @Id
    private String id;
    @Column(nullable = false)
    private String taskId;
    private String inputSha256;
    @Column(nullable = false)
    private String status;
    private String currentStage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorCode;
    private String ruleVersion;
}
