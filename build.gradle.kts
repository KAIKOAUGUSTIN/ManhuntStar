plugins {
    java
}

group = "dev.manhuntstar"
description = "Manhunt* - ManHunt hunter bots for Paper"

// The plugin version lives in gradle.properties and is bumped by the release workflow.
// Dependency versions live in gradle/libs.versions.toml (read by Dependabot).

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

// Supported Paper versions: compiled against the oldest (paper-api-min) and checked against the newest
// (paper-api). One jar runs on both; src/main/java/dev/manhuntstar/util/PaperCompat.java handles
// what differs between them.
val paperMin = libs.versions.paper.api.min.get()
val apiVersion = Regex("""^(\d+\.\d+)""").find(paperMin)?.groupValues?.get(1)
    ?: error("Can't read the api-version from paper-api-min ($paperMin)")

val paperLatest = configurations.create("paperLatest")

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperMin")
    add(paperLatest.name, libs.paper.api)

    testImplementation(libs.paper.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:removal"))
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString(), "apiVersion" to apiVersion)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

// Compiles the same code against the newest Paper API, to catch anything it removed or changed.
val compileAgainstLatest = tasks.register<JavaCompile>("compileAgainstLatest") {
    description = "Compiles the plugin against the newest supported Paper API."
    group = "verification"
    source = sourceSets.main.get().java
    classpath = paperLatest
    destinationDirectory.set(layout.buildDirectory.dir("compat/latest"))
}

tasks.check {
    dependsOn(compileAgainstLatest)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveFileName.set("ManhuntStar-${project.version}.jar")
}
