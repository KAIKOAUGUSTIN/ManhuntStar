package dev.manhuntstar.platform;

/** The death of a bot's body as the platform reports it: what killed it and what it drops. */
public interface BotDeath {

    DamageInfo source();

    void clearDrops();

    void addDrop(GameItem item);

    void setDroppedExp(int exp);
}
