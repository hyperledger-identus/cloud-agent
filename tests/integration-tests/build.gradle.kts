plugins {
    kotlin("jvm") version "1.9.21"
    idea
    java
    id("net.serenity-bdd.serenity-gradle-plugin") version "4.0.46"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
}

group = "io.iohk.atala.prism"
version = "1.0-SNAPSHOT"

buildscript {
    dependencies {
        classpath("net.serenity-bdd:serenity-single-page-report:4.0.46")
        classpath("net.serenity-bdd:serenity-json-summary-report:4.0.46")
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/hyperledger-identus/cloud-agent/")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    // HTTP listener
    testImplementation("io.ktor:ktor-server-netty:2.3.0")
    testImplementation("io.ktor:ktor-client-apache:2.3.0")
    // RestAPI client
    testImplementation("org.hyperledger-identus:cloud-agent-client:1.40.0")
    // Test helpers library
    testImplementation("io.iohk.atala:atala-automation:0.4.0")
    // Hoplite for configuration
    testImplementation("com.sksamuel.hoplite:hoplite-core:2.7.5")
    testImplementation("com.sksamuel.hoplite:hoplite-hocon:2.7.5")
    // Kotlin compose
    testImplementation("org.testcontainers:testcontainers:1.19.1")
    // Crypto
    testImplementation("com.nimbusds:nimbus-jose-jwt:9.40")
    testImplementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    testImplementation("com.google.crypto.tink:tink:1.13.0")
    testImplementation("io.iohk.atala.prism.apollo:apollo-jvm:1.3.4")
    // OID4VCI
    testImplementation("org.htmlunit:htmlunit:4.3.0")
    testImplementation("eu.europa.ec.eudi:eudi-lib-jvm-openid4vci-kt:0.4.1")
}

serenity {
    reports = listOf("single-page-html", "json-summary")
}

tasks.register<Delete>("cleanTarget") {
    group = "verification"
    delete("target")
}

tasks.test {
    dependsOn("cleanTarget")
    finalizedBy("aggregate", "reports")
    testLogging.showStandardStreams = true
    systemProperty("cucumber.filter.tags", System.getProperty("cucumber.filter.tags"))
    // Since the test runs on host and system-unter-test runs in containers,
    // We need to make the test on host resolves host.docker.internal same as the containerized services,
    // because some spec (e.g. OID4VCI) requires domain to be the same.
    //
    // The OID4VCI library does not allow mixing host.docker.internal and localhost
    systemProperty("jdk.net.hosts.file", "hosts_test")
}

kotlin {
    jvmToolchain(19)
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    version.set("1.2.1")
}

/**
 * Creates a new entry in verification group for each conf file
 */
afterEvaluate {
    val folderPath = "src/test/resources/configs" // Change this to the path of your folder

    val configFiles = fileTree(folderPath)
        .matching { include("**/*.conf") }
        .map { it.name.replace(".conf", "") }
        .toList()

    configFiles.forEach { fileName ->
        tasks.register<Test>("test_$fileName") {
            group = "verification"
            testLogging.showStandardStreams = true
            systemProperty("context", fileName)
            systemProperty("TESTS_CONFIG", "/configs/$fileName.conf")
            systemProperty("PRISM_NODE_VERSION", System.getenv("PRISM_NODE_VERSION") ?: "")
            systemProperty("AGENT_VERSION", System.getenv("AGENT_VERSION") ?: "")
            systemProperty("cucumber.filter.tags", System.getProperty("cucumber.filter.tags"))
            systemProperty("jdk.net.hosts.file", "hosts_test")
            finalizedBy("aggregate", "reports")
            outputs.upToDateWhen { false }
        }
    }

    /**
     * Runs the integration suite for each config file present
     * Restrictions: aggregation of all executions doesn't work because of serenity configuration
     */
    tasks.register<Test>("regression") {
        dependsOn("cleanTarget")
        group = "verification"
        configFiles.forEach {
            dependsOn(tasks.getByName("test_$it"))
        }
    }
}
