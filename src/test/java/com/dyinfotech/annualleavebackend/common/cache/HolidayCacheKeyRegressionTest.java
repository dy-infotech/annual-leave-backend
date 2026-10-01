package com.dyinfotech.annualleavebackend.common.cache;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HolidayCacheKeyRegressionTest {

    @Test
    void advance_movesFutureReadsToNewGeneration() {
        HolidayCacheKey key = new HolidayCacheKey();

        String oldYear = key.year(2026);
        String oldRange = key.range(2026, 2027);

        key.advance();

        String newYear = key.year(2026);
        String newRange = key.range(2026, 2027);

        assertNotEquals(oldYear, newYear);
        assertNotEquals(oldRange, newRange);
        assertTrue(oldYear.endsWith(":year:2026"));
        assertTrue(newRange.endsWith(":range:2026:2027"));
    }
}
