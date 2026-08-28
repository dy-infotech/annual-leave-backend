package com.dyinfotech.annualleavebackend.common.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PositionTypeTest {

	@Test
	void getType_whenSeedCeoName_returnsCeo() {
		assertEquals(PositionType.CEO, PositionType.getType("사장"));
	}

	@Test
	void getType_whenOperationalCeoName_returnsCeo() {
		// 운영 데이터의 대표 직급 표기('대표이사')도 CEO로 인정한다
		assertEquals(PositionType.CEO, PositionType.getType("대표이사"));
	}

	@Test
	void getType_whenRegularPositionName_returnsMatchingType() {
		assertEquals(PositionType.MANAGER, PositionType.getType("과장"));
		assertEquals(PositionType.DIRECTOR, PositionType.getType("이사"));
	}

	@Test
	void getType_whenUnknownName_returnsNull() {
		assertNull(PositionType.getType("없는직급"));
	}

	@Test
	void isCEO_whenCeoAliasResolved_returnsTrue() {
		assertTrue(PositionType.isCEO(PositionType.getType("대표이사")));
		assertFalse(PositionType.isCEO(PositionType.getType("이사")));
	}
}
