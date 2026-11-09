package dev.manhuntstar.platform;

/** A repeating task started with {@link GameServer#runTimerAsync}. */
public interface ScheduledTask {

    void cancel();
}
