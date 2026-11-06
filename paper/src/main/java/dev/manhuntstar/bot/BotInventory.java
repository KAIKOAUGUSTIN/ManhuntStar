package dev.manhuntstar.bot;

import dev.manhuntstar.platform.BotBody;
import dev.manhuntstar.platform.EntityTrait;
import dev.manhuntstar.platform.EquipSlot;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.GameItem;
import dev.manhuntstar.platform.GameItemEntity;
import dev.manhuntstar.platform.Items;
import dev.manhuntstar.platform.Sounds;
import dev.manhuntstar.util.Vec3;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.SoundCategory;

/**
 * The bot's virtual inventory (36 slots, like a player's) and handling of the best equipment:
 * picking up items/XP, automatic switch to the best sword/armor and keeping equipment between deaths.
 */
public final class BotInventory {

    public static final int SIZE = 36;
    private static final double PICKUP_RADIUS = 1.8;
    private static final EquipSlot[] ARMOR = {EquipSlot.HEAD, EquipSlot.CHEST, EquipSlot.LEGS, EquipSlot.FEET};
    private static final EquipSlot[] EQUIPMENT = {EquipSlot.HAND, EquipSlot.OFF_HAND, EquipSlot.HEAD, EquipSlot.CHEST, EquipSlot.LEGS, EquipSlot.FEET};

    private final Bot bot;
    private final GameItem[] main = new GameItem[SIZE];
    private GameItem persistedSword = GameItem.empty();
    private GameItem persistedPickaxe = GameItem.empty();
    private final EnumMap<EquipSlot, GameItem> persistedArmor = new EnumMap<>(EquipSlot.class);

    BotInventory(Bot bot) {
        this.bot = bot;
        for (int i = 0; i < SIZE; i++) {
            main[i] = GameItem.empty();
        }
        for (EquipSlot s : ARMOR) {
            persistedArmor.put(s, GameItem.empty());
        }
    }

    public GameItem get(int slot) {
        return main[slot];
    }

    public void set(int slot, GameItem stack) {
        main[slot] = stack == null ? GameItem.empty() : stack;
    }

    public void clear() {
        for (int i = 0; i < SIZE; i++) {
            main[i] = GameItem.empty();
        }
    }

    /** Adds to the inventory; returns what's left over. */
    public GameItem add(GameItem stack) {
        if (GearScoring.isEmpty(stack)) {
            return GameItem.empty();
        }
        GameItem remaining = stack.copy();
        for (int i = 0; i < SIZE && remaining.amount() > 0; i++) {
            GameItem cur = main[i];
            if (!cur.isEmpty() && cur.isSimilar(remaining)) {
                int space = cur.maxStackSize() - cur.amount();
                if (space > 0) {
                    int move = Math.min(space, remaining.amount());
                    cur.setAmount(cur.amount() + move);
                    remaining.setAmount(remaining.amount() - move);
                }
            }
        }
        for (int i = 0; i < SIZE && remaining.amount() > 0; i++) {
            if (main[i].isEmpty()) {
                main[i] = remaining.copy();
                remaining.setAmount(0);
            }
        }
        return remaining.amount() > 0 ? remaining : GameItem.empty();
    }

    public void tick() {
        pickupNearbyLootAndXp();
        if (bot.capacityLocked()) {
            return;
        }
        if (bot.tickDelay(20)) {
            reevaluateAndEquipBestGear();
        }
    }

    // ------------------------------------------------------------------ preferred equipment

    public GameItem preferredMainHand() {
        GameItem current = bot.getItem(EquipSlot.HAND);
        if (GearScoring.isSword(current)) {
            return current.copy();
        }
        if (GearScoring.isSword(persistedSword)) {
            return persistedSword.copy();
        }
        GameItem best = findBestSword();
        if (!best.isEmpty()) {
            return best.copy();
        }
        return bot.progression().mainHandItem();
    }

    public GameItem preferredPickaxe() {
        GameItem current = bot.getItem(EquipSlot.HAND);
        if (GearScoring.isPickaxe(current)) {
            return current.copy();
        }
        if (GearScoring.isPickaxe(persistedPickaxe)) {
            return persistedPickaxe.copy();
        }
        GameItem best = findBestPickaxe();
        if (!best.isEmpty()) {
            return best.copy();
        }
        return GameItem.of(Items.DIAMOND_PICKAXE);
    }

