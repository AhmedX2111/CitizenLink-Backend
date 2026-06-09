package com.ntg.CitizenLink.repositories;

import com.ntg.CitizenLink.entities.AuditLog;
import com.ntg.CitizenLink.enums.EventType;
import com.ntg.CitizenLink.enums.ActionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    // Find logs by user
    List<AuditLog> findByUserIdOrderByTimestampDesc(UUID userId, Pageable pageable);

    // Find logs by username
    List<AuditLog> findByUsernameOrderByTimestampDesc(String username, Pageable pageable);

    // Find logs by event type
    List<AuditLog> findByEventTypeOrderByTimestampDesc(EventType eventType, Pageable pageable);

    // Find logs by status
    List<AuditLog> findByStatusOrderByTimestampDesc(ActionStatus status, Pageable pageable);

    // Count failed login attempts for a user in last X minutes
    @Query("SELECT COUNT(l) FROM AuditLog l WHERE l.username = :username AND l.eventType = 'LOGIN_FAILURE' AND l.timestamp > :since")
    long countFailedLoginsSince(@Param("username") String username, @Param("since") OffsetDateTime since);

    // Find recent activity for a user
    @Query("SELECT l FROM AuditLog l WHERE l.userId = :userId AND l.timestamp > :since ORDER BY l.timestamp DESC")
    List<AuditLog> findRecentUserActivity(@Param("userId") UUID userId, @Param("since") OffsetDateTime since);

    // Find by correlation ID (for debugging request chains)
    List<AuditLog> findByCorrelationIdOrderByTimestampAsc(UUID correlationId);

    // Delete old logs (for cleanup)
    @Modifying
    @Transactional
    @Query("DELETE FROM AuditLog l WHERE l.timestamp < :cutoffDate")
    int deleteLogsOlderThan(@Param("cutoffDate") OffsetDateTime cutoffDate);
}