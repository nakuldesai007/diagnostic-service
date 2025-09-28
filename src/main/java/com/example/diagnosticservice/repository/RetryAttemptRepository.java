package com.example.diagnosticservice.repository;

import com.example.diagnosticservice.entity.RetryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RetryAttemptRepository extends JpaRepository<RetryAttempt, Long> {
    
    List<RetryAttempt> findByMessageId(String messageId);
    
    List<RetryAttempt> findByStatus(String status);
    
    List<RetryAttempt> findByCreatedAtBetween(Instant startTime, Instant endTime);
    
    @Query("SELECT ra FROM RetryAttempt ra WHERE ra.messageId = :messageId ORDER BY ra.attemptNumber ASC")
    List<RetryAttempt> findRetryHistory(@Param("messageId") String messageId);
    
    @Query("SELECT ra.status, COUNT(ra) FROM RetryAttempt ra WHERE ra.createdAt >= :since GROUP BY ra.status")
    List<Object[]> getRetryStatusStats(@Param("since") Instant since);
    
    @Query("SELECT ra.errorCategory, COUNT(ra) FROM RetryAttempt ra WHERE ra.createdAt >= :since GROUP BY ra.errorCategory")
    List<Object[]> getRetryErrorCategoryStats(@Param("since") Instant since);
    
    @Query("SELECT AVG(ra.processingTimeMs) FROM RetryAttempt ra WHERE ra.status = 'SUCCESS' AND ra.processingTimeMs IS NOT NULL AND ra.createdAt >= :since")
    Double getAverageProcessingTime(@Param("since") Instant since);
}
