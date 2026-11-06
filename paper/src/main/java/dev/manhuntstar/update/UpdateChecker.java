package dev.manhuntstar.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.manhuntstar.ManhuntHost;
import dev.manhuntstar.config.Messages;
import dev.manhuntstar.platform.GamePlayer;
import dev.manhuntstar.platform.ScheduledTask;
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

/**
 * Looks for a newer release on GitHub when the plugin starts and every 6 hours (off the main
 * thread). A newer version is logged once to the console and shown to admins when they join.
 * Turned off with the {@code general.update-check} setting.
 */
public final class UpdateChecker {

    public static final String PERMISSION = "manhuntstar.admin";
    private static final URI LATEST_RELEASE = URI.create("https://api.github.com/repos/KAIKOAUGUSTIN/ManhuntStar/releases/latest");
    private static final long FIRST_CHECK_TICKS = 20L * 10;
    private static final long INTERVAL_TICKS = 20L * 60 * 60 * 6;

    /** A release newer than the running version. */
    public record Release(String version, String url) {
    }

    private final ManhuntHost plugin;
    private final String current;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private ScheduledTask task;
    private volatile Release available;
    private volatile String logged;

    /** @param current the running version */
    public UpdateChecker(ManhuntHost plugin, String current) {
        this.plugin = plugin;
        this.current = current;
    }

    /** Starts or stops the checks to match the setting. */
    public void apply(boolean enabled) {
        if (enabled && task == null) {
            task = plugin.server().runTimerAsync(this::check, FIRST_CHECK_TICKS, INTERVAL_TICKS);
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
                plugin.logger().fine("Update check: GitHub answered HTTP " + response.statusCode() + ".");
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
                plugin.logger().warning("A new version of ManhuntStar is out: " + latest + " (this server runs " + current
                        + "). Download it at " + url);
                plugin.server().runNextTick(() -> {
                    for (GamePlayer p : plugin.server().onlinePlayers()) {
                        notify(p, release);
                    }
                });
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            plugin.logger().log(Level.FINE, "Update check failed", e);
        }
    }

    /** A player joined: admins hear about the new release a moment later. */
    public void onJoin(GamePlayer p) {
        Release release = available;
        if (release == null) {
            return;
        }
        plugin.server().runLater(() -> notify(p, release), 40L);
    }

    private void notify(GamePlayer p, Release release) {
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
