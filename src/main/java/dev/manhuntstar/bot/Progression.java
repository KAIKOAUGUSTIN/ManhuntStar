package dev.manhuntstar.bot;

import dev.manhuntstar.config.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;

/**
 * Fases de progressão do bot (1 a 10, 12 = fase final exclusiva do Terminator).
 * Cada fase define o equipamento base, o que o bot pode usar e os drops ao morrer.
 */
public final class Progression {

    public static final int FINAL_PHASE = 12;
    private static final int RESPAWN_REGEN_INTERVAL = 100;

    private final Bot bot;
    private final BotDifficulty difficulty;
    private int phase;
    private int ticksInPhase;
    private boolean netherUnlocked;
    private boolean progressionEnabled = true;
    private int regenTargetPhase = -1;
    private int regenTicks;

    Progression(Bot bot, BotDifficulty difficulty) {
        this.bot = bot;
        this.difficulty = difficulty;
        this.phase = difficulty.startPhase();
    }

    public BotDifficulty difficulty() {
        return difficulty;
    }

    public int phase() {
        return phase;
    }

    public boolean progressionEnabled() {
        return progressionEnabled;
    }

    public void setProgressionEnabled(boolean enabled) {
        this.progressionEnabled = enabled;
        if (!enabled) {
            regenTargetPhase = -1;
            regenTicks = 0;
        }
    }

    public void tick() {
        if (!progressionEnabled) {
            return;
        }
        if (regenTargetPhase > 0) {
            tickRegeneration();
            return;
        }
        World w = bot.world();
        if (!netherUnlocked && w != null && w.getEnvironment() == World.Environment.NETHER) {
            netherUnlocked = true;
            if (phase < 5) {
                setPhase(5);
            }
        }
        applyPlayerTriggeredBoost();
        if (phase >= 10 && difficulty != BotDifficulty.TERMINATOR) {
            return;
        }
        // a passagem 10 → 12 do Terminator também respeita o multiplicador de duração das fases
        int limit = (int) (difficulty.phaseDurationTicks() * bot.settings().phaseMultiplier.value());
        if (difficulty == BotDifficulty.TERMINATOR && phase == 10) {
            if (++ticksInPhase >= limit) {
                setPhase(FINAL_PHASE);
            }
            return;
        }
        if (phase >= 11) {
            return;
        }
        ticksInPhase++;
        if (ticksInPhase >= limit) {
            setPhase(phase + 1);
        }
    }

    /** Depois de renascer em uma cama de emboscada o bot volta à fase 1 e recupera uma fase a cada 5 s. */
    public void startRespawnRegeneration(int targetPhase) {
        int clamped = Math.max(1, targetPhase);
        regenTargetPhase = clamped;
        regenTicks = 0;
        setPhaseSilently(1);
        if (clamped <= 1) {
            regenTargetPhase = -1;
        }
    }

    private void tickRegeneration() {
        if (phase >= regenTargetPhase) {
            regenTargetPhase = -1;
            regenTicks = 0;
            return;
        }
        if (++regenTicks >= RESPAWN_REGEN_INTERVAL) {
            regenTicks = 0;
            setPhaseSilently(Math.min(phase + 1, regenTargetPhase));
            if (phase >= regenTargetPhase) {
                regenTargetPhase = -1;
            }
        }
    }

    private void applyPlayerTriggeredBoost() {
        Player p = bot.target();
        if (p == null || !p.isValid() || p.isDead()) {
            p = bot.fallbackTarget();
        }
        if (p == null || p.isDead()) {
            return;
        }
        int ironCount = countIron(p);
        int boostPhase = phase;
        String reasonKey = null;
        if (p.getWorld().getEnvironment() == World.Environment.NETHER) {
            boostPhase = 5;
            reasonKey = "fase.motivo.nether";
        } else if (countDiamonds(p) >= 5) {
            boostPhase = 5;
            reasonKey = "fase.motivo.diamantes";
        } else if (ironCount > 20) {
            boostPhase = 5;
            reasonKey = "fase.motivo.muito-ferro";
        } else if (ironCount >= 1) {
            boostPhase = 4;
            reasonKey = "fase.motivo.ferro";
        } else if (holdingWoodenWeapon(p)) {
            boostPhase = 2;
            reasonKey = "fase.motivo.arma-madeira";
        }
        if (boostPhase > phase) {
            int old = phase;
            setPhaseSilently(boostPhase);
            Messages m = bot.manager().plugin().messages();
            bot.manager().plugin().getServer().broadcast(m.get("fase.salto",
                    Messages.p("bot", bot.visualName()), Messages.p("de", old), Messages.p("para", boostPhase),
                    Messages.c("motivo", m.get(reasonKey)), Messages.p("jogador", p.getName())));
        }
    }

