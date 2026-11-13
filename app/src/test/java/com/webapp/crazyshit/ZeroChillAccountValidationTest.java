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

    @Test public void activationEnforcesBackendPasswordLimitAndExactConfirmation() {
        String maximum = new String(new char[128]).replace('\0', 'a');
        assertEquals("", ZeroChillAccountValidation.activationPassword(maximum, maximum));
        assertFalse(ZeroChillAccountValidation.activationPassword(maximum + "a", maximum + "a").isEmpty());
        assertFalse(ZeroChillAccountValidation.activationPassword("password123", "Password123").isEmpty());
        assertFalse(ZeroChillAccountValidation.activationPassword("short", "short").isEmpty());
    }

    @Test
    public void validatesEmailAndPassword() {
        assertEquals("", ZeroChillAccountValidation.email("user@example.com"));
        assertFalse(ZeroChillAccountValidation.email("bad-email").isEmpty());
        assertEquals("", ZeroChillAccountValidation.password("12345678"));
        assertFalse(ZeroChillAccountValidation.password("short").isEmpty());
    }
}
