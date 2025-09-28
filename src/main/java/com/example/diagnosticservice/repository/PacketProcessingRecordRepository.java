package com.example.diagnosticservice.repository;

import com.example.diagnosticservice.entity.PacketProcessingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PacketProcessingRecordRepository extends JpaRepository<PacketProcessingRecord, Long> {
    
    List<PacketProcessingRecord> findByActivityIdAndApplicationDate(String activityId, java.time.LocalDate applicationDate);
    
    List<PacketProcessingRecord> findByActivityId(String activityId);
    
    List<PacketProcessingRecord> findByApplicationDate(java.time.LocalDate applicationDate);
    
    List<PacketProcessingRecord> findByActivityIdAndApplicationDateAndStatus(String activityId, java.time.LocalDate applicationDate, String status);
    
    @Query("SELECT r FROM PacketProcessingRecord r WHERE r.activityId = :activityId AND r.applicationDate = :applicationDate AND r.status = 'FAILED' ORDER BY r.packetNumber ASC, r.recordIndex ASC")
    List<PacketProcessingRecord> findFailedRecordsByActivity(@Param("activityId") String activityId, @Param("applicationDate") java.time.LocalDate applicationDate);
    
    @Query("SELECT r FROM PacketProcessingRecord r WHERE r.activityId = :activityId AND r.applicationDate = :applicationDate AND r.status = 'PENDING' ORDER BY r.packetNumber ASC, r.recordIndex ASC")
    List<PacketProcessingRecord> findPendingRecordsByActivity(@Param("activityId") String activityId, @Param("applicationDate") java.time.LocalDate applicationDate);
    
    @Query("SELECT COUNT(r) FROM PacketProcessingRecord r WHERE r.activityId = :activityId AND r.applicationDate = :applicationDate AND r.status = :status")
    long countByActivityIdAndApplicationDateAndStatus(@Param("activityId") String activityId, @Param("applicationDate") java.time.LocalDate applicationDate, @Param("status") String status);
    
    @Query("SELECT r FROM PacketProcessingRecord r WHERE r.activityId = :activityId AND r.applicationDate = :applicationDate AND r.packetNumber = :packetNumber AND r.status = 'FAILED' ORDER BY r.recordIndex ASC")
    List<PacketProcessingRecord> findFailedRecordsInPacket(@Param("activityId") String activityId, @Param("applicationDate") java.time.LocalDate applicationDate, @Param("packetNumber") Integer packetNumber);
    
    @Query("SELECT r FROM PacketProcessingRecord r WHERE r.activityId = :activityId AND r.applicationDate = :applicationDate AND r.status = 'FAILED' AND r.retryCount < r.maxRetries ORDER BY r.packetNumber ASC, r.recordIndex ASC")
    List<PacketProcessingRecord> findRetryableFailedRecordsByActivity(@Param("activityId") String activityId, @Param("applicationDate") java.time.LocalDate applicationDate);
    
    @Query("SELECT r FROM PacketProcessingRecord r WHERE r.activityId = :activityId AND r.applicationDate = :applicationDate AND r.status = 'PENDING' AND r.createdAt < :cutoffTime ORDER BY r.packetNumber ASC, r.recordIndex ASC")
    List<PacketProcessingRecord> findStalePendingRecords(@Param("activityId") String activityId, @Param("applicationDate") java.time.LocalDate applicationDate, @Param("cutoffTime") Instant cutoffTime);
}
