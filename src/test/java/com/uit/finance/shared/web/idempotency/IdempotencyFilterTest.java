package com.uit.finance.shared.web.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.shared.web.ProblemDetailFactory;
import com.uit.finance.shared.web.ProblemDetailWriter;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class IdempotencyFilterTest {

  private static final String PATH = "/api/v1/things";

  private InMemoryStore store;
  private IdempotencyFilter filter;
  private int invocations;

  @BeforeEach
  void setUp() {
    store = new InMemoryStore();
    JsonMapper mapper = JsonMapper.builder().build();
    ProblemDetailFactory problems = new ProblemDetailFactory(noTracer());
    filter =
        new IdempotencyFilter(
            store,
            new IdempotencyProperties(
                Duration.ofHours(24), Duration.ofSeconds(30), List.of("/api/"), 1024),
            problems,
            new ProblemDetailWriter(mapper));
    invocations = 0;
  }

  @Test
  void rejectsMissingOrMalformedKey() throws Exception {
    MockHttpServletResponse noKey = run(post("{}", null));
    assertThat(noKey.getStatus()).isEqualTo(400);
    assertThat(noKey.getContentAsString()).contains("request.idempotency_key_invalid");

    MockHttpServletResponse badKey = run(post("{}", "not-a-uuid"));
    assertThat(badKey.getStatus()).isEqualTo(400);
    assertThat(invocations).isZero();
  }

  @Test
  void firstCallExecutesAndSecondIdenticalCallIsReplayed() throws Exception {
    String key = UUID.randomUUID().toString();

    MockHttpServletResponse first = run(post("{\"a\":1}", key));
    MockHttpServletResponse second = run(post("{\"a\":1}", key));

    assertThat(invocations).isEqualTo(1);
    assertThat(first.getStatus()).isEqualTo(201);
    assertThat(second.getStatus()).isEqualTo(201);
    assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
    assertThat(second.getHeader(IdempotencyFilter.REPLAYED_HEADER)).isEqualTo("true");
    assertThat(first.getHeader(IdempotencyFilter.REPLAYED_HEADER)).isNull();
  }

  @Test
  void sameKeyWithDifferentPayloadIsRejectedWith422() throws Exception {
    String key = UUID.randomUUID().toString();
    run(post("{\"a\":1}", key));

    MockHttpServletResponse other = run(post("{\"a\":2}", key));

    assertThat(other.getStatus()).isEqualTo(422);
    assertThat(other.getContentAsString()).contains("request.idempotency_key_reused");
    assertThat(invocations).isEqualTo(1);
  }

  @Test
  void inProgressKeyIsRejectedWith409() throws Exception {
    String key = UUID.randomUUID().toString();
    store.tryLock(
        "idem:v1:anonymous:POST:" + PATH + ":" + key,
        Fingerprint.of("{}".getBytes()),
        Duration.ofSeconds(30));

    MockHttpServletResponse response = run(post("{}", key));

    assertThat(response.getStatus()).isEqualTo(409);
    assertThat(invocations).isZero();
  }

  @Test
  void serverErrorsAreNotStoredSoTheClientCanRetry() throws Exception {
    String key = UUID.randomUUID().toString();
    MockHttpServletRequest request = post("{}", key);
    request.setAttribute("status", 503);

    run(request);
    MockHttpServletResponse retry = run(post("{}", key));

    assertThat(invocations).isEqualTo(2);
    assertThat(retry.getStatus()).isEqualTo(201);
  }

  @Test
  void readsAndOtherPathsAreNotFiltered() throws Exception {
    MockHttpServletRequest get = new MockHttpServletRequest("GET", PATH);
    assertThat(filter.shouldNotFilter(get)).isTrue();
    MockHttpServletRequest outside = new MockHttpServletRequest("POST", "/actuator/refresh");
    assertThat(filter.shouldNotFilter(outside)).isTrue();
  }

  // --- helpers -------------------------------------------------------------------------------

  private MockHttpServletRequest post(String body, String key) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH);
    request.setRequestURI(PATH);
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    request.setContentType("application/json");
    if (key != null) {
      request.addHeader(IdempotencyFilter.HEADER, key);
    }
    return request;
  }

  private MockHttpServletResponse run(MockHttpServletRequest request)
      throws ServletException, IOException {
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain =
        new MockFilterChain(
            new jakarta.servlet.http.HttpServlet() {
              @Override
              protected void service(
                  jakarta.servlet.http.HttpServletRequest req,
                  jakarta.servlet.http.HttpServletResponse res)
                  throws IOException {
                invocations++;
                Object forcedStatus = req.getAttribute("status");
                res.setStatus(forcedStatus instanceof Integer s ? s : 201);
                res.setContentType("application/json");
                res.getWriter().write("{\"invocation\":" + invocations + "}");
              }
            });
    filter.doFilter(request, response, chain);
    return response;
  }

  private static ObjectProvider<Tracer> noTracer() {
    return new ObjectProvider<>() {
      @Override
      public Tracer getObject() {
        throw new UnsupportedOperationException();
      }

      @Override
      public Tracer getIfAvailable() {
        return null;
      }
    };
  }

  private static final class InMemoryStore implements IdempotencyStore {
    private final Map<String, IdempotencyRecord> data = new HashMap<>();

    @Override
    public Optional<IdempotencyRecord> find(String key) {
      return Optional.ofNullable(data.get(key));
    }

    @Override
    public boolean tryLock(String key, String fingerprint, Duration ttl) {
      return data.putIfAbsent(key, IdempotencyRecord.inProgress(fingerprint)) == null;
    }

    @Override
    public void complete(String key, IdempotencyRecord record, Duration ttl) {
      data.put(key, record);
    }

    @Override
    public void release(String key) {
      data.remove(key);
    }
  }
}
