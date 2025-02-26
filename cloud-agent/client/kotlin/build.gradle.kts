plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
    id("maven-publish")
    id("signing")
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

val publishedMavenId: String = "org.hyperledger.identus"
group = publishedMavenId

repositories {
    mavenLocal()
    mavenCentral()
    google()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.23")
    implementation("com.google.code.gson:gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.4.2")
}

java {
    withJavadocJar()
    withSourcesJar()
}

kotlin {
    jvmToolchain(11)
}

publishing {
    publications {
        create<MavenPublication>(rootProject.name) {
            groupId = publishedMavenId
            artifactId = project.name
            version = project.version.toString()
            from(components["java"])
            pom {
                name.set("Hyperledger Identus Cloud Agent HTTP Client")
                description.set("The HTTP client stub for the Hyperledger Identus Cloud Agent generated based on OpenAPI specification")
                url.set("https://hyperledger-identus.github.io/docs/")
                organization {
                    name.set("Hyperledger")
                    url.set("https://www.hyperledger.org/")
                }
                issueManagement {
                    system.set("Github")
                    url.set("https://github.com/hyperledger-identus/cloud-agent")
                }
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("FabioPinheiro")
                        name.set("Fabio Pinheiro")
                        email.set("fabio.pinheiro@iohk.io")
                        organization.set("IOG")
                        roles.add("developer")
                    }
                    developer {
                        id.set("amagyar-iohk")
                        name.set("Allain Magyar")
                        email.set("allain.magyar@iohk.io")
                        organization.set("IOG")
                        roles.add("developer")
                    }
                    developer {
                        id.set("yshyn-iohk")
                        name.set("Yurii Shynbuiev")
                        email.set("yurii.shynbuiev@iohk.io")
                        organization.set("IOG")
                        roles.add("developer")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/hyperledger-identus/cloud-agent.git")
                    developerConnection.set("scm:git:ssh://github.com/hyperledger-identus/cloud-agent.git")
                    url.set("https://github.com/hyperledger-identus/cloud-agent")
                }
            }
        }
    }
}

if (System.getenv().containsKey("GPG_PRIVATE") && System.getenv().containsKey("GPG_PASSWORD")) {
    signing {
        useInMemoryPgpKeys(
            project.findProperty("signing.signingSecretKey") as String? ?: System.getenv("GPG_PRIVATE"),
            project.findProperty("signing.signingSecretKeyPassword") as String? ?: System.getenv("GPG_PASSWORD"),
        )
        sign(publishing.publications)
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://oss.sonatype.org/content/repositories/snapshots/"))
            username.set(System.getenv("OSSRH_USERNAME"))
            password.set(System.getenv("OSSRH_PASSWORD"))
        }
    }
}
