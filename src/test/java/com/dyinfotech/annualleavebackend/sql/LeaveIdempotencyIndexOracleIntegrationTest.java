package com.dyinfotech.annualleavebackend.sql;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.dyinfotech.annualleavebackend.domain.Employee;
import com.dyinfotech.annualleavebackend.support.OracleIntegrationTestSupport;

import jakarta.persistence.PersistenceException;

class LeaveIdempotencyIndexOracleIntegrationTest extends OracleIntegrationTestSupport {

    @Test
    void sameEmployeeMayHaveMultipleRowsWithoutIdempotencyKey() {
        Employee employee = seededCeo();

        assertDoesNotThrow(() -> {
            insert(employee.getEmployeeId(), null, null, 1);
            insert(employee.getEmployeeId(), null, null, 2);
            em.flush();
        });
    }

    @Test
    void sameEmployeeAndSameIdempotencyKeyIsRejected() {
        Employee employee = seededCeo();
        String key = "oracle-idempotency-0001";
        String hash = "a".repeat(64);

        insert(employee.getEmployeeId(), key, hash, 11);
        em.flush();

        assertThrows(PersistenceException.class, () -> {
            insert(employee.getEmployeeId(), key, hash, 12);
            em.flush();
        });
    }

    private void insert(
            Long employeeId,
            String requestKey,
            String requestHash,
            int dayOffset) {
        LocalDate date = LocalDate.now().plusDays(dayOffset);
        em.createNativeQuery("""
                INSERT INTO leave_request (
                    employee_id,
                    leave_type,
                    start_date,
                    end_date,
                    use_days,
                    prev_total_leave_days,
                    curr_total_leave_days,
                    leave_reason,
                    create_request_key,
                    create_request_hash,
                    status,
                    created_at,
                    created_ip
                ) VALUES (
                    :employeeId,
                    'FULL',
                    :startDate,
                    :endDate,
                    1,
                    15,
                    14,
                    NULL,
                    :requestKey,
                    :requestHash,
                    'PENDING',
                    :createdAt,
                    '127.0.0.1'
                )
                """)
                .setParameter("employeeId", employeeId)
                .setParameter("startDate", date)
                .setParameter("endDate", date)
                .setParameter("requestKey", requestKey)
                .setParameter("requestHash", requestHash)
                .setParameter("createdAt", LocalDateTime.now())
                .executeUpdate();
    }
}
