package dev.manhuntstar.paper;

import dev.manhuntstar.platform.GameSound;
import dev.manhuntstar.platform.SoundCategory;
import dev.manhuntstar.platform.SoundGroup;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

/** Conversions between core sounds and Bukkit's {@link Sound} and {@link org.bukkit.SoundCategory}. */
public final class PaperSounds {

    private static final org.bukkit.SoundCategory[] CATEGORIES = new org.bukkit.SoundCategory[SoundCategory.values().length];

    static {
        for (SoundCategory c : SoundCategory.values()) {
            CATEGORIES[c.ordinal()] = org.bukkit.SoundCategory.valueOf(c.name());
        }
    }

    private PaperSounds() {
    }

    /** The Bukkit sound with the core sound's id (the same object as the {@code Sound} constant). */
    public static Sound sound(GameSound s) {
        if (s.handle() instanceof Sound sound) {
            return sound;
        }
        Sound sound = Registry.SOUND_EVENT.get(NamespacedKey.minecraft(s.key()));
        if (sound == null) {
            throw new IllegalArgumentException("unknown sound: " + s.key());
        }
        s.setHandle(sound);
        return sound;
    }

    public static GameSound of(Sound sound) {
        GameSound s = GameSound.of(Registry.SOUND_EVENT.getKeyOrThrow(sound).getKey());
        if (s.handle() == null) {
            s.setHandle(sound);
        }
        return s;
    }

    public static org.bukkit.SoundCategory category(SoundCategory c) {
        return CATEGORIES[c.ordinal()];
    }

    public static SoundGroup of(org.bukkit.SoundGroup g) {
        return new SoundGroup(of(g.getBreakSound()), of(g.getHitSound()), of(g.getPlaceSound()), g.getVolume(), g.getPitch());
    }
}
