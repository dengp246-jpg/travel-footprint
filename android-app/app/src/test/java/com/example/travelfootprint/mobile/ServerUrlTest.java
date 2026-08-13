package com.example.travelfootprint.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class ServerUrlTest {

    @Test
    public void normalizesSecureServerAddress() {
        assertEquals("https://travel.example.com", ServerUrl.normalize(" travel.example.com/ ", false));
    }

    @Test
    public void debugBuildAllowsLocalHttpServer() {
        assertEquals("http://192.168.1.20:8080", ServerUrl.normalize("http://192.168.1.20:8080/", true));
    }

    @Test
    public void releaseBuildRejectsPlainHttp() {
        assertThrows(IllegalArgumentException.class, () -> ServerUrl.normalize("http://travel.example.com", false));
    }

    @Test
    public void rejectsCredentialsAndQueryParameters() {
        assertThrows(IllegalArgumentException.class, () -> ServerUrl.normalize("https://user:pass@example.com", false));
        assertThrows(IllegalArgumentException.class, () -> ServerUrl.normalize("https://example.com?token=secret", false));
    }
}
