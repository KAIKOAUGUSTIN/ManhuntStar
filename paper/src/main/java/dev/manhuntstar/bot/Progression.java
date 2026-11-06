package dev.manhuntstar.bot;

import dev.manhuntstar.config.Messages;
import dev.manhuntstar.platform.Dimension;
import dev.manhuntstar.platform.Enchant;
import dev.manhuntstar.platform.EquipSlot;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.GameWorld;
import dev.manhuntstar.platform.ItemType;
import dev.manhuntstar.platform.Items;
import dev.manhuntstar.platform.Platform;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The bot's progression phases (1 to 10, 12 = the Terminator's own final phase).
 * Each phase sets the base gear, what the bot can use and the drops when it dies.
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
        GameWorld w = bot.world();
        if (!netherUnlocked && w != null && w.dimension() == Dimension.NETHER) {
            netherUnlocked = true;
            if (phase < 5) {
                setPhase(5);
            }
        }
        applyPlayerTriggeredBoost();
        if (phase >= 10 && difficulty != BotDifficulty.TERMINATOR) {
            return;
        }
        // the phase clock only runs during the hunt: in the ring, in the countdown or with no huntable
        // player (dead, disconnected or in spectator), it doesn't
        if (bot.idle() != null || huntedPlayer() == null) {
            return;
        }
        // the Terminator's step from 10 to 12 also follows the phase duration multiplier
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

    /** After respawning in an ambush bed the bot goes back to phase 1 and recovers one phase every 5 s. */
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

    /** The player the bot hunts (or the closest one, if the target is gone), if they can still be hunted. */
    private GamePlayer huntedPlayer() {
        BotManager manager = bot.manager();
        GamePlayer p = bot.target();
        if (!manager.isEligibleTarget(p)) {
            p = bot.fallbackTarget();
        }
        return manager.isEligibleTarget(p) ? p : null;
    }

    private void applyPlayerTriggeredBoost() {
        GamePlayer p = huntedPlayer();
        if (p == null) {
            return;
        }
        int ironCount = countIron(p);
        int boostPhase = phase;
        String reasonKey = null;
        if (p.world().dimension() == Dimension.NETHER) {
            boostPhase = 5;
            reasonKey = "phase.reason.nether";
        } else if (countDiamonds(p) >= 5) {
            boostPhase = 5;
            reasonKey = "phase.reason.diamonds";
        } else if (ironCount > 20) {
            boostPhase = 5;
            reasonKey = "phase.reason.lots-of-iron";
        } else if (ironCount >= 1) {
            boostPhase = 4;
            reasonKey = "phase.reason.iron";
        } else if (holdingWoodenWeapon(p)) {
            boostPhase = 2;
            reasonKey = "phase.reason.wooden-weapon";
        }
        if (boostPhase > phase) {
            int old = phase;
            setPhaseSilently(boostPhase);
            Messages m = bot.manager().plugin().messages();
            Platform.server().broadcast(m.get("phase.jump",
                    Messages.p("bot", bot.visualName()), Messages.p("from", old), Messages.p("to", boostPhase),
                    Messages.c("reason", m.get(reasonKey)), Messages.p("player", p.name())));
        }
    }

    private static boolean holdingWoodenWeapon(GamePlayer p) {
        ItemType m = p.mainHandItem().type();
        return m == Items.WOODEN_SWORD || m == Items.WOODEN_AXE;
    }

    private static int countDiamonds(GamePlayer p) {
        int total = 0;
        for (GameItem s : p.inventoryContents()) {
            if (s == null || s.isEmpty()) {
                continue;
            }
            ItemType t = s.type();
            if (t == Items.DIAMOND_BLOCK) {
                total += s.amount() * 9;
            } else if (t == Items.DIAMOND || t == Items.DIAMOND_ORE || t == Items.DEEPSLATE_DIAMOND_ORE) {
                total += s.amount();
            }
        }
        return total;
    }

    private static int countIron(GamePlayer p) {
        int total = 0;
        for (GameItem s : p.inventoryContents()) {
            if (s == null || s.isEmpty()) {
                continue;
            }
            ItemType t = s.type();
            if (t == Items.IRON_BLOCK) {
                total += s.amount() * 9;
            } else if (t == Items.IRON_INGOT || t == Items.RAW_IRON || t == Items.IRON_ORE || t == Items.DEEPSLATE_IRON_ORE) {
                total += s.amount();
            }
        }
        return total;
    }

    /** Goes up a phase (never down), updates the gear and announces it on the server when it's a new phase. */
    public void setPhase(int newPhase) {
        if (newPhase <= phase) {
            return;
        }
        phase = newPhase;
        ticksInPhase = 0;
        updateGear();
        if (bot.manager().notePhaseReached(newPhase)) {
            Messages m = bot.manager().plugin().messages();
            Platform.server().broadcast(m.get("phase.upgrade", Messages.p("phase", newPhase)));
            Platform.server().broadcast(m.get("phase.description." + newPhase));
        }
    }

    /**
     * Phase chosen when creating the bot (menu or /manhunt create). Unlike {@link #setPhase}, it also goes down: a
     * Terminator (which starts at 8) created at phase 3 stays at 3. The gear comes afterwards, in {@link #updateGear}.
     */
    public void setInitialPhase(int startPhase) {
        if (startPhase > phase) {
            setPhase(startPhase);
            return;
        }
        phase = Math.max(1, startPhase);
        ticksInPhase = 0;
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

    // ------------------------------------------------------------------ gear per phase

    public void updateGear() {
        if (!bot.isAlive()) {
            return;
        }
        switch (phase) {
            case 2, 3 -> equipMain(GameItem.of(Items.WOODEN_SWORD));
            case 4 -> {
                equipArmor(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
                equipMain(GameItem.of(Items.STONE_SWORD));
                equipOffhandIfEmpty(GameItem.of(Items.SHIELD));
            }
            case 5 -> {
                equipArmor(Items.GOLDEN_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
                equipMain(GameItem.of(Items.IRON_SWORD));
                equipOffhandIfEmpty(GameItem.of(Items.SHIELD));
            }
            case 6 -> {
                equipArmor(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
                enchantArmor(Enchant.PROTECTION, 1);
                equipMain(GameItem.of(Items.IRON_SWORD));
                equipOffhandIfEmpty(GameItem.of(Items.SHIELD));
            }
            case 7 -> {
                equipArmor(Items.GOLDEN_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.GOLDEN_BOOTS);
                equipMain(GameItem.of(Items.DIAMOND_SWORD));
                equipOffhandIfEmpty(GameItem.of(Items.SHIELD));
            }
            case 8, 9, 10 -> {
                if (difficulty == BotDifficulty.TERMINATOR) {
                    equipArmor(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
                } else {
                    equipArmor(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
                }
                equipMain(GameItem.of(Items.DIAMOND_SWORD));
                equipOffhandIfEmpty(GameItem.of(Items.SHIELD));
                enchantArmor(Enchant.PROTECTION, 2);
                GameItem main = bot.getItem(EquipSlot.HAND);
                if (GearScoring.isSword(main) && main.enchantLevel(Enchant.SHARPNESS) < 2) {
                    main.addEnchant(Enchant.SHARPNESS, 2);
                    bot.setItem(EquipSlot.HAND, main);
                }
            }
            case FINAL_PHASE -> {
                equipArmor(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
                enchantArmor(Enchant.PROTECTION, 2);
                equipMain(mainHandItem());
                equipOffhandIfEmpty(GameItem.of(Items.SHIELD));
            }
            default -> {
            }
        }
    }

    /** The phase's base weapon (used when the bot has no better sword). */
    public GameItem mainHandItem() {
        return switch (phase) {
            case 2, 3 -> GameItem.of(Items.WOODEN_SWORD);
            case 4 -> GameItem.of(Items.STONE_SWORD);
            case 5, 6 -> GameItem.of(Items.IRON_SWORD);
            case 7, 8, 9, 10 -> {
                GameItem s = GameItem.of(Items.DIAMOND_SWORD);
                if (phase >= 8) {
                    s.addEnchant(Enchant.SHARPNESS, 2);
                }
                yield s;
            }
            case FINAL_PHASE -> {
                GameItem s = GameItem.of(Items.NETHERITE_SWORD);
                s.addEnchant(Enchant.SHARPNESS, 2);
                s.addEnchant(Enchant.KNOCKBACK, 2);
                yield s;
            }
            default -> GameItem.empty();
        };
    }

    /** The phase's base chestplate (to put back after using the elytra). */
    public GameItem chestplateForPhase() {
        return switch (phase) {
            case 4 -> GameItem.of(Items.LEATHER_CHESTPLATE);
            case 5, 6 -> GameItem.of(Items.IRON_CHESTPLATE);
            case 7 -> GameItem.of(Items.DIAMOND_CHESTPLATE);
            case 8, 9, 10 -> GameItem.of(difficulty == BotDifficulty.TERMINATOR ? Items.NETHERITE_CHESTPLATE : Items.DIAMOND_CHESTPLATE);
            case FINAL_PHASE -> GameItem.of(Items.NETHERITE_CHESTPLATE);
            default -> GameItem.empty();
        };
    }

    private void equipArmor(ItemType head, ItemType chest, ItemType legs, ItemType feet) {
        equipArmorBaseline(EquipSlot.HEAD, GameItem.of(head));
        equipArmorBaseline(EquipSlot.CHEST, GameItem.of(chest));
        equipArmorBaseline(EquipSlot.LEGS, GameItem.of(legs));
        equipArmorBaseline(EquipSlot.FEET, GameItem.of(feet));
    }

    private void equipArmorBaseline(EquipSlot slot, GameItem baseline) {
        GameItem current = bot.getItem(slot);
        if (current.isEmpty() || GearScoring.isBetterArmor(baseline, current, slot)) {
            bot.setItem(slot, baseline);
        }
    }

    private void equipMain(GameItem baseline) {
        GameItem current = bot.getItem(EquipSlot.HAND);
        if (current.isEmpty() || GearScoring.isBetterSword(baseline, current)) {
            bot.setItem(EquipSlot.HAND, baseline);
        }
    }

    private void equipOffhandIfEmpty(GameItem baseline) {
        if (difficulty == BotDifficulty.TERMINATOR && bot.terminatorTotemAvailable()) {
            bot.setItem(EquipSlot.OFF_HAND, GameItem.of(Items.TOTEM_OF_UNDYING));
            bot.markTotemIssued();
            return;
        }
        if (bot.getItem(EquipSlot.OFF_HAND).isEmpty()) {
            bot.setItem(EquipSlot.OFF_HAND, baseline);
        }
    }

    private void enchantArmor(Enchant ench, int level) {
        for (EquipSlot slot : new EquipSlot[]{EquipSlot.HEAD, EquipSlot.CHEST, EquipSlot.LEGS, EquipSlot.FEET}) {
            GameItem s = bot.getItem(slot);
            if (!s.isEmpty() && s.enchantLevel(ench) < level) {
                s.addEnchant(ench, level);
                bot.setItem(slot, s);
            }
        }
    }

    // ------------------------------------------------------------------ drops

    /** The phase's "synthetic" drops (food, apples, worn weapons), rolled with the difficulty's chance. */
    List<GameItem> deathDrops(boolean inventoryAlreadyDropped) {
        List<GameItem> out = new ArrayList<>();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextFloat() > difficulty.dropChance()) {
            return out;
        }
        boolean gear = !inventoryAlreadyDropped;
        switch (phase) {
            case 2 -> {
                if (gear) {
                    out.add(GameItem.of(Items.WOODEN_SWORD));
                }
                out.add(GameItem.of(Items.COOKED_BEEF, 2));
            }
            case 3 -> {
                if (gear) {
                    out.add(GameItem.of(Items.WOODEN_SWORD));
                }
                out.add(GameItem.of(Items.COOKED_BEEF, 4));
            }
            case 4 -> {
                if (gear) {
                    out.add(GameItem.of(Items.STONE_SWORD));
                    out.add(worn(Items.SHIELD, 10));
                }
                out.add(GameItem.of(Items.COOKED_BEEF, 6));
            }
            case 5 -> {
                if (r.nextFloat() < 0.1f) {
                    addArmorPiece(out);
                }
                if (gear) {
                    out.add(GameItem.of(Items.IRON_SWORD));
                    out.add(GameItem.of(Items.SHIELD));
                }
                out.add(GameItem.of(Items.COOKED_BEEF, 6 + r.nextInt(5)));
                out.add(GameItem.of(Items.GOLDEN_APPLE, 1));
            }
            case 6 -> {
                if (r.nextFloat() < 0.05f) {
                    addArmorPiece(out);
                }
                if (gear) {
                    out.add(GameItem.of(Items.IRON_SWORD));
                    out.add(GameItem.of(Items.SHIELD));
                }
                out.add(GameItem.of(Items.COOKED_BEEF, 10 + r.nextInt(5)));
                out.add(GameItem.of(Items.GOLDEN_APPLE, 1 + r.nextInt(2)));
            }
            case 7, 8, 9, 10 -> {
                if (r.nextFloat() < (phase == 7 ? 0.05f : 0.025f)) {
                    addArmorPiece(out);
                }
                if (gear) {
                    out.add(worn(Items.DIAMOND_SWORD, 20));
                    out.add(GameItem.of(Items.SHIELD));
                }
                out.add(GameItem.of(Items.COOKED_BEEF, 12 + r.nextInt(5)));
                out.add(GameItem.of(Items.GOLDEN_APPLE, 2 + r.nextInt(3)));
            }
            default -> {
            }
        }
        return out;
    }

    private static GameItem worn(ItemType m, int durabilityLeft) {
        GameItem s = GameItem.of(m);
        s.setDamage(Math.max(0, m.maxDurability() - durabilityLeft));
        return s;
    }

    private void addArmorPiece(List<GameItem> out) {
        EquipSlot[] slots = {EquipSlot.HEAD, EquipSlot.CHEST, EquipSlot.LEGS, EquipSlot.FEET};
        GameItem s = bot.getItem(slots[ThreadLocalRandom.current().nextInt(slots.length)]);
        if (!s.isEmpty()) {
            out.add(s.copy());
        }
    }
}
