package com.pos.license;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Thin HTTP client for the licensing server's /api/v1 endpoints. */
public final class LicenseClient {

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final Gson gson = new Gson();

    public LicenseClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public String activate(String key, String fingerprint, String machineLabel,
                           String product, String appVersion) {
        JsonObject b = new JsonObject();
        b.addProperty("key", key);
        b.addProperty("fingerprint", fingerprint);
        b.addProperty("machineLabel", machineLabel);
        b.addProperty("product", product);
        b.addProperty("appVersion", appVersion);
        return post("/api/v1/activate", b).get("token").getAsString();
    }

    public String startTrial(String product, String fingerprint, String machineLabel, String appVersion) {
        JsonObject b = new JsonObject();
        b.addProperty("product", product);
        b.addProperty("fingerprint", fingerprint);
        b.addProperty("machineLabel", machineLabel);
        b.addProperty("appVersion", appVersion);
        return post("/api/v1/trial", b).get("token").getAsString();
    }

    public String validate(String key, String fingerprint) {
        JsonObject b = new JsonObject();
        b.addProperty("key", key);
        b.addProperty("fingerprint", fingerprint);
        return post("/api/v1/validate", b).get("token").getAsString();
    }

    public void deactivate(String key, String fingerprint) {
        JsonObject b = new JsonObject();
        b.addProperty("key", key);
        b.addProperty("fingerprint", fingerprint);
        post("/api/v1/deactivate", b);
    }

    // ── internals ───────────────────────────────────────────────────────────

    private JsonObject post(String path, JsonObject body) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 200 || res.statusCode() == 204) {
                String b = res.body();
                return b == null || b.isBlank() ? new JsonObject()
                        : JsonParser.parseString(b).getAsJsonObject();
            }
            // Structured licensing error → {code, message}
            try {
                JsonObject err = JsonParser.parseString(res.body()).getAsJsonObject();
                throw new LicenseServerException(
                        err.has("code") ? err.get("code").getAsString() : "HTTP_" + res.statusCode(),
                        err.has("message") ? err.get("message").getAsString() : "Request failed");
            } catch (LicenseServerException e) {
                throw e;
            } catch (Exception parseFail) {
                throw new LicenseServerException("HTTP_" + res.statusCode(), "Server returned " + res.statusCode());
            }
        } catch (LicenseServerException e) {
            throw e;
        } catch (Exception e) {
            throw new LicenseServerException("UNREACHABLE",
                    "Could not reach the licensing server at " + baseUrl + ".");
        }
    }

    /** A response from the licensing server that isn't a success. */
    public static final class LicenseServerException extends RuntimeException {
        public final String code;
        public LicenseServerException(String code, String message) {
            super(message);
            this.code = code;
        }
        public boolean unreachable() { return "UNREACHABLE".equals(code); }
    }
}
