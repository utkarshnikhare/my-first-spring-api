package com.example.my_first_spring_api.controller;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class VersionController {

    private final GitProperties gitProperties;
    private final BuildProperties buildProperties;

    @org.springframework.beans.factory.annotation.Autowired
    public VersionController(ObjectProvider<GitProperties> gitPropertiesProvider,
                            ObjectProvider<BuildProperties> buildPropertiesProvider) {
        this(gitPropertiesProvider.getIfAvailable(), buildPropertiesProvider.getIfAvailable());
    }

    // Visible for testing metadata-unavailable behavior.
    VersionController(GitProperties gitProperties, BuildProperties buildProperties) {
        this.gitProperties = gitProperties;
        this.buildProperties = buildProperties;
    }

    @GetMapping("/api/version")
    public Map<String, Object> version() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("application", "SocioMart");
        result.put("version", buildProperties != null ? buildProperties.getVersion() : "unknown");
        String commitId = "unknown";
        String shortCommitId = "unknown";
        if (gitProperties != null) {
            String full = gitProperties.getCommitId();
            if (full == null) {
                full = gitProperties.get("commit.id.full");
            }
            if (full != null) {
                commitId = full;
            }
            String abbrev = gitProperties.getShortCommitId();
            if (abbrev == null) {
                abbrev = gitProperties.get("commit.id.abbrev");
            }
            if (abbrev != null) {
                shortCommitId = abbrev;
            }
        }
        result.put("commit", commitId);
        result.put("shortCommit", shortCommitId);
        result.put("buildTime", buildProperties != null ? buildProperties.getTime() : null);
        return result;
    }
}
