import java.util.*

plugins {
    id("java")
    id("maven-publish")
}

group = "fr.traqueur"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.yaml:snakeyaml:2.4")
    compileOnly("org.jetbrains:annotations:26.0.2")
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
