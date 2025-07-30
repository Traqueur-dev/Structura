import java.util.*

plugins {
    id("java-library")
    id("maven-publish")
}

group = "fr.traqueur"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.yaml:snakeyaml:2.4")

    testImplementation("org.yaml:snakeyaml:2.4")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
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



val targetJavaVersion = 21
java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

tasks.register("generateVersionProperties") {
    doLast {
        val name = project.name.lowercase(Locale.getDefault())
        val file = project.file("src/main/resources/$name.properties")
        file.parentFile?.mkdirs()
        file.writeText("version=${project.version}")
    }
}

tasks.processResources {
    dependsOn("generateVersionProperties")
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