    private static boolean holdingWoodenWeapon(Player p) {
        Material m = p.getInventory().getItemInMainHand().getType();
        return m == Material.WOODEN_SWORD || m == Material.WOODEN_AXE;
    }

    private static int countDiamonds(Player p) {
        int total = 0;
        PlayerInventory inv = p.getInventory();
        for (ItemStack s : inv.getContents()) {
            if (s == null || s.isEmpty()) {
                continue;
            }
            switch (s.getType()) {
                case DIAMOND_BLOCK -> total += s.getAmount() * 9;
                case DIAMOND, DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> total += s.getAmount();
                default -> {
                }
            }
        }
        return total;
    }

    private static int countIron(Player p) {
        int total = 0;
        for (ItemStack s : p.getInventory().getContents()) {
            if (s == null || s.isEmpty()) {
                continue;
            }
            switch (s.getType()) {
                case IRON_BLOCK -> total += s.getAmount() * 9;
                case IRON_INGOT, RAW_IRON, IRON_ORE, DEEPSLATE_IRON_ORE -> total += s.getAmount();
                default -> {
                }
            }
        }
        return total;
    }

    /** Sobe de fase (nunca desce), atualiza o equipamento e anuncia quando é uma fase nova no servidor. */
    public void setPhase(int newPhase) {
        if (newPhase <= phase) {
            return;
        }
        phase = newPhase;
        ticksInPhase = 0;
        updateGear();
        if (bot.manager().notePhaseReached(newPhase)) {
            Messages m = bot.manager().plugin().messages();
            bot.manager().plugin().getServer().broadcast(m.get("fase.upgrade", Messages.p("fase", newPhase)));
            bot.manager().plugin().getServer().broadcast(m.get("fase.descricao." + newPhase));
        }
    }

    public void setPhaseSilently(int newPhase) {
        phase = Math.max(1, newPhase);
        ticksInPhase = 0;
        updateGear();
        bot.manager().notePhaseReached(phase);
    }

    public boolean canUseCrossbow() {
        return phase >= 3 && bot.settings().allowCrossbow.on();
    }

    public boolean canUseShield() {
        return phase >= 4;
    }

    public boolean canUseTrident() {
        return phase >= 4 && bot.settings().allowTrident.on();
    }

    public boolean canUseLavaBucket() {
        return phase >= 5;
    }

    public float pearlCooldownMultiplier() {
        if (phase >= 10) {
            return 0.25f;
        }
        return phase == 9 ? 0.5f : 1.0f;
    }

    // ------------------------------------------------------------------ equipamento por fase

