// Settings shared by every module (core, paper).
//
// The version lives in gradle.properties and is bumped by the release workflow.
// Dependency versions live in gradle/libs.versions.toml (read by Dependabot).
// The plugin jar ends up in build/libs/ here at the root (see paper/build.gradle.kts).

plugins {
    // "clean" also removes build/libs/ at the root, where the plugin jar goes
    base
    alias(libs.plugins.sonarqube)
}

subprojects {
    group = "dev.manhuntstar"
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:removal"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    // Test coverage for SonarQube Cloud: the sonar task reads each module's XML report.
    pluginManager.withPlugin("java") {
        apply(plugin = "jacoco")
        tasks.named<Test>("test") {
            finalizedBy(tasks.named("jacocoTestReport"))
        }
        tasks.named<JacocoReport>("jacocoTestReport") {
            dependsOn(tasks.named("test"))
            reports {
                xml.required.set(true)
            }
        }
    }
}

// SonarQube Cloud analysis (./gradlew build sonar, run by the Build workflow). The token comes from
// the SONAR_TOKEN environment variable (the SONAR_TOKEN secret in the workflow).
sonar {
    properties {
        property("sonar.projectKey", "kaikoaugustin_manhunt-star")
        property("sonar.organization", "kaikoaugustin")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}
