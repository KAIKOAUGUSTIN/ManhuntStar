# Error reports (Sentry)

**Status:** decided, not built yet. It is waiting for the Sentry project and its DSN.

## Decision

- **What:** the plugin and the mods send ManhuntStar's own errors to Sentry, using only Sentry's error monitoring.
- **bStats stays.** The two do different jobs:
  - bStats hears from every server, so it shows how many servers use ManhuntStar and which versions.
  - Sentry only hears from servers that hit an error.

  Making Sentry count servers would mean a periodic signal from every server. That would collect more than bStats (one record per server instead of totals) and use up the free quota.
- **Why now:** the Fabric and NeoForge mods are new and haven't been tested in a real client yet. Most people uninstall instead of reporting an error.

## The Sentry project

- **Platform:** Java, with only Error monitoring ticked. Leave the other products off:
  - **Logging** would send the server log: the chat, player names, and the IP of everyone who joins.
  - **Tracing and Profiling** time the code. They cost time on the tick and send data even when nothing goes wrong.
  - **Application Metrics** does what bStats already does.

  The product boxes only change the setup instructions Sentry shows. The SDK settings in ManhuntStar decide what is sent.
- **Settings → Security & Privacy:**
  - turn on Prevent Storing of IP Addresses;
  - keep Data Scrubber and Use Default Scrubbers on (they are on by default).
- **DSN:** it goes inside the jar. It isn't a secret.

## What one report contains

- the stack trace;
- the versions of ManhuntStar, Minecraft, the platform (Paper, Fabric or NeoForge) and Java;
- the operating system.

Nothing else is sent.

## Rules in the code

- **Only ManhuntStar's errors.**
  - Reports go out from where ManhuntStar already catches its errors (tick, commands, listeners, scheduled tasks). It doesn't install a global uncaught-exception handler.
  - Before sending, drop any event without a `dev.manhuntstar` frame in its stack trace.
- **No personal data.**
  - `sendDefaultPii` off.
  - No server name. The Java SDK sends the machine's host name by default.
  - No breadcrumbs or log lines.
  - Strip file paths from exception messages, because Windows paths carry the user name (`C:\Users\<name>\...`).
- **No repeats.**
  - Each distinct error goes out at most once per server start.
  - Each start sends only a small number of reports in total.

  An error on the tick would otherwise repeat 20 times a second.
- **Errors only.** No traces, profiling, logs, metrics or session tracking.
- **ManhuntStar's own Sentry client, not the global `Sentry.init`.** This way it doesn't clash with another plugin or mod that uses Sentry.
  - The Paper jar relocates the SDK.
  - The mods embed it as jar-in-jar. Check that this doesn't clash with other mods that use Sentry.
- **Off the main thread.** If the host blocks outgoing connections, sending fails quietly and the server isn't affected.
- **Mods: dedicated servers only.** In singleplayer and LAN the "server" is the player's own computer, often a minor's, so nothing is sent there.
- **A switch in `config.yml`.** It is on by default, like bStats, and its comment says what is sent and where.

## Telling people

- the comment on the switch in `config.yml`;
- a section in the README;
- the telemetry disclosure on Modrinth (the same screen that lists bStats);
- the rules on CurseForge and Hangar, checked before the first version that sends reports;
- a line in that version's release notes, saying how to turn it off.

## Before the release

- Test against a local fake Sentry and check the exact content of a report.
- Check how much bigger the SDK makes the jars.
