plugins {
    id("java")
    id("com.gradleup.shadow") version "9.0.0-beta11"
}

group = "fr.traqueur"
version = rootProject.property("version") as String

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper API — provided at runtime by the server
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    // SnakeYAML is bundled by Paper, so compileOnly here too
    compileOnly("org.yaml:snakeyaml:2.4")

    // Structura core + writers — shaded into the jar
    implementation(project(":"))
    implementation(project(":structura-writers"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    // Relocate Structura so it doesn't conflict if another plugin uses it
    relocate("fr.traqueur.structura", "fr.traqueur.mctest.libs.structura")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