    public void restorePreferredMainHand() {
        if (bot.capacityLocked()) {
            return;
        }
        GameItem preferred = preferredMainHand();
        if (!preferred.isEmpty() && !preferred.isSimilar(bot.getItem(EquipSlot.HAND))) {
            bot.setItem(EquipSlot.HAND, preferred);
        }
    }

    public void restorePreferredPickaxe() {
        GameItem preferred = preferredPickaxe();
        if (!preferred.isEmpty() && !preferred.isSimilar(bot.getItem(EquipSlot.HAND))) {
            bot.setItem(EquipSlot.HAND, preferred);
        }
    }

    /** Called whenever an equipment slot changes: updates the persistent equipment if it's better. */
    void onEquipmentChanged(EquipSlot slot, GameItem stack) {
        if (slot == EquipSlot.HAND) {
            if (GearScoring.isSword(stack) && GearScoring.attackScore(stack) >= GearScoring.attackScore(persistedSword) - 0.01f) {
                persistedSword = stack.copy();
            }
            if (GearScoring.isPickaxe(stack) && GearScoring.pickaxeScore(stack) >= GearScoring.pickaxeScore(persistedPickaxe) - 0.01f) {
                persistedPickaxe = stack.copy();
            }
            return;
        }
        if (isArmorSlot(slot) && GearScoring.isArmorForSlot(stack, slot)) {
            GameItem cur = persistedArmor.get(slot);
            if (cur.isEmpty() || GearScoring.armorScore(stack, slot) >= GearScoring.armorScore(cur, slot) - 0.01f) {
                persistedArmor.put(slot, stack.copy());
            }
        }
    }

    private static boolean isArmorSlot(EquipSlot s) {
        return s == EquipSlot.HEAD || s == EquipSlot.CHEST || s == EquipSlot.LEGS || s == EquipSlot.FEET;
    }

    public PersistentGear snapshotPersistentGear() {
        return new PersistentGear(persistedSword.copy(), persistedPickaxe.copy(), persistedArmor.get(EquipSlot.HEAD).copy(),
                persistedArmor.get(EquipSlot.CHEST).copy(), persistedArmor.get(EquipSlot.LEGS).copy(), persistedArmor.get(EquipSlot.FEET).copy());
    }

    /** Restores the persistent equipment and equips whatever is better than the current one. */
    public void applyPersistentGear(PersistentGear gear) {
        if (gear == null) {
            return;
        }
        if (!GearScoring.isEmpty(gear.sword())) {
            persistedSword = gear.sword().copy();
        }
        if (!GearScoring.isEmpty(gear.pickaxe())) {
            persistedPickaxe = gear.pickaxe().copy();
        }
        for (EquipSlot s : ARMOR) {
            GameItem a = gear.armor(s);
            if (!GearScoring.isEmpty(a)) {
                persistedArmor.put(s, a.copy());
            }
        }
        if (!bot.isAlive()) {
            return;
        }
        if (!persistedSword.isEmpty() && GearScoring.isBetterSword(persistedSword, bot.getItem(EquipSlot.HAND))) {
            bot.setItem(EquipSlot.HAND, persistedSword.copy());
        }
        for (EquipSlot s : ARMOR) {
            applyPersistedArmorIfBetter(s);
        }
    }

    public void clearPersistentGear() {
        persistedSword = GameItem.empty();
        persistedPickaxe = GameItem.empty();
        for (EquipSlot s : ARMOR) {
            persistedArmor.put(s, GameItem.empty());
        }
    }

    private void applyPersistedArmorIfBetter(EquipSlot slot) {
        GameItem persisted = persistedArmor.get(slot);
        if (persisted == null || persisted.isEmpty()) {
            return;
        }
        GameItem current = bot.getItem(slot);
        if (current.isEmpty() || GearScoring.isBetterArmor(persisted, current, slot)) {
            bot.setItem(slot, persisted.copy());
        }
    }

    private void reevaluateAndEquipBestGear() {
        if (bot.combat().isChargingCrossbow() || bot.combat().isChargingTrident()) {
            return;
        }
        if (bot.mining().isMining() || bot.survival().isEating()) {
            return;
        }
        GameItem bestSword = findBestSword();
        if (!bestSword.isEmpty() && GearScoring.isBetterSword(bestSword, persistedSword)) {
            persistedSword = bestSword.copy();
        }
        GameItem bestPick = findBestPickaxe();
        if (!bestPick.isEmpty() && GearScoring.isBetterPickaxe(bestPick, persistedPickaxe)) {
            persistedPickaxe = bestPick.copy();
        }
        GameItem currentMain = bot.getItem(EquipSlot.HAND);
        // on horseback the bot holds the charge spear (HorseRoutine); the sword comes back when it dismounts
        boolean ridingWithSpear = bot.isRiding() && GearScoring.isSpear(currentMain);
        if (!bestSword.isEmpty() && !ridingWithSpear
                && (currentMain.isEmpty() || !GearScoring.isSword(currentMain) || GearScoring.isBetterSword(bestSword, currentMain))) {
            bot.setItem(EquipSlot.HAND, bestSword.copy());
        }
        for (EquipSlot s : ARMOR) {
            equipBestArmor(s);
        }
    }

