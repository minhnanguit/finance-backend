package com.uit.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.modules.notification.application.port.out.NotificationSenderPort;
import com.uit.finance.modules.notification.domain.model.Notification;
import com.uit.finance.modules.notification.domain.model.NotificationKind;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Chứng minh toàn bộ event flow: JIT provisioning → outbox → RabbitMQ → notification consumer →
 * dedup → welcome message. Chỉ mock delivery channel cuối cùng.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, UserRegisteredEventIT.RecordingSenderConfig.class})
class UserRegisteredEventIT {

  @LocalServerPort int port;
  @Autowired RecordingSender sender;
  @Autowired JdbcClient jdbc;

  @DynamicPropertySource
  static void identityProvider(DynamicPropertyRegistry registry) {
    TestIdentityProvider idp = TestIdentityProvider.instance();
    registry.add("app.security.issuer-uri", () -> TestIdentityProvider.ISSUER);
    registry.add("app.security.jwk-set-uri", idp::jwkSetUri);
    registry.add("app.security.audience", () -> TestIdentityProvider.AUDIENCE);
  }

  @Test
  void firstLoginReachesTheNotificationModuleExactlyOnce() {
    String subject = "sub-" + UUID.randomUUID();
    String email = "event-" + UUID.randomUUID() + "@example.com";
    ApiClient api = new ApiClient(port);

    var response =
        api.get("/api/v1/me", TestIdentityProvider.instance().validToken(subject, email, "Alice"));
    assertThat(response.getStatusCode().value()).isEqualTo(200);

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              List<Notification> welcome =
                  sender.sent.stream().filter(n -> n.recipient().email().equals(email)).toList();
              assertThat(welcome).hasSize(1);
              assertThat(welcome.getFirst().kind()).isEqualTo(NotificationKind.WELCOME);
            });

    // Consumer đã ghi event id, nên nếu broker redeliver thì sẽ bị skip.
    Integer processed =
        jdbc.sql("select count(*) from processed_events where consumer = :c")
            .param("c", "notification.user-registered")
            .query(Integer.class)
            .single();
    assertThat(processed).isGreaterThanOrEqualTo(1);

    // Outbox đã đánh dấu externalization hoàn tất (archive mode).
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              Integer archived =
                  jdbc.sql("select count(*) from event_publication_archive")
                      .query(Integer.class)
                      .single();
              assertThat(archived).isGreaterThanOrEqualTo(1);
            });
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class RecordingSenderConfig {
    @Bean
    @Primary
    RecordingSender recordingSender() {
      return new RecordingSender();
    }
  }

  static class RecordingSender implements NotificationSenderPort {
    final List<Notification> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(Notification notification) {
      sent.add(notification);
    }
  }
}
