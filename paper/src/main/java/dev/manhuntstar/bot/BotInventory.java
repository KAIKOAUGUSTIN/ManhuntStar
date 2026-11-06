package dev.manhuntstar.bot;

import dev.manhuntstar.paper.PaperEntities;
import dev.manhuntstar.platform.BotBody;
import dev.manhuntstar.platform.GameEntity;
import dev.manhuntstar.platform.Sounds;
import dev.manhuntstar.util.Locations;
import dev.manhuntstar.util.Vec3;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The bot's virtual inventory (36 slots, like a player's) and handling of the best equipment:
 * picking up items/XP, automatic switch to the best sword/armor and keeping equipment between deaths.
 */
public final class BotInventory {

    public static final int SIZE = 36;
    private static final double PICKUP_RADIUS = 1.8;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final EquipmentSlot[] EQUIPMENT = {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final Bot bot;
    private final ItemStack[] main = new ItemStack[SIZE];
    private ItemStack persistedSword = ItemStack.empty();
    private ItemStack persistedPickaxe = ItemStack.empty();
    private final EnumMap<EquipmentSlot, ItemStack> persistedArmor = new EnumMap<>(EquipmentSlot.class);

    BotInventory(Bot bot) {
        this.bot = bot;
        for (int i = 0; i < SIZE; i++) {
            main[i] = ItemStack.empty();
        }
        for (EquipmentSlot s : ARMOR) {
            persistedArmor.put(s, ItemStack.empty());
        }
    }

    public ItemStack get(int slot) {
        return main[slot];
    }

    public void set(int slot, ItemStack stack) {
        main[slot] = stack == null ? ItemStack.empty() : stack;
    }

    public void clear() {
        for (int i = 0; i < SIZE; i++) {
            main[i] = ItemStack.empty();
        }
    }

    /** Adds to the inventory; returns what's left over. */
    public ItemStack add(ItemStack stack) {
        if (GearScoring.isEmpty(stack)) {
            return ItemStack.empty();
        }
        ItemStack remaining = stack.clone();
        for (int i = 0; i < SIZE && remaining.getAmount() > 0; i++) {
            ItemStack cur = main[i];
            if (!cur.isEmpty() && cur.isSimilar(remaining)) {
                int space = cur.getMaxStackSize() - cur.getAmount();
                if (space > 0) {
                    int move = Math.min(space, remaining.getAmount());
                    cur.setAmount(cur.getAmount() + move);
                    remaining.setAmount(remaining.getAmount() - move);
                }
            }
        }
        for (int i = 0; i < SIZE && remaining.getAmount() > 0; i++) {
            if (main[i].isEmpty()) {
                main[i] = remaining.clone();
                remaining.setAmount(0);
            }
        }
        return remaining.getAmount() > 0 ? remaining : ItemStack.empty();
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

    public ItemStack preferredMainHand() {
        ItemStack current = bot.getItem(EquipmentSlot.HAND);
        if (GearScoring.isSword(current)) {
            return current.clone();
        }
        if (GearScoring.isSword(persistedSword)) {
            return persistedSword.clone();
        }
        ItemStack best = findBestSword();
        if (!best.isEmpty()) {
            return best.clone();
        }
        return bot.progression().mainHandItem();
    }

    public ItemStack preferredPickaxe() {
        ItemStack current = bot.getItem(EquipmentSlot.HAND);
        if (GearScoring.isPickaxe(current)) {
            return current.clone();
        }
        if (GearScoring.isPickaxe(persistedPickaxe)) {
            return persistedPickaxe.clone();
        }
        ItemStack best = findBestPickaxe();
        if (!best.isEmpty()) {
            return best.clone();
        }
        return new ItemStack(Material.DIAMOND_PICKAXE);
    }

    public void restorePreferredMainHand() {
        if (bot.capacityLocked()) {
            return;
        }
        ItemStack preferred = preferredMainHand();
        if (!preferred.isEmpty() && !preferred.isSimilar(bot.getItem(EquipmentSlot.HAND))) {
            bot.setItem(EquipmentSlot.HAND, preferred);
        }
    }

    public void restorePreferredPickaxe() {
        ItemStack preferred = preferredPickaxe();
        if (!preferred.isEmpty() && !preferred.isSimilar(bot.getItem(EquipmentSlot.HAND))) {
            bot.setItem(EquipmentSlot.HAND, preferred);
        }
    }

    /** Called whenever an equipment slot changes: updates the persistent equipment if it's better. */
    void onEquipmentChanged(EquipmentSlot slot, ItemStack stack) {
        if (slot == EquipmentSlot.HAND) {
            if (GearScoring.isSword(stack) && GearScoring.attackScore(stack) >= GearScoring.attackScore(persistedSword) - 0.01f) {
                persistedSword = stack.clone();
            }
            if (GearScoring.isPickaxe(stack) && GearScoring.pickaxeScore(stack) >= GearScoring.pickaxeScore(persistedPickaxe) - 0.01f) {
                persistedPickaxe = stack.clone();
            }
            return;
        }
        if (isArmorSlot(slot) && GearScoring.isArmorForSlot(stack, slot)) {
            ItemStack cur = persistedArmor.get(slot);
            if (cur.isEmpty() || GearScoring.armorScore(stack, slot) >= GearScoring.armorScore(cur, slot) - 0.01f) {
                persistedArmor.put(slot, stack.clone());
            }
        }
    }

    private static boolean isArmorSlot(EquipmentSlot s) {
        return s == EquipmentSlot.HEAD || s == EquipmentSlot.CHEST || s == EquipmentSlot.LEGS || s == EquipmentSlot.FEET;
    }

    public PersistentGear snapshotPersistentGear() {
        return new PersistentGear(persistedSword.clone(), persistedPickaxe.clone(), persistedArmor.get(EquipmentSlot.HEAD).clone(),
                persistedArmor.get(EquipmentSlot.CHEST).clone(), persistedArmor.get(EquipmentSlot.LEGS).clone(), persistedArmor.get(EquipmentSlot.FEET).clone());
    }

    /** Restores the persistent equipment and equips whatever is better than the current one. */
    public void applyPersistentGear(PersistentGear gear) {
        if (gear == null) {
            return;
        }
        if (!GearScoring.isEmpty(gear.sword())) {
            persistedSword = gear.sword().clone();
        }
        if (!GearScoring.isEmpty(gear.pickaxe())) {
            persistedPickaxe = gear.pickaxe().clone();
        }
        for (EquipmentSlot s : ARMOR) {
            ItemStack a = gear.armor(s);
            if (!GearScoring.isEmpty(a)) {
                persistedArmor.put(s, a.clone());
            }
        }
        if (!bot.isAlive()) {
            return;
        }
        if (!persistedSword.isEmpty() && GearScoring.isBetterSword(persistedSword, bot.getItem(EquipmentSlot.HAND))) {
            bot.setItem(EquipmentSlot.HAND, persistedSword.clone());
        }
        for (EquipmentSlot s : ARMOR) {
            applyPersistedArmorIfBetter(s);
        }
    }

    public void clearPersistentGear() {
        persistedSword = ItemStack.empty();
        persistedPickaxe = ItemStack.empty();
        for (EquipmentSlot s : ARMOR) {
            persistedArmor.put(s, ItemStack.empty());
        }
    }

    private void applyPersistedArmorIfBetter(EquipmentSlot slot) {
        ItemStack persisted = persistedArmor.get(slot);
        if (persisted == null || persisted.isEmpty()) {
            return;
        }
        ItemStack current = bot.getItem(slot);
        if (current.isEmpty() || GearScoring.isBetterArmor(persisted, current, slot)) {
            bot.setItem(slot, persisted.clone());
        }
    }

    private void reevaluateAndEquipBestGear() {
        if (bot.combat().isChargingCrossbow() || bot.combat().isChargingTrident()) {
            return;
        }
        if (bot.mining().isMining() || bot.survival().isEating()) {
            return;
        }
        ItemStack bestSword = findBestSword();
        if (!bestSword.isEmpty() && GearScoring.isBetterSword(bestSword, persistedSword)) {
            persistedSword = bestSword.clone();
        }
        ItemStack bestPick = findBestPickaxe();
        if (!bestPick.isEmpty() && GearScoring.isBetterPickaxe(bestPick, persistedPickaxe)) {
            persistedPickaxe = bestPick.clone();
        }
        ItemStack currentMain = bot.getItem(EquipmentSlot.HAND);
        // on horseback the bot holds the charge spear (HorseRoutine); the sword comes back when it dismounts
        boolean ridingWithSpear = bot.isRiding() && GearScoring.isSpear(currentMain);
        if (!bestSword.isEmpty() && !ridingWithSpear
                && (currentMain.isEmpty() || !GearScoring.isSword(currentMain) || GearScoring.isBetterSword(bestSword, currentMain))) {
            bot.setItem(EquipmentSlot.HAND, bestSword.clone());
        }
        for (EquipmentSlot s : ARMOR) {
            equipBestArmor(s);
        }
    }

    private void equipBestArmor(EquipmentSlot slot) {
        ItemStack best = findBestArmor(slot);
        if (best.isEmpty()) {
            return;
        }
        ItemStack current = bot.getItem(slot);
        if (current.isEmpty() || GearScoring.isBetterArmor(best, current, slot)) {
            bot.setItem(slot, best.clone());
        }
        ItemStack persisted = persistedArmor.get(slot);
        if (persisted.isEmpty() || GearScoring.isBetterArmor(best, persisted, slot)) {
            persistedArmor.put(slot, best.clone());
        }
    }

    private ItemStack findBestSword() {
        ItemStack best = ItemStack.empty();
        float bestScore = -1f;
        for (ItemStack s : main) {
            float score = GearScoring.attackScore(s);
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        if (GearScoring.attackScore(persistedSword) > bestScore) {
            best = persistedSword;
        }
        return best.isEmpty() ? ItemStack.empty() : best.clone();
    }

    private ItemStack findBestPickaxe() {
        ItemStack best = ItemStack.empty();
        float bestScore = -1f;
        for (ItemStack s : main) {
            float score = GearScoring.pickaxeScore(s);
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        if (GearScoring.pickaxeScore(persistedPickaxe) > bestScore) {
            best = persistedPickaxe;
        }
        return best.isEmpty() ? ItemStack.empty() : best.clone();
    }

    private ItemStack findBestArmor(EquipmentSlot slot) {
        ItemStack best = ItemStack.empty();
        float bestScore = -1f;
        for (ItemStack s : main) {
            float score = GearScoring.armorScore(s, slot);
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        ItemStack persisted = persistedArmor.get(slot);
        if (GearScoring.armorScore(persisted, slot) > bestScore) {
            best = persisted;
        }
        return best.isEmpty() ? ItemStack.empty() : best.clone();
    }

    // ------------------------------------------------------------------ pickup

    private void pickupNearbyLootAndXp() {
        BotBody e = bot.entity();
        if (e == null) {
            return;
        }
        for (GameEntity near : e.nearbyEntities(PICKUP_RADIUS, PICKUP_RADIUS, PICKUP_RADIUS)) {
            if (PaperEntities.bukkit(near) instanceof Item item) {
                if (!item.isValid() || item.getPickupDelay() > 0 || !item.canMobPickup()) {
                    continue;
                }
                ItemStack stack = item.getItemStack();
                int before = stack.getAmount();
                ItemStack left = add(stack);
                int taken = before - left.getAmount();
                if (taken <= 0) {
                    continue;
                }
                PaperEntities.bukkit(e).playPickupItemAnimation(item, taken);
                if (left.isEmpty()) {
                    item.remove();
                } else {
                    item.setItemStack(left);
                }
                e.world().playSound(e.position(), Sounds.ENTITY_ITEM_PICKUP, dev.manhuntstar.platform.SoundCategory.PLAYERS, 0.2f,
                        (ThreadLocalRandom.current().nextFloat() - ThreadLocalRandom.current().nextFloat()) * 1.4f + 2.0f);
            } else if (PaperEntities.bukkit(near) instanceof ExperienceOrb orb && orb.isValid()) {
                orb.remove();
                e.world().playSound(e.position(), Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, dev.manhuntstar.platform.SoundCategory.PLAYERS, 0.1f,
                        0.5f + ThreadLocalRandom.current().nextFloat() * 0.6f);
            }
        }
    }

    // ------------------------------------------------------------------ death

    /** Items to drop when the bot dies, according to the drop and persistence options. */
    public List<ItemStack> collectDeathDrops(boolean dropInventory, Modes.GearPersistence mode) {
        List<ItemStack> drops = new ArrayList<>();
        reevaluateAndEquipBestGearSafe();
        if (dropInventory) {
            boolean keep = mode == Modes.GearPersistence.KEEP_ON_RESPAWN_NO_DROP;
            int keepSword = keep ? findBestSwordSlot() : -1;
            int keepPick = keep ? findBestPickaxeSlot() : -1;
            // What the bot wears or holds and came from the inventory is a copy of the item there: it's one item.
            // It used to drop twice (or drop and stay with the bot in the mode that keeps it).
            Map<EquipmentSlot, Integer> source = equippedSourceSlots();
            Set<Integer> keptSources = new HashSet<>();
            for (EquipmentSlot slot : EQUIPMENT) {
                ItemStack s = bot.getItem(slot);
                if (s.isEmpty()) {
                    continue;
                }
                Integer from = source.get(slot);
                if (keep && (isArmorSlot(slot) || (slot == EquipmentSlot.HAND && (GearScoring.isSword(s) || GearScoring.isPickaxe(s))) || keepNamedCombatItem(s))) {
                    if (from != null) {
                        keptSources.add(from);
                    }
                    continue;
                }
                // the worn elytra is lent for flying (the {@code mobility.elytra} option) and the spear is outside the inventory,
                // for the horseback charge: they don't belong to the bot
                if (from == null && !(slot == EquipmentSlot.CHEST && s.getType() == Material.ELYTRA)
                        && !(slot == EquipmentSlot.HAND && GearScoring.isSpear(s))) {
                    drops.add(s.clone());
                }
            }
            for (int i = 0; i < SIZE; i++) {
                ItemStack s = main[i];
                if (s.isEmpty()) {
                    continue;
                }
                if (keep && (i == keepSword || i == keepPick || keptSources.contains(i) || keepNamedCombatItem(s))) {
                    continue;
                }
                drops.add(s.clone());
                main[i] = ItemStack.empty();
            }
        }
        drops.addAll(bot.progression().deathDrops(dropInventory));
        if (mode == Modes.GearPersistence.DROP_AND_LOSE) {
            clearPersistentGear();
        }
        return drops;
    }

    /** For each worn/held piece, the inventory slot it was copied from (if any). */
    private Map<EquipmentSlot, Integer> equippedSourceSlots() {
        Map<EquipmentSlot, Integer> out = new EnumMap<>(EquipmentSlot.class);
        Set<Integer> used = new HashSet<>();
        for (EquipmentSlot slot : EQUIPMENT) {
            ItemStack s = bot.getItem(slot);
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
            ItemStack bestSword = findBestSword();
            if (!bestSword.isEmpty() && GearScoring.isBetterSword(bestSword, persistedSword)) {
                persistedSword = bestSword.clone();
            }
        }
    }

    private boolean keepNamedCombatItem(ItemStack s) {
        return s.hasItemMeta() && s.getItemMeta().hasCustomName() && (GearScoring.isSword(s) || GearScoring.isPickaxe(s));
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
    public static void dropAt(Bot bot, ItemStack stack) {
        if (GearScoring.isEmpty(stack) || bot.world() == null) {
            return;
        }
        Vec3 p = bot.pos();
        Item item = bot.bukkitWorld().dropItemNaturally(Locations.location(p.add(0, 1.0, 0), bot.world()), stack);
        item.setPickupDelay(40);
    }
}
