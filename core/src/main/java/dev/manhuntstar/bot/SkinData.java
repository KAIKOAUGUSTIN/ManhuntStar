package dev.manhuntstar.bot;

/** Textura assinada de skin (propriedade "textures" do perfil). */
public record SkinData(String value, String signature) {

    public boolean valid() {
        return value != null && !value.isBlank();
    }
}
