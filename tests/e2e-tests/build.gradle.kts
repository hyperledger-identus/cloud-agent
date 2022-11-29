plugins {
    kotlin("jvm") version "1.7.20"
    idea
    jacoco
    id("net.serenity-bdd.serenity-gradle-plugin") version "3.4.2"
}

repositories {
    mavenCentral()
}

dependencies {
    // Logging
    implementation("org.slf4j:slf4j-log4j12:2.0.3")
    // Beautify async waits
    implementation("org.awaitility:awaitility-kotlin:4.2.0")
    // Test engines and reports
    testImplementation("junit:junit:4.13.2")
    testImplementation("net.serenity-bdd:serenity-core:3.4.2")
    testImplementation("net.serenity-bdd:serenity-cucumber:3.4.2")
    implementation("net.serenity-bdd:serenity-single-page-report:3.4.2")
    implementation("net.serenity-bdd:serenity-json-summary-report:3.4.2")
    testImplementation("net.serenity-bdd:serenity-screenplay-rest:3.4.2")
    // Beautify exceptions handling assertions
    testImplementation("org.assertj:assertj-core:3.23.1")
}

/**
 * Add HTML one-pager and JSON summary report to be produced
 */
serenity {
    reports = listOf("single-page-html", "json-summary")
}
