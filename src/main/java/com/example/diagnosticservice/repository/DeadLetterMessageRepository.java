package com.example.diagnosticservice.repository;

import com.example.diagnosticservice.entity.DeadLetterMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface DeadLetterMessageRepository extends JpaRepository<DeadLetterMessage, Long> {
    
    List<DeadLetterMessage> findByMessageId(String messageId);
    
    List<DeadLetterMessage> findByErrorCategory(String errorCategory);
    
    List<DeadLetterMessage> findByProcessingStatus(String processingStatus);
    
    List<DeadLetterMessage> findByCreatedAtBetween(Instant startTime, Instant endTime);
    
    @Query("SELECT dlm FROM DeadLetterMessage dlm WHERE dlm.sourceTopic = :topic ORDER BY dlm.createdAt DESC")
    List<DeadLetterMessage> findBySourceTopic(@Param("topic") String topic);
    
    @Query("SELECT dlm.errorCategory, COUNT(dlm) FROM DeadLetterMessage dlm WHERE dlm.createdAt >= :since GROUP BY dlm.errorCategory")
    List<Object[]> getErrorCategoryStats(@Param("since") Instant since);
    
    @Query("SELECT dlm.processingStatus, COUNT(dlm) FROM DeadLetterMessage dlm WHERE dlm.createdAt >= :since GROUP BY dlm.processingStatus")
    List<Object[]> getProcessingStatusStats(@Param("since") Instant since);
    
    @Query("SELECT dlm.sourceService, COUNT(dlm) FROM DeadLetterMessage dlm WHERE dlm.createdAt >= :since GROUP BY dlm.sourceService")
    List<Object[]> getSourceServiceStats(@Param("since") Instant since);
}
