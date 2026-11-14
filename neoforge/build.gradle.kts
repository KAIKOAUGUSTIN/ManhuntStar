plugins {
    java
    alias(libs.plugins.moddev)
}

// The NeoForge mod: mod-common's sources compiled with NeoForge's Minecraft, the NeoForge-only code, and
// core. One jar per Minecraft version, in build/libs/ at the root next to the plugin's.

description = "Manhunt* - ManHunt hunter bots for NeoForge"

// This is the build of one Minecraft version (:neoforge:26.3 and so on, see settings.gradle.kts), from the
// sources in src/ here.
val minecraft = project.name
// every module's versions are named alike (26.3), so the group tells them apart
group = "dev.manhuntstar.neoforge"
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun forMinecraft(name: String): String = catalog.findVersion("mc${minecraft.replace(".", "")}-$name")
    .orElseThrow { GradleException("gradle/libs.versions.toml has no $name for Minecraft $minecraft") }.requiredVersion
val adventurePlatformMod = forMinecraft("adventure-platform-mod")

sourceSets.main {
    java.srcDir(parent!!.file("src/main/java"))
    resources.srcDir(parent!!.file("src/main/resources"))
}

neoForge {
    version = forMinecraft("neoforge")
    runs {
        create("server") {
            server()
            gameDirectory = file("run/server")
        }
    }
    mods {
        create("manhuntstar") {
            sourceSet(sourceSets.main.get())
        }
    }
}

val commonJava by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}
val commonResources by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}
// core goes into the mod jar as classes (it is part of the mod, not a library of its own)
val bundled by configurations.creating {
    isTransitive = false
}

dependencies {
    implementation(project(":core"))
    bundled(project(":core"))
    commonJava(project(path = ":mod-common:$minecraft", configuration = "commonJava"))
    commonResources(project(path = ":mod-common:$minecraft", configuration = "commonResources"))

    // inside the mod jar (jar-in-jar): Adventure for the mods, and SnakeYAML, which Minecraft doesn't have
    jarJar(implementation("net.kyori:adventure-platform-neoforge:$adventurePlatformMod")!!)
    jarJar(implementation("org.yaml:snakeyaml:${libs.versions.snakeyaml.get()}")!!)
    // and FastStats (anonymous statistics), a mod of its own built for each Minecraft version
    jarJar(implementation("dev.faststats.metrics:neoforge:${libs.versions.faststats.get()}+mc$minecraft")!!)
}

tasks.compileJava {
    dependsOn(commonJava)
    source(commonJava)
}

tasks.processResources {
    dependsOn(commonResources)
    from(commonResources)
    val props = mapOf(
        "version" to project.version.toString(),
        "minecraft" to minecraft,
        "neoforge" to forMinecraft("neoforge"),
        "faststatsToken" to rootProject.extra["faststatsToken"].toString(),
    )
    inputs.properties(props)
    filesMatching(listOf("META-INF/neoforge.mods.toml", "manhuntstar.properties")) {
        expand(props)
    }
}

tasks.jar {
    dependsOn(bundled)
    from(provider { bundled.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF")
    }
    archiveFileName.set("ManhuntStar-neoforge-${project.version}+$minecraft.jar")
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("libs"))
}
