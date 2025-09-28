package com.example.diagnosticservice.repository;

import com.example.diagnosticservice.entity.PacketProcessingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PacketProcessingSessionRepository extends JpaRepository<PacketProcessingSession, Long> {
    
    Optional<PacketProcessingSession> findByActivityIdAndApplicationDate(String activityId, java.time.LocalDate applicationDate);
    
    List<PacketProcessingSession> findByActivityId(String activityId);
    
    List<PacketProcessingSession> findByApplicationDate(java.time.LocalDate applicationDate);
    
    List<PacketProcessingSession> findByActivityType(String activityType);
    
    List<PacketProcessingSession> findByStatus(String status);
    
    List<PacketProcessingSession> findByStatusAndCreatedAtAfter(String status, Instant createdAt);
    
    @Query("SELECT s FROM PacketProcessingSession s WHERE s.status IN ('ACTIVE', 'PAUSED') ORDER BY s.createdAt ASC")
    List<PacketProcessingSession> findActiveOrPausedSessions();
    
    @Query("SELECT s FROM PacketProcessingSession s WHERE s.status = 'ACTIVE' AND s.lastProcessedAt < :cutoffTime ORDER BY s.lastProcessedAt ASC")
    List<PacketProcessingSession> findStaleActiveSessions(@Param("cutoffTime") Instant cutoffTime);
    
    @Query("SELECT s FROM PacketProcessingSession s WHERE s.activityId = :activityId AND s.applicationDate = :applicationDate AND s.status IN ('ACTIVE', 'PAUSED')")
    List<PacketProcessingSession> findActiveOrPausedSessionsByActivity(@Param("activityId") String activityId, @Param("applicationDate") java.time.LocalDate applicationDate);
    
    @Query("SELECT COUNT(s) FROM PacketProcessingSession s WHERE s.status = :status")
    long countByStatus(@Param("status") String status);
    
    @Query("SELECT COUNT(s) FROM PacketProcessingSession s WHERE s.activityId = :activityId AND s.applicationDate = :applicationDate")
    long countByActivityIdAndApplicationDate(@Param("activityId") String activityId, @Param("applicationDate") java.time.LocalDate applicationDate);
    
    @Query("SELECT s FROM PacketProcessingSession s WHERE s.endpointUrl = :endpointUrl AND s.status = 'ACTIVE'")
    List<PacketProcessingSession> findActiveSessionsByEndpoint(@Param("endpointUrl") String endpointUrl);
}
