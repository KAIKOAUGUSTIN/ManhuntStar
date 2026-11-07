package dev.manhuntstar.host;

import dev.manhuntstar.gui.MenuScreens;

/** The features only the platform has, which core uses through these interfaces. */
public record PlatformServices(PathDebug debugRenderer, BotTabList tabList, BotGlow glow, DeathMessageSource deathMessages,
        BotEventSink events, AimDiagnostics aim, MenuScreens menuScreens) {
}
