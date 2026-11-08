plugins {
    `java-library`
    alias(libs.plugins.moddev)
}

// The mods' code that only uses Minecraft itself, the same for Fabric and NeoForge (Minecraft isn't
// obfuscated since 26.1, so both loaders use the same names). It compiles here against plain Minecraft
// (NeoForm), which keeps loader code out of it, and its sources compile again inside each loader's module
// with that loader's Minecraft, as the MultiLoader template does.

description = "Manhunt* - code shared by the Fabric and NeoForge mods"

repositories {
    exclusiveContent {
        forRepository {
            maven("https://maven.fabricmc.net") { name = "Fabric" }
        }
        filter {
            includeGroupAndSubgroups("net.fabricmc")
        }
    }
}

neoForge {
    neoFormVersion = libs.versions.neoform.get()
}

val adventurePlatformMod = libs.versions.adventurePlatformMod.get()
val mixinextras = "io.github.llamalad7:mixinextras-common:${libs.versions.mixinextras.get()}"

dependencies {
    compileOnly(project(":core"))
    compileOnly("net.kyori:adventure-platform-mod-shared:$adventurePlatformMod")
    compileOnly("org.yaml:snakeyaml:${libs.versions.snakeyaml.get()}")
    // Mixin and MixinExtras come with both loaders
    compileOnly("net.fabricmc:sponge-mixin:${libs.versions.sponge.mixin.get()}")
    compileOnly(mixinextras)
    annotationProcessor(mixinextras)
}

// What the loaders' modules take: the sources and resources, to compile and pack them themselves
val commonJava by configurations.creating {
    isCanBeResolved = false
    isCanBeConsumed = true
}
val commonResources by configurations.creating {
    isCanBeResolved = false
    isCanBeConsumed = true
}

artifacts {
    add(commonJava.name, sourceSets.main.get().java.sourceDirectories.singleFile)
    add(commonResources.name, sourceSets.main.get().resources.sourceDirectories.singleFile)
}
