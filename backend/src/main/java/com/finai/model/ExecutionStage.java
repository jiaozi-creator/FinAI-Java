package com.finai.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "execution_stage")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStage {
    @Id
    private String id;
    @Column(nullable = false)
    private String runId;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorCode;
}
