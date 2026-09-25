package com.finai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一次任务的结构化结果。数字只来自计算引擎，不在这里现算。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "analysis_snapshot")
public class AnalysisSnapshot {

    @Id
    @Column(name = "task_id", length = 50)
    private String taskId;

    @Lob
    @Column(name = "metrics_json")
    private String metricsJson;

    @Lob
    @Column(name = "anomalies_json")
    private String anomaliesJson;

    @Lob
    @Column(name = "valuation_json")
    private String valuationJson;

    @Lob
    @Column(name = "report_json")
    private String reportJson;
}
