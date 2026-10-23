package com.projectpandora.app.feature.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkPermissionTest {
    @Test fun localApiHostsRequirePermissionOnAndroid17() {
        assertTrue(LocalNetworkPermission.isRequired("http://10.0.2.2:8000/", sdkInt = 37))
        assertTrue(LocalNetworkPermission.isRequired("http://192.168.1.25:8000/", sdkInt = 37))
        assertTrue(LocalNetworkPermission.isRequired("http://pandora.local:8000/", sdkInt = 37))
    }

    @Test fun publicApiAndOlderAndroidDoNotRequireLocalNetworkPermission() {
        assertFalse(LocalNetworkPermission.isRequired("https://api.example.com/", sdkInt = 37))
        assertFalse(LocalNetworkPermission.isRequired("http://10.0.2.2:8000/", sdkInt = 36))
    }
}
