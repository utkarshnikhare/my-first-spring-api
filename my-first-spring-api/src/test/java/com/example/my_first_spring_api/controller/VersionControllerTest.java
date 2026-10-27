package com.example.my_first_spring_api.controller;

import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VersionControllerTest {

    @org.junit.jupiter.api.Test
    void reportsUnknownWhenBuildMetadataIsUnavailable() {
        VersionController controller = new VersionController((GitProperties) null, (BuildProperties) null);
        Map<String, Object> result = controller.version();
        assertEquals("unknown", result.get("commit"));
        assertEquals("unknown", result.get("shortCommit"));
        assertEquals("unknown", result.get("version"));
        assertEquals(null, result.get("buildTime"));
        assertEquals("SocioMart", result.get("application"));
    }
}
