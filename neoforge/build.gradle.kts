plugins {
    java
    alias(libs.plugins.moddev)
}

// The NeoForge mod: mod-common's sources compiled with NeoForge's Minecraft, the NeoForge-only code, and
// core. One jar per Minecraft version, in build/libs/ at the root next to the plugin's.

description = "Manhunt* - ManHunt hunter bots for NeoForge"

val minecraft = libs.versions.minecraft.get()

neoForge {
    version = libs.versions.neoforge.get()
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
    commonJava(project(path = ":mod-common", configuration = "commonJava"))
    commonResources(project(path = ":mod-common", configuration = "commonResources"))

    // inside the mod jar (jar-in-jar): Adventure for the mods, and SnakeYAML, which Minecraft doesn't have
    jarJar(implementation("net.kyori:adventure-platform-neoforge:${libs.versions.adventurePlatformMod.get()}")!!)
    jarJar(implementation("org.yaml:snakeyaml:${libs.versions.snakeyaml.get()}")!!)
}

tasks.compileJava {
    dependsOn(commonJava)
    source(commonJava)
}

tasks.processResources {
    dependsOn(commonResources)
    from(commonResources)
    val props = mapOf("version" to project.version.toString(), "minecraft" to minecraft, "neoforge" to libs.versions.neoforge.get())
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") {
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