    public void updateGear() {
        if (!bot.isAlive()) {
            return;
        }
        switch (phase) {
            case 2, 3 -> equipMain(new ItemStack(Material.WOODEN_SWORD));
            case 4 -> {
                equipArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);
                equipMain(new ItemStack(Material.STONE_SWORD));
                equipOffhandIfEmpty(new ItemStack(Material.SHIELD));
            }
            case 5 -> {
                equipArmor(Material.GOLDEN_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS);
                equipMain(new ItemStack(Material.IRON_SWORD));
                equipOffhandIfEmpty(new ItemStack(Material.SHIELD));
            }
            case 6 -> {
                equipArmor(Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS);
                enchantArmor(Enchantment.PROTECTION, 1);
                equipMain(new ItemStack(Material.IRON_SWORD));
                equipOffhandIfEmpty(new ItemStack(Material.SHIELD));
            }
            case 7 -> {
                equipArmor(Material.GOLDEN_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.GOLDEN_BOOTS);
                equipMain(new ItemStack(Material.DIAMOND_SWORD));
                equipOffhandIfEmpty(new ItemStack(Material.SHIELD));
            }
            case 8, 9, 10 -> {
                if (difficulty == BotDifficulty.TERMINATOR) {
                    equipArmor(Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS);
                } else {
                    equipArmor(Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS);
                }
                equipMain(new ItemStack(Material.DIAMOND_SWORD));
                equipOffhandIfEmpty(new ItemStack(Material.SHIELD));
                enchantArmor(Enchantment.PROTECTION, 2);
                ItemStack main = bot.getItem(EquipmentSlot.HAND);
                if (GearScoring.isSword(main) && main.getEnchantmentLevel(Enchantment.SHARPNESS) < 2) {
                    main.addUnsafeEnchantment(Enchantment.SHARPNESS, 2);
                    bot.setItem(EquipmentSlot.HAND, main);
                }
            }
            case FINAL_PHASE -> {
                equipArmor(Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS);
                enchantArmor(Enchantment.PROTECTION, 2);
                equipMain(mainHandItem());
                equipOffhandIfEmpty(new ItemStack(Material.SHIELD));
            }
            default -> {
            }
        }
    }

    /** Arma base da fase (usada quando o bot não tem espada melhor). */
    public ItemStack mainHandItem() {
        return switch (phase) {
            case 2, 3 -> new ItemStack(Material.WOODEN_SWORD);
            case 4 -> new ItemStack(Material.STONE_SWORD);
            case 5, 6 -> new ItemStack(Material.IRON_SWORD);
            case 7, 8, 9, 10 -> {
                ItemStack s = new ItemStack(Material.DIAMOND_SWORD);
                if (phase >= 8) {
                    s.addUnsafeEnchantment(Enchantment.SHARPNESS, 2);
                }
                yield s;
            }
            case FINAL_PHASE -> {
                ItemStack s = new ItemStack(Material.NETHERITE_SWORD);
                s.addUnsafeEnchantment(Enchantment.SHARPNESS, 2);
                s.addUnsafeEnchantment(Enchantment.KNOCKBACK, 2);
                yield s;
            }
            default -> ItemStack.empty();
        };
    }

    /** Peitoral base da fase (para devolver depois de usar a elytra). */
    public ItemStack chestplateForPhase() {
        return switch (phase) {
            case 4 -> new ItemStack(Material.LEATHER_CHESTPLATE);
            case 5, 6 -> new ItemStack(Material.IRON_CHESTPLATE);
            case 7 -> new ItemStack(Material.DIAMOND_CHESTPLATE);
            case 8, 9, 10 -> new ItemStack(difficulty == BotDifficulty.TERMINATOR ? Material.NETHERITE_CHESTPLATE : Material.DIAMOND_CHESTPLATE);
            case FINAL_PHASE -> new ItemStack(Material.NETHERITE_CHESTPLATE);
            default -> ItemStack.empty();
        };
    }

    private void equipArmor(Material head, Material chest, Material legs, Material feet) {
        equipArmorBaseline(EquipmentSlot.HEAD, new ItemStack(head));
        equipArmorBaseline(EquipmentSlot.CHEST, new ItemStack(chest));
        equipArmorBaseline(EquipmentSlot.LEGS, new ItemStack(legs));
        equipArmorBaseline(EquipmentSlot.FEET, new ItemStack(feet));
    }

    private void equipArmorBaseline(EquipmentSlot slot, ItemStack baseline) {
        ItemStack current = bot.getItem(slot);
        if (current.isEmpty() || GearScoring.isBetterArmor(baseline, current, slot)) {
            bot.setItem(slot, baseline);
        }
    }

    private void equipMain(ItemStack baseline) {
        ItemStack current = bot.getItem(EquipmentSlot.HAND);
        if (current.isEmpty() || GearScoring.isBetterSword(baseline, current)) {
            bot.setItem(EquipmentSlot.HAND, baseline);
        }
    }

    private void equipOffhandIfEmpty(ItemStack baseline) {
        if (difficulty == BotDifficulty.TERMINATOR && bot.terminatorTotemAvailable()) {
            bot.setItem(EquipmentSlot.OFF_HAND, new ItemStack(Material.TOTEM_OF_UNDYING));
            bot.markTotemIssued();
            return;
        }
        if (bot.getItem(EquipmentSlot.OFF_HAND).isEmpty()) {
            bot.setItem(EquipmentSlot.OFF_HAND, baseline);
        }
    }

    private void enchantArmor(Enchantment ench, int level) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = bot.getItem(slot);
            if (!s.isEmpty() && s.getEnchantmentLevel(ench) < level) {
                s.addUnsafeEnchantment(ench, level);
                bot.setItem(slot, s);
            }
        }
    }

    // ------------------------------------------------------------------ drops

    /** Drops "sintéticos" da fase (comida, maçãs, armas gastas), sorteados pela chance da dificuldade. */
    List<ItemStack> deathDrops(boolean inventoryAlreadyDropped) {
        List<ItemStack> out = new ArrayList<>();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextFloat() > difficulty.dropChance()) {
            return out;
        }
        boolean gear = !inventoryAlreadyDropped;
        switch (phase) {
            case 2 -> {
                if (gear) {
                    out.add(new ItemStack(Material.WOODEN_SWORD));
                }
                out.add(new ItemStack(Material.COOKED_BEEF, 2));
            }
            case 3 -> {
                if (gear) {
                    out.add(new ItemStack(Material.WOODEN_SWORD));
                }
                out.add(new ItemStack(Material.COOKED_BEEF, 4));
            }
            case 4 -> {
                if (gear) {
                    out.add(new ItemStack(Material.STONE_SWORD));
                    out.add(worn(Material.SHIELD, 10));
                }
                out.add(new ItemStack(Material.COOKED_BEEF, 6));
            }
            case 5 -> {
                if (r.nextFloat() < 0.1f) {
                    addArmorPiece(out);
                }
                if (gear) {
                    out.add(new ItemStack(Material.IRON_SWORD));
                    out.add(new ItemStack(Material.SHIELD));
                }
                out.add(new ItemStack(Material.COOKED_BEEF, 6 + r.nextInt(5)));
                out.add(new ItemStack(Material.GOLDEN_APPLE, 1));
            }
            case 6 -> {
                if (r.nextFloat() < 0.05f) {
                    addArmorPiece(out);
                }
                if (gear) {
                    out.add(new ItemStack(Material.IRON_SWORD));
                    out.add(new ItemStack(Material.SHIELD));
                }
                out.add(new ItemStack(Material.COOKED_BEEF, 10 + r.nextInt(5)));
                out.add(new ItemStack(Material.GOLDEN_APPLE, 1 + r.nextInt(2)));
            }
            case 7, 8, 9, 10 -> {
                if (r.nextFloat() < (phase == 7 ? 0.05f : 0.025f)) {
                    addArmorPiece(out);
                }
                if (gear) {
                    out.add(worn(Material.DIAMOND_SWORD, 20));
                    out.add(new ItemStack(Material.SHIELD));
                }
                out.add(new ItemStack(Material.COOKED_BEEF, 12 + r.nextInt(5)));
                out.add(new ItemStack(Material.GOLDEN_APPLE, 2 + r.nextInt(3)));
            }
            default -> {
            }
        }
        return out;
    }

    private static ItemStack worn(Material m, int durabilityLeft) {
        ItemStack s = new ItemStack(m);
        if (s.getItemMeta() instanceof Damageable d) {
            d.setDamage(Math.max(0, m.getMaxDurability() - durabilityLeft));
            s.setItemMeta(d);
        }
        return s;
    }

    private void addArmorPiece(List<ItemStack> out) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        ItemStack s = bot.getItem(slots[ThreadLocalRandom.current().nextInt(slots.length)]);
        if (!s.isEmpty()) {
            out.add(s.clone());
        }
    }
}
