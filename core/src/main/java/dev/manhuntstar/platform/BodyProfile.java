package dev.manhuntstar.platform;

import java.util.UUID;

/**
 * The game profile shown by a bot's body: name, id and, when it has a skin, the signed "textures"
 * property ({@code skinValue} null for the default skin).
 */
public record BodyProfile(String name, UUID id, String skinValue, String skinSignature) {
}
