package com.uit.finance.architecture;

import com.uit.finance.FinanceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Rule 6: modules depend on each other only through declared, public APIs – no cycles. */
class ModularityTest {

  private static final ApplicationModules modules = ApplicationModules.of(FinanceApplication.class);

  @Test
  void modulesRespectDeclaredBoundaries() {
    modules.verify();
  }

  @Test
  void documentationIsGenerated() {
    // build/spring-modulith-docs: C4 component diagrams + module canvases, kept up to date by CI.
    new Documenter(modules).writeDocumentation();
  }
}
