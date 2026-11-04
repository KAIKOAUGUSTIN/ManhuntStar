plugins {
    java
    alias(libs.plugins.shadow)
}

// The Paper plugin. Shared settings (version, Java 25, tests) are in the root build.gradle.kts.

description = "Manhunt* - ManHunt hunter bots for Paper"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

// Supported Paper versions: compiled against the oldest (paper-api-min) and checked against the newest
// (paper-api). One jar runs on both; paper/src/main/java/dev/manhuntstar/util/PaperCompat.java
// handles what differs between them. core doesn't see the Paper API, so it doesn't change any of this.
val paperMin = libs.versions.paper.api.min.get()
val apiVersion = Regex("""^(\d+\.\d+)""").find(paperMin)?.groupValues?.get(1)
    ?: error("Can't read the api-version from paper-api-min ($paperMin)")

val paperLatest = configurations.create("paperLatest")

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperMin")
    add(paperLatest.name, libs.paper.api)

    // bundled in the jar: core as is, bStats relocated (see shadowJar)
    implementation(project(":core"))
    implementation(libs.bstats.bukkit)

    testImplementation(libs.paper.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "apiVersion" to apiVersion,
        "bstatsId" to (findProperty("bstatsId")?.toString() ?: "0"),
    )
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching(listOf("plugin.yml", "manhuntstar.properties")) {
        expand(props)
    }
}

// Compiles the same code against the newest Paper API, to catch anything it removed or changed.
val compileAgainstLatest = tasks.register<JavaCompile>("compileAgainstLatest") {
    description = "Compiles the plugin against the newest supported Paper API."
    group = "verification"
    source = sourceSets.main.get().java
    classpath = files(paperLatest, configurations.runtimeClasspath)
    destinationDirectory.set(layout.buildDirectory.dir("compat/latest"))
}

tasks.check {
    dependsOn(compileAgainstLatest)
}

// The plugin jar is the shadow jar: the plugin, core and bStats, moved into its own package so it
// doesn't clash with other plugins' copies (bStats refuses to start when it isn't relocated).
// It goes to build/libs/ at the root, where the workflows and the README expect it.
tasks.shadowJar {
    archiveFileName.set("ManhuntStar-${project.version}.jar")
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("libs"))
    relocate("org.bstats", "dev.manhuntstar.libs.bstats")
    exclude("META-INF/maven/**", "META-INF/LICENSE*", "META-INF/NOTICE*")
}

tasks.jar {
    enabled = false
}
