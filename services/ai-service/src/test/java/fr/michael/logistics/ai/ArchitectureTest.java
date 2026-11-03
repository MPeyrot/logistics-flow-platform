package fr.michael.logistics.ai;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Fait respecter les règles hexagonales décrites dans .claude/rules/architecture.md. */
@AnalyzeClasses(packages = "fr.michael.logistics.ai", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMAIN = "..ai.domain..";
    private static final String APPLICATION = "..ai.application..";
    private static final String INFRASTRUCTURE = "..ai.infrastructure..";

    @ArchTest
    static final ArchRule domainIgnoresFrameworks = noClasses().that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "com.anthropic..", "com.fasterxml..", "tools.jackson..")
            .because("le domaine ne dépend d'aucun framework ni du SDK Claude");

    @ArchTest
    static final ArchRule domainIgnoresOtherLayers = noClasses().that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(APPLICATION, INFRASTRUCTURE);

    @ArchTest
    static final ArchRule applicationIgnoresInfrastructure = noClasses().that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAPackage(INFRASTRUCTURE)
            .because("l'application passe par des ports, jamais par un adaptateur");

    @ArchTest
    static final ArchRule applicationIgnoresClaudeSdkAndWeb = noClasses().that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.anthropic..", "org.springframework.web..", "jakarta.validation..", "tools.jackson..")
            .because("ni SDK Claude, ni HTTP, ni validation de données externes dans les cas d'usage");

    @ArchTest
    static final ArchRule securityStaysInInfrastructure = noClasses()
            .that().resideInAnyPackage(DOMAIN, APPLICATION)
            .should().dependOnClassesThat().resideInAPackage("org.springframework.security..")
            .because("l'authentification est un détail d'infrastructure");

    @ArchTest
    static final ArchRule claudeSdkStaysInClaudeAdapter = noClasses()
            .that().resideOutsideOfPackage("..ai.infrastructure.claude..")
            .should().dependOnClassesThat().resideInAPackage("com.anthropic..")
            .because("un seul adaptateur parle à l'API Claude");

    @ArchTest
    static final ArchRule webIgnoresClaudeAdapter = noClasses().that().resideInAPackage("..ai.infrastructure.web..")
            .should().dependOnClassesThat().resideInAPackage("..ai.infrastructure.claude..")
            .because("le contrôleur passe par le cas d'usage, pas par l'adaptateur");
}
