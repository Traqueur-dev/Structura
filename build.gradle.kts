import java.util.*

plugins {
    id("java-library")
    id("maven-publish")
}

allprojects {
    group = "fr.traqueur.structura"
    version = property("version") as String

    apply {
        if (project.name != "bom")
            plugin("java-library")
        // example is a runnable demo, not a published artifact
        if (project.name != "example")
            plugin("maven-publish")
    }

    // CI override: version becomes the short commit sha when building from the pipeline
    System.getProperty("github.sha")?.let { version = it }

    repositories {
        mavenCentral()
    }

    if (project.name != "bom") {

        dependencies {
            testImplementation("org.yaml:snakeyaml:2.6")
            testImplementation("org.junit.jupiter:junit-jupiter:6.1.0")
            // Gradle 9 no longer auto-provisions the JUnit Platform launcher; declare it explicitly.
            testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.0")
        }

        val targetJavaVersion = 21
        java {
            val javaVersion = JavaVersion.toVersion(targetJavaVersion)
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
            if (JavaVersion.current() < javaVersion) {
                toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
            }
            withSourcesJar()
            withJavadocJar()
        }

        tasks.test {
            useJUnitPlatform()
            jvmArgs = listOf("-XX:+EnableDynamicAgentLoading")

            reports {
                html.required.set(true)
                junitXml.required.set(true)
            }

            testLogging {
                showStandardStreams = true
                events("passed", "skipped", "failed", "standardOut", "standardError")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }

    // Centralized groupez publishing repository for any module applying maven-publish
    plugins.withId("maven-publish") {
        configure<PublishingExtension> {
            repositories {
                maven {
                    val repo = System.getProperty("repository.name", "snapshots")
                    name = "groupez${repo.replaceFirstChar { it.uppercase() }}"
                    url = uri("https://repo.groupez.dev/${repo.lowercase()}")
                    credentials {
                        username = (findProperty("${name}Username") as String?) ?: System.getenv("MAVEN_USERNAME")
                        password = (findProperty("${name}Password") as String?) ?: System.getenv("MAVEN_PASSWORD")
                    }
                    authentication {
                        create("basic", BasicAuthentication::class)
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Core module (root project) ─────────────────────────────

dependencies {
    api("org.yaml:snakeyaml:2.6")
}

// Generates src/main/resources/structura.properties — read at runtime by Updater.getVersion()
tasks.register("generateVersionProperties") {
    // Capture values at configuration time so doLast does not touch `project`
    // (required for the Gradle configuration cache / Gradle 10 compatibility).
    val propertiesFile = project.file(
        "src/main/resources/${project.name.lowercase(Locale.getDefault())}.properties"
    )
    val versionString = project.version.toString()
    doLast {
        propertiesFile.parentFile?.mkdirs()
        propertiesFile.writeText("version=$versionString")
    }
}

tasks.processResources {
    dependsOn("generateVersionProperties")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            groupId = project.group.toString()
            artifactId = "structura-core"
            version = project.version.toString()

            pom {
                name.set("structura-core")
                description.set("Type-safe YAML configuration library for Java 21+ (core)")
                url.set("https://github.com/Traqueur-dev/Structura")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("traqueur")
                        name.set("Traqueur")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/Traqueur-dev/Structura.git")
                    developerConnection.set("scm:git:ssh://github.com/Traqueur-dev/Structura.git")
                    url.set("https://github.com/Traqueur-dev/Structura")
                }
            }
        }
    }
}

// Aggregates the publish task of the root project and every publishable subproject
tasks.register("publishAll") {
    description = "Publishes all modules (core, writers, bom) to the Maven repository"
    group = "publishing"

    dependsOn(tasks.named("publish"))
    subprojects.forEach { sub ->
        sub.plugins.withId("maven-publish") {
            dependsOn(sub.tasks.named("publish"))
        }
    }
}
