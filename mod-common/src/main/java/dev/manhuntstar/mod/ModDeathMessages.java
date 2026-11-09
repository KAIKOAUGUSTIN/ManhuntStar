package dev.manhuntstar.mod;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotManager;
import dev.manhuntstar.host.DeathMessageSource;
import dev.manhuntstar.platform.DamageInfo;
import net.kyori.adventure.text.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Bot death messages in the game's format ("X was slain by Y", "X fell from a high place"...), with the
 * client's own translation keys, so they show up in each player's language.
 */
public final class ModDeathMessages implements DeathMessageSource {

    private final BotManager manager;

    public ModDeathMessages(BotManager manager) {
        this.manager = manager;
    }

    @Override
    public Component build(Bot bot, DamageInfo info) {
        DamageSource src = ModDamageInfo.vanilla(info);
        Component victim = Component.text(bot.visualName());
        String base = "death.attack." + src.type().msgId();
        Entity causing = src.getEntity();
        Entity direct = src.getDirectEntity();
        if (src.typeHolder().is(DamageTypes.FALL)) {
            return causing != null
                    ? Component.translatable("death.fell.assist", victim, nameOf(causing))
                    : Component.translatable("death.fell.accident.generic", victim);
        }
        if (causing == null && direct == null) {
            return Component.translatable(base, victim);
        }
        Entity killer = causing != null ? causing : direct;
        ItemStack weapon = killer instanceof LivingEntity le ? le.getMainHandItem() : null;
        if (weapon != null && !weapon.isEmpty() && weapon.has(DataComponents.CUSTOM_NAME)) {
            Component name = ModServer.get().audiences().asAdventure(weapon.getDisplayName());
            return Component.translatable(base + ".item", victim, nameOf(killer), name);
        }
        return Component.translatable(base, victim, nameOf(killer));
    }

    private Component nameOf(Entity e) {
        Bot b = manager.byEntity(ModEntities.of(e));
        if (b != null) {
            return Component.text(b.visualName());
        }
        if (e instanceof ServerPlayer p) {
            return ModEntities.of(p).displayName();
        }
        net.minecraft.network.chat.Component custom = e.getCustomName();
        return custom != null ? ModServer.get().audiences().asAdventure(custom) : Component.translatable(e.getType().getDescriptionId());
    }
}
