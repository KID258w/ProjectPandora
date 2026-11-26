package com.projectpandora.app.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileFieldValidationTest {
    @Test fun acceptsUnicodeNamesAndNfcNormalizes() {
        assertEquals("Élodie O’Connor", normalizeProfileName("E\u0301lodie O’Connor"))
        assertFalse(validateProfileFields("曲懿涵", null, null).hasErrors)
        assertFalse(validateProfileFields("𠀀", null, null).hasErrors)
        assertTrue(validateProfileFields("甲".repeat(51), null, null).name != null)
        assertTrue(validateProfileFields("Li 3", null, null).name != null)
    }

    @Test fun validatesPhoneAndChineseIdentityChecksumAndDate() {
        assertFalse(validateProfileFields(null, "13812345678", "11010519491231002X").hasErrors)
        assertTrue(validateProfileFields(null, "1381234567", null).phone != null)
        assertTrue(validateProfileFields(null, null, "11010119900230123X").idNumber != null)
        assertTrue(validateProfileFields(null, null, "110101199001011234").idNumber != null)
        assertEquals("11010519491231002X", normalizeProfileIdNumber(" 11010519491231002x "))
    }
}
