package com.f1stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SettingsManagerTest {

    @Test
    public void normaliseUrl_addsTrailingSlash() {
        assertEquals("https://f1.example.com/", SettingsManager.normaliseUrl(" https://f1.example.com ", false));
        assertEquals("https://f1.example.com/api/", SettingsManager.normaliseUrl("https://f1.example.com/api/", false));
    }

    @Test
    public void normaliseUrl_httpOnlyWhenAllowed() {
        assertNull(SettingsManager.normaliseUrl("http://10.0.2.2:8000", false));
        assertEquals("http://10.0.2.2:8000/", SettingsManager.normaliseUrl("http://10.0.2.2:8000", true));
    }

    @Test
    public void normaliseUrl_rejectsInvalid() {
        assertNull(SettingsManager.normaliseUrl(null, true));
        assertNull(SettingsManager.normaliseUrl("ftp://f1.example.com", true));
        assertNull(SettingsManager.normaliseUrl("f1.example.com", true));
        assertNull(SettingsManager.normaliseUrl("https://", true));
    }
}
