package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ZeroChillAccountValidationTest {
    @Test
    public void acceptsSimpleUsernames() {
        assertEquals("", ZeroChillAccountValidation.username("Addy_37"));
        assertEquals("addy_37", ZeroChillAccountValidation.normalizedUsername(" Addy_37 "));
    }

    @Test
    public void rejectsBadUsernames() {
        assertFalse(ZeroChillAccountValidation.username("ab").isEmpty());
        assertFalse(ZeroChillAccountValidation.username("bad name").isEmpty());
        assertFalse(ZeroChillAccountValidation.username("way_too_long_for_zerochill").isEmpty());
    }

    @Test
    public void validatesEmailAndPassword() {
        assertEquals("", ZeroChillAccountValidation.email("user@example.com"));
        assertFalse(ZeroChillAccountValidation.email("bad-email").isEmpty());
        assertEquals("", ZeroChillAccountValidation.password("12345678"));
        assertFalse(ZeroChillAccountValidation.password("short").isEmpty());
    }
}
