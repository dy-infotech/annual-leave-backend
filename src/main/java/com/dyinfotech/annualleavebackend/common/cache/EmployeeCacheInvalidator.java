package com.dyinfotech.annualleavebackend.common.cache;

import java.util.Collection;

import org.springframework.stereotype.Component;

import com.dyinfotech.annualleavebackend.common.transaction.AfterCommitExecutor;
import com.dyinfotech.annualleavebackend.config.CacheConfig;

@Component
public class EmployeeCacheInvalidator {

    private final AfterCommitExecutor afterCommitExecutor;
    private final EmployeeViewCacheKey employeeViewCacheKey;

    public EmployeeCacheInvalidator(
            AfterCommitExecutor afterCommitExecutor,
            EmployeeViewCacheKey employeeViewCacheKey) {
        this.afterCommitExecutor = afterCommitExecutor;
        this.employeeViewCacheKey = employeeViewCacheKey;
    }

    public void afterEmployeeViewChange(Long employeeId) {
        afterCommitExecutor.execute(() -> employeeViewCacheKey.bumpEmployee(employeeId));
    }

    public void afterEmployeeChange(
            Long employeeId,
            Collection<String> names,
            String employeeNumber) {
        afterCommitExecutor.execute(() -> {
            employeeViewCacheKey.bumpEmployee(employeeId);
            invalidateEmailLookups(names, employeeNumber);
        });
    }

    public void afterEmailLookupChange(Collection<String> names, String employeeNumber) {
        afterCommitExecutor.execute(() -> invalidateEmailLookups(names, employeeNumber));
    }

    private void invalidateEmailLookups(Collection<String> names, String employeeNumber) {
        if (names != null) {
            names.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .forEach(CacheConfig.EMAIL_BY_NAME_CACHE::invalidate);
        }
        if (employeeNumber != null && !employeeNumber.isBlank()) {
            CacheConfig.EMAIL_BY_EMPLOYEE_NUMBER_CACHE.invalidate(employeeNumber);
        }
    }
}
