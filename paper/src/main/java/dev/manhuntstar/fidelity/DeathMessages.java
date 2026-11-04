package dev.manhuntstar.fidelity;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.bot.BotManager;
import net.kyori.adventure.text.Component;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Mensagens de morte do bot no padrão do jogo ("X foi morto por Y", "X caiu de um lugar alto"...),
 * usando as chaves de tradução do próprio cliente, então aparecem no idioma de cada jogador.
 */
public final class DeathMessages {

    private final BotManager manager;

    public DeathMessages(BotManager manager) {
        this.manager = manager;
    }

    public Component build(Bot bot, DamageSource src) {
        Component victim = Component.text(bot.visualName());
        DamageType type = src.getDamageType();
        String base = "death.attack." + type.getTranslationKey();
        Entity causing = src.getCausingEntity();
        Entity direct = src.getDirectEntity();
        if (type.equals(DamageType.FALL)) {
            return causing != null
                    ? Component.translatable("death.fell.assist", victim, nameOf(causing))
                    : Component.translatable("death.fell.accident.generic", victim);
        }
        if (causing == null && direct == null) {
            return Component.translatable(base, victim);
        }
        Entity killer = causing != null ? causing : direct;
        ItemStack weapon = killer instanceof LivingEntity le && le.getEquipment() != null ? le.getEquipment().getItemInMainHand() : null;
        if (weapon != null && !weapon.isEmpty() && weapon.hasItemMeta() && weapon.getItemMeta().hasCustomName()) {
            return Component.translatable(base + ".item", victim, nameOf(killer), weapon.displayName());
        }
        return Component.translatable(base, victim, nameOf(killer));
    }

    private Component nameOf(Entity e) {
        Bot b = manager.byEntity(e);
        if (b != null) {
            return Component.text(b.visualName());
        }
        if (e instanceof Player p) {
            return p.displayName();
        }
        Component custom = e.customName();
        return custom != null ? custom : Component.translatable(e.getType().translationKey());
    }
}
