plugins {
    `java-platform`
    `maven-publish`
}

description = "Structura BOM (Bill of Materials)"

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api(project(":"))                  // fr.traqueur.structura:structura-core
        api(project(":writer")) // fr.traqueur.structura:structura-writer
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])

            groupId = project.group.toString()
            artifactId = "structura-bom"
            version = project.version.toString()

            pom {
                name.set("structura-bom")
                description.set("Structura Bill of Materials — aligns versions across Structura modules")
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
