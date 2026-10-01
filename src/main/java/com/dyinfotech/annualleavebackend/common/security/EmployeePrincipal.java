package com.dyinfotech.annualleavebackend.common.security;

import com.dyinfotech.annualleavebackend.common.type.Role;

public record EmployeePrincipal(Long employeeId, Role role, boolean personnelAuthority) {
    public EmployeePrincipal(Long employeeId, Role role) {
        this(employeeId, role, false);
    }
}
