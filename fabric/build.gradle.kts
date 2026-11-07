plugins {
    java
    alias(libs.plugins.fabric.loom)
}

// The Fabric mod: mod-common's sources compiled with Fabric's Minecraft, the Fabric-only code, and core.
// One jar per Minecraft version, in build/libs/ at the root next to the plugin's.

description = "Manhunt* - ManHunt hunter bots for Fabric"

val minecraft = libs.versions.minecraft.get()

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
    minecraft("com.mojang:minecraft:$minecraft")
    implementation("net.fabricmc:fabric-loader:${libs.versions.fabric.loader.get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${libs.versions.fabric.api.get()}")

    implementation(project(":core"))
    bundled(project(":core"))
    commonJava(project(path = ":mod-common", configuration = "commonJava"))
    commonResources(project(path = ":mod-common", configuration = "commonResources"))

    // inside the mod jar (jar-in-jar): Adventure for the mods, and SnakeYAML, which Minecraft doesn't have
    implementation("net.kyori:adventure-platform-fabric:${libs.versions.adventurePlatformMod.get()}")
    include("net.kyori:adventure-platform-fabric:${libs.versions.adventurePlatformMod.get()}")
    implementation("org.yaml:snakeyaml:${libs.versions.snakeyaml.get()}")
    include("org.yaml:snakeyaml:${libs.versions.snakeyaml.get()}")
}

tasks.compileJava {
    dependsOn(commonJava)
    source(commonJava)
}

tasks.processResources {
    dependsOn(commonResources)
    from(commonResources)
    val props = mapOf("version" to project.version.toString(), "minecraft" to minecraft)
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}

tasks.jar {
    dependsOn(bundled)
    from(provider { bundled.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF")
    }
    archiveFileName.set("ManhuntStar-fabric-${project.version}+$minecraft.jar")
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("libs"))
}

loom {
    runs {
        named("server") {
            runDir("run/server")
        }
    }
}
