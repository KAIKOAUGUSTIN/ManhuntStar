package dev.manhuntstar.api;

import dev.manhuntstar.bot.Bot;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Eventos Bukkit dos bots (equivalentes aos eventos da API do mod), para outros plugins. */
public final class BotEvents {

    private BotEvents() {
    }

    /** Base: todo evento tem o bot envolvido. */
    public abstract static class BotEvent extends Event {
        private final Bot bot;

        protected BotEvent(Bot bot) {
            this.bot = bot;
        }

        public Bot getBot() {
            return bot;
        }
    }

    /** Um jogador vai causar dano ao bot (cancelável; o dano pode ser alterado). */
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

    /** O bot vai levar dano de queda (cancelável). */
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

    /** O bot morreu. */
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

    /** O bot foi morto por um jogador. */
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
