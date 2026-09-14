package com.mosaicglobal.finance;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Small HTTP helper for integration tests: never throws on 4xx/5xx, parses JSON bodies. */
final class ApiClient {

  static final Map<String, Object> DEVICE =
      Map.of("deviceId", "it-device-0001", "deviceName", "IT Pixel", "platform", "ANDROID");

  private final RestClient http;
  private final JsonMapper json = JsonMapper.builder().build();

  ApiClient(int port) {
    this.http =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
            .build();
  }

  ResponseEntity<String> post(String path, Object body, String idempotencyKey, String bearer) {
    RestClient.RequestBodySpec spec =
        http.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body);
    if (idempotencyKey != null) {
      spec = spec.header("Idempotency-Key", idempotencyKey);
    }
    if (bearer != null) {
      spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
    }
    return spec.retrieve().toEntity(String.class);
  }

  ResponseEntity<String> post(String path, Object body) {
    return post(path, body, UUID.randomUUID().toString(), null);
  }

  ResponseEntity<String> get(String path, String bearer) {
    RestClient.RequestHeadersSpec<?> spec = http.get().uri(path);
    if (bearer != null) {
      spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
    }
    return spec.retrieve().toEntity(String.class);
  }

  JsonNode json(ResponseEntity<String> response) {
    return json.readTree(response.getBody());
  }

  static Map<String, Object> registerBody(String email) {
    return Map.of(
        "email",
        email,
        "password",
        "correct horse battery",
        "displayName",
        "Alice",
        "device",
        DEVICE);
  }

  static Map<String, Object> loginBody(String email, String password) {
    return Map.of("email", email, "password", password, "device", DEVICE);
  }

  static Map<String, Object> refreshBody(String refreshToken) {
    return Map.of("refreshToken", refreshToken, "deviceId", DEVICE.get("deviceId"));
  }
}
