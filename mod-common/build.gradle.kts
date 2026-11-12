plugins {
    `java-library`
    alias(libs.plugins.moddev)
}

// The mods' code that only uses Minecraft itself, the same for Fabric and NeoForge (Minecraft isn't
// obfuscated since 26.1, so both loaders use the same names). It compiles here against plain Minecraft
// (NeoForm), which keeps loader code out of it, and its sources compile again inside each loader's module
// with that loader's Minecraft, as the MultiLoader template does.
//
// This is the build of one Minecraft version (:mod-common:26.3 and so on, see settings.gradle.kts): the
// sources in src/ here, plus what changed in that version in versions/<version>/src/.

description = "Manhunt* - code shared by the Fabric and NeoForge mods"

val minecraft = project.name
// every module's versions are named alike (26.3), so the group tells them apart
group = "dev.manhuntstar.mod-common"
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun forMinecraft(name: String): String = catalog.findVersion("mc${minecraft.replace(".", "")}-$name")
    .orElseThrow { GradleException("gradle/libs.versions.toml has no $name for Minecraft $minecraft") }.requiredVersion

sourceSets.main {
    java.srcDir(parent!!.file("src/main/java"))
    resources.srcDir(parent!!.file("src/main/resources"))
}

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
    neoFormVersion = forMinecraft("neoform")
}

val adventurePlatformMod = forMinecraft("adventure-platform-mod")
val mixinextras = "io.github.llamalad7:mixinextras-common:${libs.versions.mixinextras.get()}"

dependencies {
    compileOnly(project(":core"))
    compileOnly("net.kyori:adventure-platform-mod-shared:$adventurePlatformMod")
    compileOnly("org.yaml:snakeyaml:${libs.versions.snakeyaml.get()}")
    // FastStats: each loader brings its own build of the SDK (jar-in-jar); the shared code only uses its API
    compileOnly(libs.faststats.core)
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
    sourceSets.main.get().java.srcDirs.filter { it.exists() }.forEach { add(commonJava.name, it) }
    sourceSets.main.get().resources.srcDirs.filter { it.exists() }.forEach { add(commonResources.name, it) }
}
