plugins {
    id("java")
    id("com.gradleup.shadow") version "9.0.0-beta11"
}

group = "fr.traqueur"
version = rootProject.property("version") as String

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":"))
    implementation(project(":structura-writers"))
    implementation("org.yaml:snakeyaml:2.4")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.shadowJar {
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "fr.traqueur.example.Main"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
