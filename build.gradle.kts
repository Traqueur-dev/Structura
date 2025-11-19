import java.util.*

plugins {
    id("java-library")
    id("re.alwyn974.groupez.publish") version "1.0.0"
    id("com.gradleup.shadow") version "9.0.0-beta11"
}

group = "fr.traqueur"
version = property("version")!!

extra.set("targetFolder", file("target/"))
extra.set("classifier", System.getProperty("archive.classifier"))
extra.set("sha", System.getProperty("github.sha"))

rootProject.extra.properties["sha"]?.let { sha ->
    version = sha
}

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

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.shadowJar {
    rootProject.extra.properties["sha"]?.let { sha ->
        archiveClassifier.set("${rootProject.extra.properties["classifier"]}-${sha}")
    } ?: run {
        archiveClassifier.set(rootProject.extra.properties["classifier"] as String?)
    }
    destinationDirectory.set(rootProject.extra["targetFolder"] as File)
}

tasks.processResources {
    dependsOn("generateVersionProperties")
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishConfig {
    githubOwner = "Traqueur-dev"
    useRootProjectName = true
}