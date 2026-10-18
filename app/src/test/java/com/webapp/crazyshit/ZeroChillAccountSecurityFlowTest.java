package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Protects the destructive confirmation and password mismatch gates. */
public class ZeroChillAccountSecurityFlowTest {
    @Test public void deleteRequiresExactTypedPhrase() {
        assertFalse(ZeroChillAccountSecurityActivity.validDeleteConfirmation(null));
        assertFalse(ZeroChillAccountSecurityActivity.validDeleteConfirmation("delete"));
        assertFalse(ZeroChillAccountSecurityActivity.validDeleteConfirmation("DELETE NOW"));
        assertTrue(ZeroChillAccountSecurityActivity.validDeleteConfirmation(" DELETE "));
    }

    @Test public void passwordConfirmationMustMatchExactly() {
        assertFalse(ZeroChillAccountSecurityActivity.matchingPasswords("password123", "Password123"));
        assertFalse(ZeroChillAccountSecurityActivity.matchingPasswords("password123", "password1234"));
        assertTrue(ZeroChillAccountSecurityActivity.matchingPasswords("password123", "password123"));
    }
}
