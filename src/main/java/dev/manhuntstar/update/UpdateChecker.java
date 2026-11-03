package dev.manhuntstar.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.manhuntstar.ManhuntStarPlugin;
import dev.manhuntstar.config.Messages;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Looks for a newer release on GitHub when the plugin starts and every 6 hours (off the main
 * thread). A newer version is logged once to the console and shown to admins when they join.
 * Turned off with the {@code general.update-check} setting.
 */
public final class UpdateChecker implements Listener {

    public static final String PERMISSION = "manhuntstar.admin";
    private static final URI LATEST_RELEASE = URI.create("https://api.github.com/repos/KAIKOAUGUSTIN/ManhuntStar/releases/latest");
    private static final long FIRST_CHECK_TICKS = 20L * 10;
    private static final long INTERVAL_TICKS = 20L * 60 * 60 * 6;

    /** A release newer than the running version. */
    public record Release(String version, String url) {
    }

    private final ManhuntStarPlugin plugin;
    private final String current;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private BukkitTask task;
    private volatile Release available;
    private volatile String logged;

    public UpdateChecker(ManhuntStarPlugin plugin) {
        this.plugin = plugin;
        this.current = plugin.getPluginMeta().getVersion();
    }

    /** Starts or stops the checks to match the setting. */
    public void apply(boolean enabled) {
        if (enabled && task == null) {
            task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::check, FIRST_CHECK_TICKS, INTERVAL_TICKS);
        } else if (!enabled && task != null) {
            task.cancel();
            task = null;
            available = null;
        }
    }

    public void shutdown() {
        apply(false);
    }

    /** The newer release found by the last check, or null. */
    public Release available() {
        return available;
    }

    private void check() {
        try {
            HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE)
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "ManhuntStar/" + current)
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // 403/429: rate limited (60 requests an hour per IP); 404: no release yet
                plugin.getLogger().fine("Update check: GitHub answered HTTP " + response.statusCode() + ".");
                return;
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String latest = stripPrefix(json.get("tag_name").getAsString());
            String url = json.has("html_url") ? json.get("html_url").getAsString() : "https://github.com/KAIKOAUGUSTIN/ManhuntStar/releases";
            if (compare(latest, current) <= 0) {
                available = null;
                return;
            }
            Release release = new Release(latest, url);
            available = release;
            if (!latest.equals(logged)) {
                logged = latest;
                plugin.getLogger().warning("A new version of ManhuntStar is out: " + latest + " (this server runs " + current
                        + "). Download it at " + url);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        notify(p, release);
                    }
                });
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Update check failed", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Release release = available;
        if (release == null) {
            return;
        }
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> notify(p, release), 40L);
    }

    private void notify(Player p, Release release) {
        if (!p.isOnline() || !p.hasPermission(PERMISSION) || available == null) {
            return;
        }
        Messages messages = plugin.messages();
        Component link = messages.get("update.download")
                .clickEvent(ClickEvent.openUrl(release.url()))
                .hoverEvent(HoverEvent.showText(messages.get("update.hover")));
        messages.send(p, "update.available",
                Messages.p("version", release.version()),
                Messages.p("current", current),
                Messages.c("link", link));
    }

    private static String stripPrefix(String tag) {
        String t = tag.trim();
        return t.startsWith("v") || t.startsWith("V") ? t.substring(1) : t;
    }

    /**
     * Compares two versions by their numbers ("1.10.0" is newer than "1.9.3"); missing parts count as
     * 0 and a suffix such as "-SNAPSHOT" is ignored.
     */
    static int compare(String a, String b) {
        List<Integer> x = numbers(a);
        List<Integer> y = numbers(b);
        for (int i = 0; i < Math.max(x.size(), y.size()); i++) {
            int cmp = Integer.compare(i < x.size() ? x.get(i) : 0, i < y.size() ? y.get(i) : 0);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private static List<Integer> numbers(String version) {
        List<Integer> out = new ArrayList<>();
        String core = stripPrefix(version).split("[-+ ]", 2)[0];
        for (String part : core.split("\\.")) {
            try {
                out.add(Integer.parseInt(part));
            } catch (NumberFormatException e) {
                break;
            }
        }
        return out;
    }
}
