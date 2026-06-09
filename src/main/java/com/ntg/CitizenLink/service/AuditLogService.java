package com.ntg.CitizenLink.service;

import com.ntg.CitizenLink.entities.AuditLog;
import com.ntg.CitizenLink.enums.EventType;
import com.ntg.CitizenLink.enums.ActionStatus;
import com.ntg.CitizenLink.repositories.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Value("${audit.log.retention-days:90}")
    private int retentionDays;

    /**
     * Log an authentication event (login success/failure, logout)
     */
    @Async
    public void logAuthenticationEvent(EventType eventType, String username, UUID userId,
                                       String userRole, ActionStatus status, String reason) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .correlationId(UUID.randomUUID())
                    .eventType(eventType)
                    .userId(userId)
                    .username(username)
                    .userRole(userRole)
                    .status(status)
                    .reason(reason)
                    .message(String.format("%s for user: %s", eventType, username))
                    .timestamp(OffsetDateTime.now())
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("Authentication event logged: {} for user: {}", eventType, username);
        } catch (Exception e) {
            log.error("Failed to log authentication event: {}", eventType, e);
        }
    }

    /**
     * Get failed login attempts for a user (for lockout detection)
     */
    public long getRecentFailedLoginAttempts(String username, int minutes) {
        OffsetDateTime since = OffsetDateTime.now().minusMinutes(minutes);
        return auditLogRepository.countFailedLoginsSince(username, since);
    }

    /**
     * Clean up old logs (runs daily at 2 AM)
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Async
    public void cleanupOldLogs() {
        try {
            OffsetDateTime cutoffDate = OffsetDateTime.now().minusDays(retentionDays);
            int deletedCount = auditLogRepository.deleteLogsOlderThan(cutoffDate);
            log.info("Deleted {} audit logs older than {} days", deletedCount, retentionDays);
        } catch (Exception e) {
            log.error("Failed to cleanup old audit logs", e);
        }
    }
}
