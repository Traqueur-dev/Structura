plugins {
    id("java-library")
}

group = "fr.traqueur"
version = rootProject.property("version") as String

repositories {
    mavenCentral()
}

dependencies {
    api(project(":"))
    compileOnly("org.yaml:snakeyaml:2.4")

    testImplementation(project(":"))
    testImplementation("org.yaml:snakeyaml:2.4")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
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
