package dev.manhuntstar.bot;

/** Signed skin texture (the profile's "textures" property). */
public record SkinData(String value, String signature) {

    public boolean valid() {
        return value != null && !value.isBlank();
    }
}
