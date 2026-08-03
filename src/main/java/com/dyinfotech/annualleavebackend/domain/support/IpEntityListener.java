package com.dyinfotech.annualleavebackend.domain.support;

import com.dyinfotech.annualleavebackend.common.IpContext;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

public class IpEntityListener {

    @PrePersist
    public void prePersist(Object entity) {
        String ip = IpContext.get();

        if (entity instanceof HasCreatedAudit e && e.getCreatedAudit() != null) {
            e.getCreatedAudit().setCreatedIp(ip);
        }
        if (entity instanceof HasCreatedIp e) {
        	e.changeCreatedIp(ip);
        }
        if (entity instanceof HasUpdatedAudit e && e.getUpdatedAudit() != null) {
            e.getUpdatedAudit().setUpdatedIp(ip);
        }
    }

    @PreUpdate
    public void preUpdate(Object entity) {
        if (entity instanceof HasUpdatedAudit e && e.getUpdatedAudit() != null) {
            e.getUpdatedAudit().setUpdatedIp(IpContext.get());
        }
    }
}
