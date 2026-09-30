package com.example.my_first_spring_api.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression cover for the local API-port configuration defect.
 *
 * <p>Symptom: with the app served from any port other than 8081, every API call was
 * sent to http://localhost:8081 instead of the app that actually served the page,
 * so each Seller screen silently fell back to its error state ("Seller session
 * required", zero history cards).
 *
 * <p>Root cause was API URL selection, NOT CORS: config.js returned
 * 'http://localhost:8081' whenever the page was on localhost, even though the
 * Spring app serves BOTH the static pages and /api/** from a single port, so the
 * API always lives at the page's own origin.
 *
 * <p>These are behavioural tests: they execute the shipped config.js in a Node vm
 * with a synthetic browser environment and assert both the resolved base and the
 * URL common.js would actually build. The class is skipped (never failed) when
 * Node is unavailable, so it cannot break a build on a machine without it.
 */
@EnabledIf("nodeAvailable")
class SellerApiBaseConfigTest {

    private static Path configJs;
    private static Map<String, List<String>> rows;

    static boolean nodeAvailable() {
        try {
            Process p = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
            return p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** One probe row per case: {label, status, base, joined, errName, errMsg}. */
    private static List<String> r(String label) {
        List<String> row = rows.get(label);
        assertThat(row).as("probe row for %s", label).isNotNull();
        return row;
    }

    private static String base(String label) { return r(label).get(2); }

    private static String joined(String label) { return r(label).get(3); }

    private static void assertOk(String label) {
        assertThat(r(label).get(1)).as("%s must resolve without error (err: %s)", label, r(label).get(5))
                .isEqualTo("ok");
    }

    private static String kase(String label, String host, int port) {
        return "{\"label\":\"" + label + "\",\"hostname\":\"" + host + "\",\"port\":" + port + "}";
    }

    private static String kase(String label, String host, int port, String override) {
        return "{\"label\":\"" + label + "\",\"hostname\":\"" + host + "\",\"port\":" + port
                + ",\"hasOverride\":true,\"override\":\"" + override + "\"}";
    }

    @BeforeAll
    static void runProbe(@TempDir Path tmp) throws Exception {
        configJs = Paths.get("src", "main", "resources", "static", "js", "config.js");
        Path probeJs = Paths.get("src", "test", "resources", "config-base-probe.js");
        assertThat(configJs).as("shipped config.js must exist").exists();
        assertThat(probeJs).as("probe harness must exist").exists();

        List<String> cases = List.of(
                kase("default-localhost-8081", "localhost", 8081),
                kase("default-localhost-8099", "localhost", 8099),
                kase("default-localhost-3000", "localhost", 3000),
                kase("default-loopback-8099", "127.0.0.1", 8099),
                kase("default-loopback-8081", "127.0.0.1", 8081),
                kase("default-render", "sociomart-demo.onrender.com", 443),
                kase("default-lan-host", "192.168.1.10", 8081),
                kase("override-loopback-8081", "localhost", 3000, "http://127.0.0.1:8081"),
                kase("override-localhost-9090", "localhost", 3000, "http://localhost:9090"),
                kase("override-trailing-slash", "localhost", 3000, "http://127.0.0.1:8081/"),
                kase("override-empty-string", "localhost", 3000, ""),
                kase("override-blank-string", "localhost", 3000, "   "),
                kase("override-invalid-url", "localhost", 3000, "not-a-url"),
                kase("override-relative-path", "localhost", 3000, "/api"),
                kase("override-bad-protocol", "localhost", 3000, "ftp://127.0.0.1:8081"),
                kaseNum("override-non-string", "localhost", 3000, 8081));

        // Cases go through a file: Windows strips quotes from JSON passed on argv.
        Path casesFile = tmp.resolve("cases.json");
        Files.writeString(casesFile, "[" + String.join(",", cases) + "]", StandardCharsets.UTF_8);

        Process p = new ProcessBuilder("node", probeJs.toString(), configJs.toString(), casesFile.toString())
                .redirectErrorStream(false).start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) out.append(line).append('\n');
        }
        String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(p.waitFor(120, TimeUnit.SECONDS)).as("probe must finish").isTrue();
        assertThat(p.exitValue()).as("probe failed. stderr=%s", err).isZero();

        rows = new LinkedHashMap<>();
        for (String line : out.toString().split("\\R")) {
            if (!line.isBlank()) {
                List<String> cells = new ArrayList<>(List.of(line.split("\t", -1)));
                rows.put(cells.get(0), cells);
            }
        }
        assertThat(rows).as("one probe row per case").hasSize(cases.size());
    }

    private static String kaseNum(String label, String host, int port, int override) {
        return "{\"label\":\"" + label + "\",\"hostname\":\"" + host + "\",\"port\":" + port
                + ",\"hasOverride\":true,\"override\":" + override + "}";
    }

    /** The regression: no host/port may be forced onto the old hard-coded :8081. */
    @Test
    void resolvesSameOriginOnEveryHostAndPortWithoutAnOverride() {
        for (String label : new String[]{"default-localhost-8081", "default-localhost-8099",
                "default-localhost-3000", "default-loopback-8099", "default-loopback-8081",
                "default-render", "default-lan-host"}) {
            assertOk(label);
            assertThat(base(label)).as("%s must be same-origin", label).isEqualTo("");
            assertThat(base(label)).as("%s must not target the old :8081", label)
                    .isNotEqualTo("http://localhost:8081");
        }
    }

    /** The URL common.js actually builds must stay a plain path on this origin. */
    @Test
    void commonJsBuildsAPlainRelativeApiPathByDefault() {
        assertThat(joined("default-localhost-8099")).isEqualTo("/api/seller-app/history");
        assertThat(joined("default-render")).isEqualTo("/api/seller-app/history");
    }

    /** A split frontend :3000 / backend :8081 must still reach the backend. */
    @Test
    void explicitOverrideIsHonouredWhenFrontendAndBackendDiffer() {
        assertOk("override-loopback-8081");
        assertThat(base("override-loopback-8081")).isEqualTo("http://127.0.0.1:8081");
        assertThat(joined("override-loopback-8081")).isEqualTo("http://127.0.0.1:8081/api/seller-app/history");
        assertOk("override-localhost-9090");
        assertThat(base("override-localhost-9090")).isEqualTo("http://localhost:9090");
    }

    @Test
    void overrideTrailingSlashIsNormalisedSoTheApiPathIsNotDoubled() {
        assertOk("override-trailing-slash");
        assertThat(base("override-trailing-slash")).isEqualTo("http://127.0.0.1:8081");
        assertThat(joined("override-trailing-slash")).doesNotContain("//api");
    }

    @Test
    void explicitEmptyOrBlankOverrideMeansSameOrigin() {
        for (String label : new String[]{"override-empty-string", "override-blank-string"}) {
            assertOk(label);
            assertThat(base(label)).as("%s", label).isEqualTo("");
        }
    }

    /** An unusable override must fail loudly, never silently hit the wrong API. */
    @Test
    void invalidOverrideFailsLoudlyInsteadOfFallingBack() {
        for (String label : new String[]{"override-invalid-url", "override-relative-path", "override-bad-protocol"}) {
            assertThat(r(label).get(1)).as("%s must fail", label).isEqualTo("error");
            assertThat(r(label).get(4)).as("%s error type", label).isIn("SyntaxError", "TypeError");
            assertThat(base(label)).as("%s must not resolve to any base", label).isEmpty();
        }
    }

    @Test
    void nonStringOverrideIsRejected() {
        assertThat(r("override-non-string").get(1)).isEqualTo("error");
        assertThat(r("override-non-string").get(4)).isEqualTo("TypeError");
    }

    /** The rest of the CONFIG contract every screen reads must be untouched. */
    @Test
    void keepsTheRemainingConfigContractIntact() throws Exception {
        String src = new String(Files.readAllBytes(configJs), StandardCharsets.UTF_8);
        assertThat(src).contains("APP_NAME: 'SocioMart'").contains("SESSION_TIMEOUT:").contains("DEBUG:");
    }
}
