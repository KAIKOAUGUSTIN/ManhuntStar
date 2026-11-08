pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        // Loom (the Fabric build plugin) is only on Fabric's repository
        exclusiveContent {
            forRepository {
                maven("https://maven.fabricmc.net") { name = "Fabric" }
            }
            filter {
                includeGroupAndSubgroups("net.fabricmc")
            }
        }
    }
}

rootProject.name = "ManhuntStar"

// core: code that doesn't depend on any platform (no Paper, no Minecraft).
// paper: the Paper plugin, built on top of core (one jar for every supported Minecraft version).
include("core", "paper")

// The mods are built once per Minecraft version (minecraftVersions in gradle.properties), each in its own
// project: :mod-common:26.3, :fabric:26.3, :neoforge:26.3 and so on. A module's build.gradle.kts is the
// build of each of its versions (the module itself builds nothing), and the module's sources are shared by
// all of them; what changed between Minecraft versions goes in <module>/versions/<version>/src/.
// mod-common: the mods' code that only uses Minecraft itself (the same for Fabric and NeoForge).
// fabric: the Fabric mod (mod-common's code compiled with Fabric's Minecraft, plus core).
// neoforge: the NeoForge mod (the same with NeoForge).
val minecraftVersions = providers.gradleProperty("minecraftVersions").get().split(",").map { it.trim() }
for (module in listOf("mod-common", "fabric", "neoforge")) {
    include(module)
    project(":$module").buildFileName = "versions.gradle.kts"
    for (minecraft in minecraftVersions) {
        include("$module:$minecraft")
        project(":$module:$minecraft").apply {
            // where that version's build goes (and its own sources, if any)
            projectDir = file("$module/versions/$minecraft").also { it.mkdirs() }
            buildFileName = "../../build.gradle.kts"
        }
    }
}
