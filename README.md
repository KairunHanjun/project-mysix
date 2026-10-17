# Project Mysix

Minecraft Java 26.3 / Fabric / JDK 25. Mod ID: `mysix`.

## Install the public prerelease

Download the regular JAR from [GitHub Releases](https://github.com/KairunHanjun/project-mysix/releases),
and install it alongside Fabric API in the client and server `mods` folders.
Use Minecraft **26.3**, Fabric Loader **0.19.5 or newer**, and **Java 25**.
The `-sources.jar` is for developers, not installation. The server also needs
**yt-dlp, FFmpeg/ffprobe, and Deno** available on its PATH. In singleplayer,
the server runs on your own computer. Clients joining a dedicated server need
the mod and Fabric API but do not need those external tools.

Craft a CD, rename it in an anvil to an artist/title or YouTube video link, then
insert it into a jukebox. This is an early prerelease; see [CHANGELOG.md](CHANGELOG.md)
for limitations. No downloaded music is distributed with the mod.

## Build and publish

Run `./gradlew build` (Windows: `.\gradlew.bat build`) with JDK 25.
The installable mod and developer sources are written to `build/libs/`.
The build runs the offline suite plus the dedicated-server environment tests;
real-song network tests remain an explicit `networkTest` task.

GitHub Actions builds every push to `main` and every pull request. To publish
the next version, update `version` in `gradle.properties` and `CHANGELOG.md`,
commit and push, then create and push a matching tag such as `v0.1.1`.
The tag workflow checks that the versions match, runs the tests, and publishes
a GitHub prerelease containing both JARs and SHA-256 checksums. Release notes
come from `CHANGELOG.md`. Releases remain prereleases until the workflow is
deliberately changed for a stable release.

## Milestones 1–2: craftable CDs and vanilla jukebox playback

Sixteen colored CDs, one per vanilla dye. Each stack holds one CD, ready for independent state later. Find them under Tools & Utilities or `/give @s mysix:red_cd`.

Craft at a crafting table:

| Glass pane | Glass pane | Glass pane |
|---|---|---|
| Glass pane | Redstone | Glass pane |
| Glass pane | Color dye | Glass pane |

Cost: seven glass panes, one redstone, one dye; output: one matching CD. Collect the dye to unlock its recipe. CDs can be renamed in an anvil.

All 16 CDs default to the vanilla **C418 — Cat** song. Right-click an empty jukebox with a CD to insert and play it; right-click the occupied jukebox to eject and stop it. Minecraft handles the ordinary jukebox sound, distance attenuation, playback synchronization, and ejection for vanilla songs. Renamed external recordings use the online integration described below.

## Milestone 3: anvil-selected vanilla songs

Rename any Mysix CD in an anvil to the full vanilla artist/title, such as `C418 - Cat`, `C418 - Stal`, `Lena Raine - Pigstep`, `Lena Raine - Creator (Music Box)`, or `Aaron Cherof - Relic`. All 22 vanilla disc recordings in Minecraft 26.3 are supported; the complete names are in `src/main/resources/data/mysix/vanilla_songs.json`. Matching ignores case, repeated whitespace, and separator spacing, and accepts a hyphen, en dash, or em dash. Titles and versions are otherwise exact: `C418 - Cat Live` does not substitute Cat.

The anvil result gets the selected `JUKEBOX_PLAYABLE` component before pickup. An unknown custom name removes the old song component and is handed to the online lookup at insertion. If the online service is unavailable or lookup fails, vanilla's ejection method drops the CD above the jukebox for pickup and shows an error **above the inserting player's hotbar**. The held disc is moved even in Creative mode to avoid creating a duplicate. Only the server performs the transfer/drop; the client acknowledges the interaction so it reaches the server. Occupied jukeboxes retain their normal vanilla interaction behavior. Clearing the name at an anvil restores Cat. Existing and command-renamed Mysix stacks are rechecked on insertion. Vanilla Minecraft music discs are unaffected. Each individual CD retains its own selection through the normal item save/load codec.

Implementation: `VanillaSongCatalog` resolves exact names, `CdSongSelection` updates the stack component, and `AnvilMenuMixin` runs that update after vanilla calculates the anvil result. `CdJukeboxInteraction` is registered through a common-side `UseBlockCallback` and handles revalidation, rejection/ejection, and overlay feedback. No client-only classes, custom audio files, or network protocol are needed for vanilla songs.

## Milestone 4: yt-dlp download command

With cheats/operator permission, run:

```text
/mysix download Dhyo Haw - Ada Aku Disini
/mysix download https://www.youtube.com/watch?v=dQw4w9WgXcQ
```

This is a download-only milestone: it does not connect yt-dlp to the CD or jukebox yet. Existing vanilla song selection and unknown-name ejection remain active. The server searches once (`ytsearch1:`), pins the selected YouTube video URL, and downloads audio only. A failed download gets one retry of the same video, never a replacement search. The selected title and verified file path/size are reported on completion.

Install **yt-dlp, Deno, and FFmpeg on the machine running the server**, including single-player's integrated server. No browser login or cookies are used. yt-dlp's official EJS script can be fetched from GitHub through `--remote-components ejs:github`. yt-dlp downloads the source audio, then uses FFmpeg to convert it to **mono Ogg Vorbis** with `--extract-audio --audio-format vorbis --audio-quality 5 --postprocessor-args "ExtractAudio+ffmpeg_o:-ac 1"`. YouTube does not supply native Vorbis Ogg here; this is an extra conversion step. Merely renaming a WebM or Opus file to `.ogg` is not sufficient for Minecraft playback.

Files go in `<game/server directory>/mysix-cache/downloads/<video-id>.ogg` with metadata and diagnostic logs. In this development project, the normal client/server launch directory is `run`, so inspect `run/mysix-cache/downloads/`. Dedicated-server downloads stay on that server, not on players' computers. Files are not automatically tied to a CD's lifetime or deleted yet. Rerun the download command to convert an older WebM cache entry.

`config/mysix-download.json` is created when the server starts. The default command is `["yt-dlp"]`. If it is not on the server's PATH, set `ytDlpCommand` to a JSON array containing its absolute executable path (and optional fixed arguments), then restart the server. Defaults are 90 seconds per process and 30 MiB per audio file. A single background worker and four queued jobs keep downloads off the server tick thread. Server shutdown interrupts the active process and discards queued work. The command requires gamemaster/operator permission, so other players cannot trigger downloads in this first milestone.

`YtDlpDownloader` is ordinary Java with an injectable process runner; it handles request validation, search, retries, and completed-file checks. `DownloadCommands` connects it to Fabric's command and server lifecycle events. User input is passed as arguments without a shell.

### Testing downloads

`./gradlew.bat test` runs offline tests, including simulated search/download failures, retries pinned to the same video, empty or missing output files, and configuration/request handling. Network tests are excluded from ordinary Gradle tests and disabled by default in Test Explorer.

Explicitly run `./gradlew.bat networkTest` for two real downloads. This opt-in task saves its audio to `build/integration-downloads/` and reports under `build/reports/tests/networkTest/`. It uses the same Java downloader as the command. Files existing with nonzero size establish download success, not recording-match accuracy or jukebox playback.

## Milestone 5: local playback test

All `/mysix` test commands (`download`, `play`, and `stop`) require cheat/operator permission, just like `/gamemode`. They are unavailable in normal survival with cheats disabled. Survival with cheats enabled can still use them; the restriction checks permission, not game mode.

After downloading in single-player:

```text
/mysix play
/mysix play Yikl044w8d0
/mysix stop
```

`play` without an ID chooses the most recently modified completed local Ogg file. An ID selects that exact cached file. This is a **client command for local testing**, not remote server audio delivery. On a dedicated server, clients cannot read the server's disk; transferring/streaming that audio is a future milestone. The command reports missing local files instead of trying to open a server path.

`DownloadedSound` supplies its own streaming sound definition through `getOrResolve`, then overrides Fabric's `getAudioStream` hook to open the file through Minecraft's `JOrbisAudioStream`. Playback does not depend on a `sounds.json` entry or a resource-pack reload. Playback is non-positional at this stage, controlled by **Master** and **Jukebox/Note Blocks** volume. Starting another test song stops the previous one, `/mysix stop` stops the current song, and disconnecting stops it too. Decoder errors are reported to the player.

Offline tests read PCM from a bundled Vorbis fixture through the actual Minecraft decoder and check local file selection and decoder failures. The opt-in network tests also decode the two real downloaded songs and assert mono PCM output. No audio device or game window is required for those checks; audible output and real sound-engine timing still need an in-game test.

## Milestone 6: online CDs and multiplayer audio

1. Rename a Mysix CD to `Dhyo Haw - Ada Aku Disini`, another artist/title, or a YouTube video URL.
2. Insert it into an empty jukebox. The hotbar shows lookup progress; the server downloads mono Vorbis Ogg through the existing bounded worker.
3. Nearby clients receive the audio in 24 KiB packets, then play it from the jukebox position with distance attenuation up to 64 blocks. Clients use Minecraft's decoder and need no yt-dlp, FFmpeg, or Deno installation. Both server and clients need the mod; only the server needs those external tools.
4. Remove the CD or break the jukebox to stop it. Walking away or changing dimension stops that client's sound. Players entering range receive the current recording and skip elapsed PCM to approximately match the shared server timeline.

Gameplay CD insertion works in normal survival. The `/mysix` test commands continue to require cheat/operator permission. Exact vanilla names retain vanilla playback. Other names use `ytsearch1`; a successful result is not proof that YouTube returned the intended recording. Use a direct YouTube link if the result is wrong.

Each successful online CD stores its original query and resolved video ID in its own `CUSTOM_DATA` component. The pin survives saving, loading, and ejection. Changing the name invalidates the pin; a later insertion searches the new name. Reusing a pin reads the server's cached Ogg plus yt-dlp metadata without searching again, including after a restart. If the cache is missing, it downloads that same pinned video. Server cache files are shared by video ID and are retained; automatic deletion when the last CD disappears is not implemented yet.

Downloads finish before client delivery begins; this is not progressive Internet streaming. The current limits are eight active online jukeboxes, 30 MiB per recording, four chunks per player/jukebox per tick, and eight chunks total per server tick. Files stay on the server; client audio is assembled in bounded memory for the active playback session. Download failures eject exactly the inserted CD and show a hotbar error. Local audio-device/decoder errors are reported on the affected client. Unloading a jukebox's chunk or restarting the server stops its active session; eject and reinsert the CD to resume.

`OnlineJukeboxTest` exercises real jukebox item handling with mocked world/transport boundaries: two-player delivery, shared timing, range changes, failures, removal during download, chunk unloading, and shutdown. `AudioTransferTest` checks packet codecs and bounded ordered assembly. `serverTest` repeats these under Fabric's dedicated-server environment. Network tests also assemble real downloaded tracks and decode positional playback after seeking. Audible output and a live two-client session still require an in-game test.

## Development commands

Open this folder itself in VSCodium. Run `./gradlew.bat build vscode` to build and generate debugger configurations; select Minecraft Client and press F5. Run `./gradlew.bat runServer --args=nogui` for a dedicated-server smoke test. Review and accept Minecraft's EULA yourself before running a world; the project does not accept it automatically.

The main entrypoint is `src/main/java/me/simonhamour/mysix/Mysix.java`. Item registration is in `item/ModItems.java`. Names, item definitions, models, and textures are under `src/main/resources/assets/mysix`. Recipes and recipe-book unlocks are under `src/main/resources/data/mysix`.

Server/common code has no Minecraft client imports. The client source set is reserved for future audio playback.

## Headless automated tests

Run `./gradlew.bat test`. A test-only Fabric session listener initializes Minecraft registries and item components without opening a game window or starting a world.

`CdCraftingTest` decodes the shipped recipes with Minecraft's codec and exercises its actual crafting matcher and assembly: all 16 output colors, output count and stack limit, all incorrect dye colors, every missing/replaced ingredient, rejection of the 2x2 grid, and advancement decoding with the recipe registry populated. It also verifies jukebox acceptance, resolves each CD to Cat, and exercises vanilla song-player start/stop state and events against a minimal world-interface test double. Anvil tests execute the actual `AnvilMenu` with a mocked player boundary, covering renamed output, repeated renames, invalid names, clearing names, vanilla disc isolation, and item serialization. `VanillaSongCatalogTest` covers all 22 recordings plus spelling/version rejection and normalization. These do not verify audible playback, rendering, a live world's insertion/ejection, or a live multiplayer session.

Open `build/reports/tests/test/index.html` for the report.

In VSCodium, the Java Test Explorer and **Terminal > Run Task > Mysix: Run JUnit tests** are both supported. After changing test dependencies, run **Java: Update Project Configuration** from the Command Palette so the IDE removes the upstream `fabric-loader-junit` listener from its classpath.

Fabric Loader 0.19.5's upstream JUnit listener constructs a new process-wide launcher for every JUnit listener instance. The Eclipse/JDT runner creates multiple instances, resulting in `Duplicate setLauncher call!`. `ReusableFabricSessionListener` replaces that listener only on the test classpath, initializes Fabric once, and restores thread context classloaders as sessions open and close. `IdeLauncherTest` checks repeated discovery sessions.

The actual installed Eclipse Test Explorer runner can also be verified headlessly on this Windows development machine:

```powershell
.\gradlew.bat -I ide-runner-check.init.gradle writeIdeTestClasspath
python verify_ide_runner.py
```

This checks the crafting suite through the installed editor runner, including its result protocol. Reports are under `build/ide-runner-check/`. The helper expects VSCodium extensions under `.vscode-oss/extensions` and `JAVA_HOME` to point at JDK 25. It is a development helper, not part of the mod.
