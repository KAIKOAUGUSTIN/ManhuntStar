plugins {
    java
}

group = "dev.manhuntstar"
version = "1.0.0"
description = "Manhunt* - bots caçadores de ManHunt para Paper"

// As versões ficam em gradle/libs.versions.toml (lidas pelo Dependabot).

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val compat263: Configuration by configurations.creating

dependencies {
    compileOnly(libs.paper.api)
    compat263("io.papermc.paper:paper-api:${libs.versions.paper.compat.get()}")

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
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

// Compila o mesmo código contra a API 26.3 (beta) para detectar quebras de API.
val compileAgainst263 by tasks.registering(JavaCompile::class) {
    description = "Compila o plugin contra a paper-api 26.3 para checar compatibilidade."
    group = "verification"
    source = sourceSets.main.get().java
    classpath = compat263
    destinationDirectory.set(layout.buildDirectory.dir("compat/26.3"))
}

tasks.check {
    dependsOn(compileAgainst263)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveFileName.set("ManhuntStar-${project.version}.jar")
}
