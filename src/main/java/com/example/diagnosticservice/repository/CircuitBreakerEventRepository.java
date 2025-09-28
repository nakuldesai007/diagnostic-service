package com.example.diagnosticservice.repository;

import com.example.diagnosticservice.entity.CircuitBreakerEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface CircuitBreakerEventRepository extends JpaRepository<CircuitBreakerEvent, Long> {
    
    List<CircuitBreakerEvent> findByCircuitBreakerName(String circuitBreakerName);
    
    List<CircuitBreakerEvent> findByEventType(String eventType);
    
    List<CircuitBreakerEvent> findByCreatedAtBetween(Instant startTime, Instant endTime);
    
    @Query("SELECT cbe FROM CircuitBreakerEvent cbe WHERE cbe.circuitBreakerName = :name AND cbe.createdAt >= :since ORDER BY cbe.createdAt DESC")
    List<CircuitBreakerEvent> findRecentEvents(@Param("name") String circuitBreakerName, @Param("since") Instant since);
    
    @Query("SELECT cbe.eventType, COUNT(cbe) FROM CircuitBreakerEvent cbe WHERE cbe.createdAt >= :since GROUP BY cbe.eventType")
    List<Object[]> getEventTypeStats(@Param("since") Instant since);
    
    @Query("SELECT cbe.circuitBreakerName, COUNT(cbe) FROM CircuitBreakerEvent cbe WHERE cbe.createdAt >= :since GROUP BY cbe.circuitBreakerName")
    List<Object[]> getCircuitBreakerStats(@Param("since") Instant since);
}
