package dev.manhuntstar.listener;

import dev.manhuntstar.paper.PaperEntities;
import dev.manhuntstar.update.UpdateChecker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Tells the update check that a player joined (admins get the new-release message). */
public final class UpdateNotifyListener implements Listener {

    private final UpdateChecker updates;

    public UpdateNotifyListener(UpdateChecker updates) {
        this.updates = updates;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        updates.onJoin(PaperEntities.of(event.getPlayer()));
    }
}
