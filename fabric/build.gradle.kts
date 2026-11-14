plugins {
    java
    alias(libs.plugins.fabric.loom)
}

// The Fabric mod: mod-common's sources compiled with Fabric's Minecraft, the Fabric-only code, and core.
// One jar per Minecraft version, in build/libs/ at the root next to the plugin's.

description = "Manhunt* - ManHunt hunter bots for Fabric"

// This is the build of one Minecraft version (:fabric:26.3 and so on, see settings.gradle.kts), from the
// sources in src/ here.
val minecraft = project.name
// every module's versions are named alike (26.3), so the group tells them apart
group = "dev.manhuntstar.fabric"
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun forMinecraft(name: String): String = catalog.findVersion("mc${minecraft.replace(".", "")}-$name")
    .orElseThrow { GradleException("gradle/libs.versions.toml has no $name for Minecraft $minecraft") }.requiredVersion
val adventurePlatformMod = forMinecraft("adventure-platform-mod")
val faststats = "dev.faststats.metrics:fabric:${libs.versions.faststats.get()}+mc$minecraft"

sourceSets.main {
    java.srcDir(parent!!.file("src/main/java"))
    resources.srcDir(parent!!.file("src/main/resources"))
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
    minecraft("com.mojang:minecraft:$minecraft")
    implementation("net.fabricmc:fabric-loader:${libs.versions.fabric.loader.get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${forMinecraft("fabric-api")}")

    implementation(project(":core"))
    bundled(project(":core"))
    commonJava(project(path = ":mod-common:$minecraft", configuration = "commonJava"))
    commonResources(project(path = ":mod-common:$minecraft", configuration = "commonResources"))

    // inside the mod jar (jar-in-jar): Adventure for the mods, and SnakeYAML, which Minecraft doesn't have
    implementation("net.kyori:adventure-platform-fabric:$adventurePlatformMod")
    include("net.kyori:adventure-platform-fabric:$adventurePlatformMod")
    implementation("org.yaml:snakeyaml:${libs.versions.snakeyaml.get()}")
    include("org.yaml:snakeyaml:${libs.versions.snakeyaml.get()}")
    // and FastStats (anonymous statistics), a mod of its own built for each Minecraft version
    // (not its Gson: Minecraft brings Gson and pins its own version)
    implementation(faststats) {
        exclude(group = "com.google.code.gson")
    }
    include(faststats)
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
        "faststatsToken" to rootProject.extra["faststatsToken"].toString(),
    )
    inputs.properties(props)
    filesMatching(listOf("fabric.mod.json", "manhuntstar.properties")) {
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
