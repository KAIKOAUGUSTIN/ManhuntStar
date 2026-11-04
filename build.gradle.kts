// Settings shared by every module (core, paper).
//
// The version lives in gradle.properties and is bumped by the release workflow.
// Dependency versions live in gradle/libs.versions.toml (read by Dependabot).
// The plugin jar ends up in build/libs/ here at the root (see paper/build.gradle.kts).

plugins {
    // "clean" also removes build/libs/ at the root, where the plugin jar goes
    base
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
}
