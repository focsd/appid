package com.focsd.appid;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Locale;

public final class AppValueFormatterTest {
    @Test
    public void durationHandlesBoundaries() {
        assertEquals("0m", AppValueFormatter.duration(-1));
        assertEquals("0m", AppValueFormatter.duration(0));
        assertEquals("1m", AppValueFormatter.duration(1));
        assertEquals("59m", AppValueFormatter.duration(59L * 60_000L));
        assertEquals("1h 1m", AppValueFormatter.duration(61L * 60_000L));
    }

    @Test
    public void bytesUsesReadableBinaryUnits() {
        assertEquals("0 B", AppValueFormatter.bytes(-1, Locale.US));
        assertEquals("1023 B", AppValueFormatter.bytes(1023, Locale.US));
        assertEquals("1.0 KB", AppValueFormatter.bytes(1024, Locale.US));
        assertEquals("1.5 MB", AppValueFormatter.bytes(1572864, Locale.US));
        assertEquals("1.0 GB", AppValueFormatter.bytes(1073741824L, Locale.US));
    }

    @Test
    public void safeAddRejectsInvalidValuesAndSaturatesOverflow() {
        assertEquals(0L, AppValueFormatter.safeAdd(-1L, 2L));
        assertEquals(5L, AppValueFormatter.safeAdd(2L, 3L));
        assertEquals(Long.MAX_VALUE, AppValueFormatter.safeAdd(Long.MAX_VALUE, 1L));
    }
}
