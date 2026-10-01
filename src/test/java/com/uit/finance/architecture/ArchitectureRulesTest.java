package com.uit.finance.architecture;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.uit.finance.shared.kernel.UserId;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Clean Architecture rules from ARCHITECTURE.md §4.3, executable (rule 6 lives in {@code
 * ModularityTest}). A violation fails the build.
 */
@AnalyzeClasses(packages = "com.uit.finance", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

  private static final String DOMAIN = "..domain..";
  private static final String APPLICATION = "..application..";
  private static final String ADAPTER = "..adapter..";
  private static final String KERNEL = "..shared.kernel..";
  private static final String[] FRAMEWORK_PACKAGES = {
    "org.springframework..",
    "jakarta..",
    "javax..",
    "tools.jackson..",
    "com.fasterxml..",
    "org.hibernate..",
    "com.nimbusds..",
    "com.rabbitmq.."
  };
  private static final String[] FRAMEWORK_SHARED_PACKAGES = {
    "..shared.web..", "..shared.persistence..", "..shared.security..", "..shared.messaging.."
  };

  /** Modules whose data belongs to one user. Add a module here when it gets user-owned tables. */
  private static final String[] USER_SCOPED_OUT_PORTS = {"..modules.ledger.application.port.out.."};

  @ArchTest
  static final ArchRule rule1_domainAndKernelAreFrameworkFree =
      noClasses()
          .that()
          .resideInAnyPackage(DOMAIN, KERNEL)
          .and()
          .doNotHaveSimpleName("package-info") // carries Modulith metadata only
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_PACKAGES)
          .because("domain logic must be testable with plain JUnit and portable across frameworks");

  @ArchTest
  static final ArchRule rule2_jpaEntitiesLiveOnlyInPersistenceAdapters =
      classes()
          .that()
          .areAnnotatedWith(Entity.class)
          .or()
          .areAnnotatedWith(MappedSuperclass.class)
          .should()
          .resideInAnyPackage("..adapter.out.persistence..", "..shared.persistence..")
          .because(
              "JPA entities are a persistence detail; domain models are mapped, never annotated");

  @ArchTest
  static final ArchRule rule3_applicationDependsOnlyOnDomainAndItsOwnPorts =
      classes()
          .that()
          .resideInAPackage(APPLICATION)
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(
              APPLICATION,
              DOMAIN,
              KERNEL,
              "java..",
              "org.jspecify..",
              "org.slf4j..",
              "org.springframework.stereotype..",
              "org.springframework.transaction.annotation..")
          .because("use cases orchestrate the domain through ports; they never see adapters");

  @ArchTest
  static final ArchRule rule4a_layersPointInwards =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Domain")
          .definedBy(DOMAIN)
          .layer("Application")
          .definedBy(APPLICATION)
          .layer("Adapter")
          .definedBy(ADAPTER)
          .whereLayer("Adapter")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Application")
          .mayOnlyBeAccessedByLayers("Adapter")
          .whereLayer("Domain")
          .mayOnlyBeAccessedByLayers("Application", "Adapter");

  @ArchTest
  static final ArchRule rule4b_inboundAdaptersDoNotTalkToOutboundAdapters =
      noClasses()
          .that()
          .resideInAPackage("..adapter.in..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapter.out..")
          .because("adapters communicate only through the application layer");

  @ArchTest
  static final ArchRule rule4c_outboundAdaptersDoNotTalkToInboundAdapters =
      noClasses()
          .that()
          .resideInAPackage("..adapter.out..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapter.in..");

  @ArchTest
  static final ArchRule rule5_portsAreInterfacesOrValueRecords =
      classes()
          .that()
          .resideInAPackage("..application.port..")
          .and()
          .areTopLevelClasses()
          .should()
          .beInterfaces()
          .orShould()
          .beRecords()
          .because("ports are narrow contracts (one per use case), commands/results are records");

  // Rule 6 (module boundaries) is enforced by Spring Modulith in ModularityTest.

  @ArchTest
  static final ArchRule rule7_frameworkSharedPackagesAreForAdaptersOnly =
      noClasses()
          .that()
          .resideInAnyPackage(DOMAIN, APPLICATION, KERNEL)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORK_SHARED_PACKAGES)
          .because("shared.web/persistence/security/messaging are adapters of the platform");

  @ArchTest
  static final ArchRule rule8_transactionsAreDefinedOnlyInApplicationServices =
      classes()
          .that()
          .areAnnotatedWith(Transactional.class)
          .or()
          .containAnyMethodsThat(annotatedWith(Transactional.class))
          .should()
          .resideInAPackage("..application.service..")
          .because(
              "the use case is the unit of work; adapters must not open business transactions");

  @ArchTest
  static final ArchRule rule10_userScopedOutPortsAlwaysTakeTheOwner =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAnyPackage(USER_SCOPED_OUT_PORTS)
          .and()
          .areDeclaredInClassesThat()
          .areInterfaces()
          .and()
          .doNotHaveModifier(JavaModifier.SYNTHETIC)
          .should(takeAParameterOfType(UserId.class))
          .because(
              "every query and write on user data must be scoped to its owner, or user A can read"
                  + " or overwrite user B's ledger by id (ADR-006 B1, OWASP API1)");

  private static ArchCondition<JavaMethod> takeAParameterOfType(Class<?> type) {
    return new ArchCondition<>("take a parameter of type " + type.getSimpleName()) {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        boolean found =
            method.getRawParameterTypes().stream().anyMatch(param -> param.isEquivalentTo(type));
        if (!found) {
          events.add(
              SimpleConditionEvent.violated(
                  method, method.getFullName() + " has no " + type.getSimpleName() + " parameter"));
        }
      }
    };
  }
}
