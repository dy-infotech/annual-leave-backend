package com.dyinfotech.annualleavebackend.common.type;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum Role {
    EMPLOYEE,
    ADMIN
    ;
	
	private static final Map<String, Role> nameToRole = Arrays.stream(values())
																.collect(Collectors.toUnmodifiableMap(Role::name,
																			Function.identity()));
	public static Role getRole(String role) {
		return role == null || role.isBlank() ? null : nameToRole.get(role);
	}
	public static Role getAdminRole() {
		return Role.ADMIN;
	}
	public static boolean isAdmin(Role role) {
		return role == getAdminRole();
	}
	public String authority() {
        return "ROLE_" + name();
    }
}