repositories {
    mavenCentral()
}

dependencies {
    // Brings the core API and snakeyaml (declared as `api` in the core) transitively
    api(project(":"))

    testImplementation(project(":"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            groupId = project.group.toString()
            artifactId = "structura-writer"
            version = project.version.toString()

            pom {
                name.set("structura-writer")
                description.set("Reverse YAML serialization (writer) for Structura")
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
