package com.goldbee.analysis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPerformanceAnalyzerTest {

    @Test
    fun sessionWindowsUseStartInclusiveAndEndExclusiveHours() {
        val london = SessionPerformanceAnalyzer.defaultWindows.first { it.id == "LONDON" }
        assertTrue(london.containsHour(15))
        assertTrue(london.containsHour(19))
        assertFalse(london.containsHour(14))
        assertFalse(london.containsHour(20))
    }

    @Test
    fun midnightCrossingWindowIncludesLateEveningButNotMidnightBoundary() {
        val overlap = SessionPerformanceAnalyzer.defaultWindows.first { it.id == "OVERLAP" }
        assertTrue(overlap.containsHour(20))
        assertTrue(overlap.containsHour(23))
        assertFalse(overlap.containsHour(0))
        assertFalse(overlap.containsHour(19))
    }

    @Test
    fun newYorkLateWindowIncludesHoursAfterMidnight() {
        val late = SessionPerformanceAnalyzer.defaultWindows.first { it.id == "NEW_YORK_LATE" }
        assertTrue(late.containsHour(0))
        assertTrue(late.containsHour(2))
        assertFalse(late.containsHour(3))
    }
}
