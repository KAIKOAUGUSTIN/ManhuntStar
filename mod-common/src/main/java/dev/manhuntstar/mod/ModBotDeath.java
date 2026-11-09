package dev.manhuntstar.mod;

import dev.manhuntstar.platform.BotDeath;
import dev.manhuntstar.platform.DamageInfo;
import dev.manhuntstar.platform.GameItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;

/**
 * A bot body's death as core sees it (Bukkit's {@code EntityDeathEvent}): what killed it, and the drops and
 * experience core decides. The game's own drops never happen; the mod drops these instead.
 */
final class ModBotDeath implements BotDeath {

    private final DamageSource source;
    final List<ItemStack> drops = new ArrayList<>();
    int exp;

    ModBotDeath(DamageSource source) {
        this.source = source;
    }

    @Override
    public DamageInfo source() {
        return new ModDamageInfo(source);
    }

    @Override
    public void clearDrops() {
        drops.clear();
    }

    @Override
    public void addDrop(GameItem item) {
        drops.add(ModItem.stack(item).copy());
    }

    @Override
    public void setDroppedExp(int exp) {
        this.exp = exp;
    }
}
