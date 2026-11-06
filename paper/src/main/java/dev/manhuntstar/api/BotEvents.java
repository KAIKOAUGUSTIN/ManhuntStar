package dev.manhuntstar.api;

import dev.manhuntstar.bot.Bot;
import dev.manhuntstar.paper.PaperDamageInfo;
import dev.manhuntstar.paper.PaperEntities;
import dev.manhuntstar.platform.DamageInfo;
import dev.manhuntstar.platform.GamePlayer;
import org.bukkit.Bukkit;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Bukkit events of the bots (matching the mod API's events), for other plugins. */
public final class BotEvents {

    private BotEvents() {
    }

    /** Calls {@link BotDeathEvent} for a bot that died. */
    public static void fireDeath(Bot bot, DamageInfo source) {
        Bukkit.getPluginManager().callEvent(new BotDeathEvent(bot, PaperDamageInfo.bukkit(source)));
    }

    /** Calls {@link BotKilledByPlayerEvent} for a bot a player killed. */
    public static void fireKilledByPlayer(Bot bot, GamePlayer player) {
        Bukkit.getPluginManager().callEvent(new BotKilledByPlayerEvent(bot, PaperEntities.bukkit(player)));
    }

    /** Base: every event has the bot involved. */
    public abstract static class BotEvent extends Event {
        private final Bot bot;

        protected BotEvent(Bot bot) {
            this.bot = bot;
        }

        public Bot getBot() {
            return bot;
        }
    }

    /** A player is about to damage the bot (cancellable; the damage can be changed). */
    public static final class BotDamageByPlayerEvent extends BotEvent implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();
        private final Player player;
        private double damage;
        private boolean cancelled;

        public BotDamageByPlayerEvent(Bot bot, Player player, double damage) {
            super(bot);
            this.player = player;
            this.damage = damage;
        }

        public Player getPlayer() {
            return player;
        }

        public double getDamage() {
            return damage;
        }

        public void setDamage(double damage) {
            this.damage = damage;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancel) {
            this.cancelled = cancel;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /** The bot is about to take fall damage (cancellable). */
    public static final class BotFallDamageEvent extends BotEvent implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();
        private final double damage;
        private boolean cancelled;

        public BotFallDamageEvent(Bot bot, double damage) {
            super(bot);
            this.damage = damage;
        }

        public double getDamage() {
            return damage;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancel) {
            this.cancelled = cancel;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /** The bot died. */
    public static final class BotDeathEvent extends BotEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        private final DamageSource source;

        public BotDeathEvent(Bot bot, DamageSource source) {
            super(bot);
            this.source = source;
        }

        public DamageSource getDamageSource() {
            return source;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /** The bot was killed by a player. */
    public static final class BotKilledByPlayerEvent extends BotEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        private final Player player;

        public BotKilledByPlayerEvent(Bot bot, Player player) {
            super(bot);
            this.player = player;
        }

        public Player getPlayer() {
            return player;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }
}
