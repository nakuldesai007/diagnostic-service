package com.example.diagnosticservice.repository;

import com.example.diagnosticservice.entity.MessageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageLogRepository extends JpaRepository<MessageLog, Long> {
    
    Optional<MessageLog> findByMessageId(String messageId);
    
    List<MessageLog> findByTopicAndPartition(String topic, Integer partition);
    
    List<MessageLog> findByProcessingStatus(String processingStatus);
    
    List<MessageLog> findByErrorCategory(String errorCategory);
    
    List<MessageLog> findByCreatedAtBetween(Instant startTime, Instant endTime);
    
    @Query("SELECT ml FROM MessageLog ml WHERE ml.messageId = :messageId ORDER BY ml.createdAt DESC")
    List<MessageLog> findMessageHistory(@Param("messageId") String messageId);
    
    @Query("SELECT ml FROM MessageLog ml WHERE ml.topic = :topic AND ml.partition = :partition AND ml.offset >= :offset ORDER BY ml.offset ASC")
    List<MessageLog> findMessagesFromOffset(@Param("topic") String topic, @Param("partition") Integer partition, @Param("offset") Long offset);
    
    @Query("SELECT COUNT(ml) FROM MessageLog ml WHERE ml.processingStatus = :status AND ml.createdAt >= :since")
    Long countByProcessingStatusSince(@Param("status") String status, @Param("since") Instant since);
    
    @Query("SELECT ml.errorCategory, COUNT(ml) FROM MessageLog ml WHERE ml.createdAt >= :since GROUP BY ml.errorCategory")
    List<Object[]> getErrorCategoryStats(@Param("since") Instant since);
    
    @Query("SELECT ml.processingStatus, COUNT(ml) FROM MessageLog ml WHERE ml.createdAt >= :since GROUP BY ml.processingStatus")
    List<Object[]> getProcessingStatusStats(@Param("since") Instant since);
    
    Page<MessageLog> findByTopicContainingIgnoreCaseOrErrorMessageContainingIgnoreCase(
            String topic, String errorMessage, Pageable pageable);
}
