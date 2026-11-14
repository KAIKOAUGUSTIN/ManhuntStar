// Settings shared by every module (core, paper and the mods).
//
// The version lives in gradle.properties and is bumped by the release workflow.
// Dependency versions live in gradle/libs.versions.toml (read by Dependabot).
// The plugin's and the mods' jars end up in build/libs/ here at the root.

plugins {
    // "clean" also removes build/libs/ at the root, where the plugin jar goes
    base
    alias(libs.plugins.sonarqube)
}

// FastStats project token (https://faststats.dev), put into the plugin and the mods at build time
// (manhuntstar.properties). It comes from the FASTSTATS_TOKEN environment variable (the secret of the same
// name in the Release workflow) or -PfaststatsToken, and is not in the repository: forks, pull requests
// and local builds have none, and their jars send no statistics.
val faststatsToken = providers.environmentVariable("FASTSTATS_TOKEN")
    .orElse(providers.gradleProperty("faststatsToken"))
    .map { it.trim() }
    .getOrElse("")
require(faststatsToken.isEmpty() || Regex("[a-z0-9]{32}").matches(faststatsToken)) {
    "FASTSTATS_TOKEN isn't a FastStats project token (32 lowercase letters and digits)"
}
extra["faststatsToken"] = faststatsToken

subprojects {
    group = "dev.manhuntstar"
    version = rootProject.version

    repositories {
        mavenCentral()
        // the FastStats SDK (Paper plugin and mods) is only on FastStats' repository
        exclusiveContent {
            forRepository {
                maven("https://repo.faststats.dev/releases") { name = "FastStats" }
            }
            filter {
                includeGroup("dev.faststats.metrics")
            }
        }
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
        property("sonar.projectKey", "KAIKOAUGUSTIN_ManhuntStar")
        property("sonar.organization", "kaikoaugustin")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}

// Each Minecraft version's project of a mod module (:fabric:26.2, :fabric:26.3...) compiles the same
// sources, which SonarQube can't index twice: they are analyzed once, in the newest version's project.
val newestMinecraft = providers.gradleProperty("minecraftVersions").get().split(",").last().trim()
subprojects {
    if (parent != rootProject && name != newestMinecraft) {
        sonar {
            isSkipProject = true
        }
    }
}
