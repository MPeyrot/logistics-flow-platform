package fr.michael.logistics.ingestion;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Fait respecter les règles hexagonales décrites dans .claude/rules/architecture.md. */
@AnalyzeClasses(packages = "fr.michael.logistics.ingestion", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMAIN = "..ingestion.domain..";
    private static final String APPLICATION = "..ingestion.application..";
    private static final String INFRASTRUCTURE = "..ingestion.infrastructure..";

    @ArchTest
    static final ArchRule domainIgnoresFrameworks = noClasses().that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "org.hibernate..", "org.apache.kafka..",
                    "com.fasterxml..", "tools.jackson..")
            .because("le domaine ne dépend d'aucun framework");

    @ArchTest
    static final ArchRule domainIgnoresOtherLayers = noClasses().that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(APPLICATION, INFRASTRUCTURE);

    @ArchTest
    static final ArchRule applicationIgnoresInfrastructure = noClasses().that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAPackage(INFRASTRUCTURE)
            .because("l'application passe par des ports, jamais par un adaptateur");

    @ArchTest
    static final ArchRule batchStaysInItsPackage = noClasses()
            .that().resideOutsideOfPackage("..ingestion.infrastructure.batch..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.batch..")
            .because("Spring Batch reste dans infrastructure/batch : domain et application l'ignorent");

    @ArchTest
    static final ArchRule applicationIgnoresPersistenceAndWeb = noClasses().that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.springframework.web..", "org.springframework.kafka..", "org.apache.kafka..", "org.springframework.security..")
            .because("ni JPA, ni HTTP, ni Kafka, ni Spring Security dans les cas d'usage");
}
