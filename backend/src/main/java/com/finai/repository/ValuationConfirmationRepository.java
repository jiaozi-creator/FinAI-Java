package com.finai.repository;

import com.finai.model.ValuationConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ValuationConfirmationRepository extends JpaRepository<ValuationConfirmation, String> {
    Optional<ValuationConfirmation> findFirstByTaskIdOrderByConfirmedAtDesc(String taskId);
    void deleteByTaskId(String taskId);
}
