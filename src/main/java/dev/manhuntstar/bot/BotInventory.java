package dev.manhuntstar.bot;

import dev.manhuntstar.util.Vec3;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mannequin;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Inventário virtual do bot (36 espaços, como o de um jogador) e controle do melhor equipamento:
 * coleta de itens/XP, troca automática para a melhor espada/armadura e persistência de equipamento entre mortes.
 */
public final class BotInventory {

    public static final int SIZE = 36;
    private static final double PICKUP_RADIUS = 1.8;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

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

    /** Adiciona ao inventário; retorna o que sobrou. */
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

    // ------------------------------------------------------------------ equipamento preferido

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

    /** Chamado sempre que um slot de equipamento muda: atualiza o equipamento persistente se for melhor. */
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

    /** Restaura o equipamento persistente e equipa o que for melhor que o atual. */
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
        if (!bestSword.isEmpty() && (currentMain.isEmpty() || !GearScoring.isSword(currentMain) || GearScoring.isBetterSword(bestSword, currentMain))) {
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

    // ------------------------------------------------------------------ coleta

    private void pickupNearbyLootAndXp() {
        Mannequin e = bot.entity();
        if (e == null) {
            return;
        }
        for (Entity near : e.getNearbyEntities(PICKUP_RADIUS, PICKUP_RADIUS, PICKUP_RADIUS)) {
            if (near instanceof Item item) {
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
                e.playPickupItemAnimation(item, taken);
                if (left.isEmpty()) {
                    item.remove();
                } else {
                    item.setItemStack(left);
                }
                e.getWorld().playSound(e.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.2f,
                        (ThreadLocalRandom.current().nextFloat() - ThreadLocalRandom.current().nextFloat()) * 1.4f + 2.0f);
            } else if (near instanceof ExperienceOrb orb && orb.isValid()) {
                orb.remove();
                e.getWorld().playSound(e.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.1f,
                        0.5f + ThreadLocalRandom.current().nextFloat() * 0.6f);
            }
        }
    }

    // ------------------------------------------------------------------ morte

    /** Itens a dropar quando o bot morre, conforme as opções de drop e de persistência. */
    public List<ItemStack> collectDeathDrops(boolean dropInventory, Modes.GearPersistence mode) {
        List<ItemStack> drops = new ArrayList<>();
        reevaluateAndEquipBestGearSafe();
        if (dropInventory) {
            boolean keep = mode == Modes.GearPersistence.KEEP_ON_RESPAWN_NO_DROP;
            int keepSword = keep ? findBestSwordSlot() : -1;
            int keepPick = keep ? findBestPickaxeSlot() : -1;
            for (int i = 0; i < SIZE; i++) {
                ItemStack s = main[i];
                if (s.isEmpty()) {
                    continue;
                }
                if (keep && (i == keepSword || i == keepPick || keepNamedCombatItem(s))) {
                    continue;
                }
                drops.add(s.clone());
                main[i] = ItemStack.empty();
            }
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack s = bot.getItem(slot);
                if (s.isEmpty()) {
                    continue;
                }
                if (keep && (isArmorSlot(slot) || (slot == EquipmentSlot.HAND && (GearScoring.isSword(s) || GearScoring.isPickaxe(s))) || keepNamedCombatItem(s))) {
                    continue;
                }
                drops.add(s.clone());
            }
        }
        drops.addAll(bot.progression().deathDrops(dropInventory));
        if (mode == Modes.GearPersistence.DROP_AND_LOSE) {
            clearPersistentGear();
        }
        return drops;
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

    /** Derruba um item no mundo como um jogador faria. */
    public static void dropAt(Bot bot, ItemStack stack) {
        if (GearScoring.isEmpty(stack) || bot.world() == null) {
            return;
        }
        Vec3 p = bot.pos();
        Item item = bot.world().dropItemNaturally(p.add(0, 1.0, 0).toLocation(bot.world()), stack);
        item.setPickupDelay(40);
    }
}
