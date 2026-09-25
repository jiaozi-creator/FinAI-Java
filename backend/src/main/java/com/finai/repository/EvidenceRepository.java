package com.finai.repository;

import com.finai.model.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 证据数据访问层
 */
@Repository
public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    /**
     * 根据任务ID查询所有证据
     */
    List<Evidence> findByTaskIdOrderByFieldId(String taskId);

    /**
     * 根据任务ID和字段ID查询证据
     */
    List<Evidence> findByTaskIdAndFieldId(String taskId, String fieldId);

    /**
     * 根据任务ID和报告期查询证据
     */
    List<Evidence> findByTaskIdAndPeriod(String taskId, String period);

    /**
     * 根据字段ID查询证据
     */
    List<Evidence> findByFieldIdOrderByCreatedAtDesc(String fieldId);

    /**
     * 查询指定任务的特定字段证据
     */
    @Query("SELECT e FROM Evidence e WHERE e.taskId = :taskId AND e.fieldId IN :fieldIds")
    List<Evidence> findByTaskIdAndFieldIds(
            @Param("taskId") String taskId,
            @Param("fieldIds") List<String> fieldIds);

    /**
     * 查询低置信度证据
     */
    @Query("SELECT e FROM Evidence e WHERE e.taskId = :taskId AND e.confidence < :threshold")
    List<Evidence> findLowConfidenceEvidence(
            @Param("taskId") String taskId,
            @Param("threshold") Double threshold);

    /**
     * 统计任务的证据数量
     */
    long countByTaskId(String taskId);

    /**
     * 删除任务的所有证据
     */
    void deleteByTaskId(String taskId);

    /**
     * 查询指定页码的证据
     */
    @Query("SELECT e FROM Evidence e WHERE e.taskId = :taskId AND e.page = :page")
    List<Evidence> findByTaskIdAndPage(
            @Param("taskId") String taskId,
            @Param("page") Integer page);
}