    private void equipBestArmor(EquipSlot slot) {
        GameItem best = findBestArmor(slot);
        if (best.isEmpty()) {
            return;
        }
        GameItem current = bot.getItem(slot);
        if (current.isEmpty() || GearScoring.isBetterArmor(best, current, slot)) {
            bot.setItem(slot, best.copy());
        }
        GameItem persisted = persistedArmor.get(slot);
        if (persisted.isEmpty() || GearScoring.isBetterArmor(best, persisted, slot)) {
            persistedArmor.put(slot, best.copy());
        }
    }

    private GameItem findBestSword() {
        GameItem best = GameItem.empty();
        float bestScore = -1f;
        for (GameItem s : main) {
            float score = GearScoring.attackScore(s);
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        if (GearScoring.attackScore(persistedSword) > bestScore) {
            best = persistedSword;
        }
        return best.isEmpty() ? GameItem.empty() : best.copy();
    }

    private GameItem findBestPickaxe() {
        GameItem best = GameItem.empty();
        float bestScore = -1f;
        for (GameItem s : main) {
            float score = GearScoring.pickaxeScore(s);
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        if (GearScoring.pickaxeScore(persistedPickaxe) > bestScore) {
            best = persistedPickaxe;
        }
        return best.isEmpty() ? GameItem.empty() : best.copy();
    }

    private GameItem findBestArmor(EquipSlot slot) {
        GameItem best = GameItem.empty();
        float bestScore = -1f;
        for (GameItem s : main) {
            float score = GearScoring.armorScore(s, slot);
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        GameItem persisted = persistedArmor.get(slot);
        if (GearScoring.armorScore(persisted, slot) > bestScore) {
            best = persisted;
        }
        return best.isEmpty() ? GameItem.empty() : best.copy();
    }

    // ------------------------------------------------------------------ pickup

    private void pickupNearbyLootAndXp() {
        BotBody e = bot.entity();
        if (e == null) {
            return;
        }
        for (GameEntity near : e.nearbyEntities(PICKUP_RADIUS, PICKUP_RADIUS, PICKUP_RADIUS)) {
            if (near instanceof GameItemEntity item) {
                if (!item.isValid() || item.pickupDelay() > 0 || !item.canMobPickup()) {
                    continue;
                }
                GameItem stack = item.item();
                int before = stack.amount();
                GameItem left = add(stack);
                int taken = before - left.amount();
                if (taken <= 0) {
                    continue;
                }
                e.playPickupAnimation(item, taken);
                if (left.isEmpty()) {
                    item.remove();
                } else {
                    item.setItem(left);
                }
                e.world().playSound(e.position(), Sounds.ENTITY_ITEM_PICKUP, dev.manhuntstar.platform.SoundCategory.PLAYERS, 0.2f,
                        (ThreadLocalRandom.current().nextFloat() - ThreadLocalRandom.current().nextFloat()) * 1.4f + 2.0f);
            } else if (near.is(EntityTrait.EXPERIENCE_ORB) && near.isValid()) {
                near.remove();
                e.world().playSound(e.position(), Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, dev.manhuntstar.platform.SoundCategory.PLAYERS, 0.1f,
                        0.5f + ThreadLocalRandom.current().nextFloat() * 0.6f);
            }
        }
    }

    // ------------------------------------------------------------------ death

    /** Items to drop when the bot dies, according to the drop and persistence options. */
    public List<GameItem> collectDeathDrops(boolean dropInventory, Modes.GearPersistence mode) {
        List<GameItem> drops = new ArrayList<>();
        reevaluateAndEquipBestGearSafe();
        if (dropInventory) {
            boolean keep = mode == Modes.GearPersistence.KEEP_ON_RESPAWN_NO_DROP;
            int keepSword = keep ? findBestSwordSlot() : -1;
            int keepPick = keep ? findBestPickaxeSlot() : -1;
            // What the bot wears or holds and came from the inventory is a copy of the item there: it's one item.
            // It used to drop twice (or drop and stay with the bot in the mode that keeps it).
            Map<EquipSlot, Integer> source = equippedSourceSlots();
            Set<Integer> keptSources = new HashSet<>();
            for (EquipSlot slot : EQUIPMENT) {
                GameItem s = bot.getItem(slot);
                if (s.isEmpty()) {
                    continue;
                }
                Integer from = source.get(slot);
                if (keep && (isArmorSlot(slot) || (slot == EquipSlot.HAND && (GearScoring.isSword(s) || GearScoring.isPickaxe(s))) || keepNamedCombatItem(s))) {
                    if (from != null) {
                        keptSources.add(from);
                    }
                    continue;
                }
                // the worn elytra is lent for flying (the {@code mobility.elytra} option) and the spear is outside the inventory,
                // for the horseback charge: they don't belong to the bot
                if (from == null && !(slot == EquipSlot.CHEST && s.type() == Items.ELYTRA)
                        && !(slot == EquipSlot.HAND && GearScoring.isSpear(s))) {
                    drops.add(s.copy());
                }
            }
            for (int i = 0; i < SIZE; i++) {
                GameItem s = main[i];
                if (s.isEmpty()) {
                    continue;
                }
                if (keep && (i == keepSword || i == keepPick || keptSources.contains(i) || keepNamedCombatItem(s))) {
                    continue;
                }
                drops.add(s.copy());
                main[i] = GameItem.empty();
            }
        }
        drops.addAll(bot.progression().deathDrops(dropInventory));
        if (mode == Modes.GearPersistence.DROP_AND_LOSE) {
            clearPersistentGear();
        }
        return drops;
    }

    /** For each worn/held piece, the inventory slot it was copied from (if any). */
    private Map<EquipSlot, Integer> equippedSourceSlots() {
        Map<EquipSlot, Integer> out = new EnumMap<>(EquipSlot.class);
        Set<Integer> used = new HashSet<>();
        for (EquipSlot slot : EQUIPMENT) {
            GameItem s = bot.getItem(slot);
            if (s.isEmpty()) {
                continue;
            }
            for (int i = 0; i < SIZE; i++) {
                if (!used.contains(i) && main[i].isSimilar(s)) {
                    used.add(i);
                    out.put(slot, i);
                    break;
                }
            }
        }
        return out;
    }

    private void reevaluateAndEquipBestGearSafe() {
        if (bot.isAlive()) {
            GameItem bestSword = findBestSword();
            if (!bestSword.isEmpty() && GearScoring.isBetterSword(bestSword, persistedSword)) {
                persistedSword = bestSword.copy();
            }
        }
    }

    private boolean keepNamedCombatItem(GameItem s) {
        return s.hasCustomName() && (GearScoring.isSword(s) || GearScoring.isPickaxe(s));
    }

    private int findBestSwordSlot() {
        int bestSlot = -1;
        float bestScore = GearScoring.attackScore(persistedSword);
        for (int i = 0; i < SIZE; i++) {
            float score = GearScoring.attackScore(main[i]);
            if (score > bestScore + 0.01f) {
                bestScore = score;
                bestSlot = i;
            }
        }
        if (bestSlot != -1) {
            return bestSlot;
        }
        for (int i = 0; i < SIZE; i++) {
            if (GearScoring.isSword(main[i])) {
                return i;
            }
        }
        return -1;
    }

    private int findBestPickaxeSlot() {
        int bestSlot = -1;
        float bestScore = GearScoring.pickaxeScore(persistedPickaxe);
        for (int i = 0; i < SIZE; i++) {
            float score = GearScoring.pickaxeScore(main[i]);
            if (score > bestScore + 0.01f) {
                bestScore = score;
                bestSlot = i;
            }
        }
        if (bestSlot != -1) {
            return bestSlot;
        }
        for (int i = 0; i < SIZE; i++) {
            if (GearScoring.isPickaxe(main[i])) {
                return i;
            }
        }
        return -1;
    }

    /** Drops an item into the world as a player would. */
    public static void dropAt(Bot bot, GameItem stack) {
        if (GearScoring.isEmpty(stack) || bot.world() == null) {
            return;
        }
        Vec3 p = bot.pos();
        GameItemEntity item = bot.world().dropItemNaturally(p.add(0, 1.0, 0), stack);
        item.setPickupDelay(40);
    }
}
