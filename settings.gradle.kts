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
// paper: the Paper plugin, built on top of core.
// mod-common: the mods' code that only uses Minecraft itself (the same for Fabric and NeoForge).
// fabric: the Fabric mod (mod-common's code compiled with Fabric's, plus core).
// neoforge: the NeoForge mod (the same with NeoForge).
include("core", "paper", "mod-common", "fabric", "neoforge")
