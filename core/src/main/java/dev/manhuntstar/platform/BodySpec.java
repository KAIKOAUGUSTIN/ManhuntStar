package dev.manhuntstar.platform;

import net.kyori.adventure.text.Component;

/**
 * How a bot's body is created (see {@link GameWorld#spawnBotBody}): its profile, the name over its head,
 * its health (also the maximum) and a persistent mark that tells it apart as a bot's body.
 */
public record BodySpec(BodyProfile profile, Component name, boolean nameVisible, double health, String markKey, String markValue) {
}
