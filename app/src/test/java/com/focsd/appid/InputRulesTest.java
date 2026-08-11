package com.focsd.appid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class InputRulesTest {
    @Test
    public void packageIdsRequireAtLeastTwoValidSegments() {
        assertTrue(InputRules.isPackageId("com.focsd.appid"));
        assertTrue(InputRules.isPackageId("a.b"));
        assertTrue(InputRules.isPackageId("com.example.app_2"));
        assertFalse(InputRules.isPackageId(null));
        assertFalse(InputRules.isPackageId("single"));
        assertFalse(InputRules.isPackageId("1com.example"));
        assertFalse(InputRules.isPackageId("com.example-name"));
    }

    @Test
    public void colorsAreValidatedAndCanonicalized() {
        assertEquals("#F2F2F2", InputRules.normalizeColor("f2f2f2"));
        assertEquals("#172238", InputRules.normalizeColor("#172238"));
        assertNull(InputRules.normalizeColor("#123"));
        assertNull(InputRules.normalizeColor("not-a-color"));
        assertNull(InputRules.normalizeColor(null));
    }
}
