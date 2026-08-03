package com.dyinfotech.annualleavebackend.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IpContext {
	private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

	public static void set(String ip) {
		HOLDER.set(ip);
	}

	public static String get() {
		String ip = HOLDER.get();
		return (ip != null) ? ip : "SYSTEM";
	}

	public static void clear() {
		HOLDER.remove();
	}
}
