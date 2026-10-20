# Forge build

Mysix has a separate experimental port for **Minecraft 26.3**, **Minecraft Forge 66.0.13**, and **Java 25**. Forge and NeoForge are separate loaders; this build is for Forge.

## Install and use

Use the regular `project-mysix-forge-<version>.jar` on the Forge server and every Forge client. Do not install the Fabric JAR or Fabric API in that instance. The `-sources.jar` is for developers.

The Forge port includes all sixteen CDs, recipes, vanilla song selection, YouTube search/direct links, recording data, cache reuse, failure ejection, multiplayer audio delivery, and automatic tool installation. Run `/mysix setup` once, then craft and rename a CD as described in the [player guide](../README.md). In the server console, omit the command's slash. Setup permissions and [downloader configuration](SETUP.md) are the same as Fabric.

The Forge build shares the core Java sources and resources with Fabric. The `forge/` directory provides loader-specific registration, events, networking, client commands and audio hooks. Build output is in `forge/build/libs/`; GitHub's **Forge build and release** workflow uploads JARs and checksums, and publishes a separate release for `forge-v*` tags after all Forge checks pass.

Forge's version is stored in `forge/gradle.properties` as `forge_version`. Its initial `0.6.0` matches Fabric's feature baseline. Tags such as `forge-v0.6.0` and `forge-v0.6.1` belong to Forge; existing `v*` tags belong to Fabric. Bug-fix versions advance independently, so fixing Forge does not bump or replace Fabric's release. A future Forge feature baseline can align with the equivalent Fabric version deliberately.

Both loaders follow the [release versioning policy](VERSIONING.md): bug fixes use `0.6.x`, feature upgrades advance to `0.7.0` and beyond, and the first complete stable release is `1.0.0`.

## Build and test

From the repository root, with Java 25 selected:

```powershell
.\gradlew.bat -p forge build
.\gradlew.bat -p forge runGameTestServer
.\gradlew.bat -p forge -PplaylistCompat runGameTestServer
.\gradlew.bat -p forge -PplaylistCompat -PsoundPhysicsCompat runClientData
.\gradlew.bat -p forge runClient
.\gradlew.bat -p forge runServer
```

The first build downloads Forge's development dependencies and any launch-helper toolchain it needs. JUnit checks the shared downloader, installer and audio packets, plus Forge's custom audio stream hook. Headless Forge GameTests exercise item registration, all sixteen recipes, anvil rename selection, recording persistence and server commands. A game window is not required for those checks.

Before using this as a stable release, test audible playback with two real Forge clients: unnamed/vanilla CDs, YouTube searches and links, removal during downloading/playback, failure ejection, reconnects, and cache reuse after restart. Automated decoding does not by itself verify an audible multiplayer session.

## Optional mods

Release availability was checked on **20 October 2026** for **Forge + Minecraft 26.3**, not just for a project having a Forge tag on an older release:

| Existing integration | Matching Forge release found? | Port decision |
|---|---|---|
| Traveler's Backpack | No; current 26.3 releases are Fabric/NeoForge. | Omitted. |
| Additional Additions | No matching Forge release. | Omitted. |
| Sculk Radio | No matching Forge release. | Omitted. |
| Visual Jukebox | No matching Forge release. | Omitted. |

Version queries: [Traveler's Backpack](https://api.modrinth.com/v2/project/travelersbackpack/version?loaders=%5B%22forge%22%5D&game_versions=%5B%2226.3%22%5D), [Additional Additions](https://api.modrinth.com/v2/project/addadd/version?loaders=%5B%22forge%22%5D&game_versions=%5B%2226.3%22%5D), [Sculk Radio](https://api.modrinth.com/v2/project/sculk-radio/version?loaders=%5B%22forge%22%5D&game_versions=%5B%2226.3%22%5D), [Visual Jukebox](https://api.modrinth.com/v2/project/visual-jukebox/version?loaders=%5B%22forge%22%5D&game_versions=%5B%2226.3%22%5D). Traveler's Backpack's [CurseForge releases](https://www.curseforge.com/minecraft/mc-mods/travelers-backpack/files/all?version=26.3) also distinguish NeoForge from Forge.

### Jukebox Playlists

The Forge build includes an optional adapter for **[Jukebox Playlists 1.0.1 for Forge 26.3](https://github.com/EyeCrasher07/Jukebox-Playlists/releases/tag/v1.0.1)**. Install that mod separately on the server and clients alongside Mysix. Its JAR is not bundled in Mysix.

Place a single chest directly above a jukebox and put vanilla or renamed Mysix CDs into it. Upstream controls slot order, skipping and looping. Mysix starts external recordings automatically, holds the queue during downloading, and advances one second after the actual recording finishes. Names and pinned recording data survive swaps. Failed downloads eject the CD, show the usual hotbar error, and allow the next disc to play. Recordings with no usable duration are rejected rather than blocking the playlist indefinitely. Removing the chest leaves the final CD stopped after playback. Automatic external playback waits for a compatible Mysix player in the same dimension.

The optional test command above downloads a checksum-pinned upstream binary and runs six headless integration tests with actual jukebox/chest entities and upstream mixins. The downloader is replaced with deterministic offline results; these tests verify queue behavior, not YouTube availability or audible multiplayer playback. Without `-PplaylistCompat`, development runs omit the optional mod and test Mysix independently. To play-test the integration, use `-PplaylistCompat runClient` or `-PplaylistCompat runServer`.

### Sound Physics Remastered

Tested with **[Sound Physics Remastered Forge 1.5.1+26.3](https://modrinth.com/mod/sound-physics-remastered/version/9fsEYAXC)**, alongside Jukebox Playlists 1.0.1. Install Sound Physics separately on clients that want its effects. It is optional on the dedicated server. Mysix remains required on the server and every client; install Jukebox Playlists on both when using playlists.

Mysix sends placed jukebox music through Minecraft's regular positional `RECORDS` sound channels as mono audio. Sound Physics already processes those channels, so no extra processing hook is needed. Personal playback remains listener-relative. Mysix does not change Sound Physics settings or bundle its JAR.

**Enable music-disc processing:** Sound Physics defaults `update_moving_sounds` to `false`, which skips record sounds. After it has generated its configuration, close Minecraft, set this in the client's `config/sound_physics_remastered/soundphysics.properties`, then restart:

```properties
update_moving_sounds=true
```

Leave Sound Physics enabled and check that its sound-rate configuration has not excluded `mysix:download_test`. The same identifier is used for downloaded jukebox tracks. Effects and their strength follow the listener's Sound Physics settings. Its distance options do not extend Mysix's 64-block server audio delivery range.

For a combined development launch, use `-PplaylistCompat -PsoundPhysicsCompat runClient`. The `runClientData` command above uses Forge's windowless client data launcher to apply the actual client mixins from Mysix and Sound Physics, verify the hooks coexist, and decode positional mono Ogg. CI runs that check plus the playlist GameTests with both optional mods installed. These checks do not evaluate OpenAL effects or prove audible reverb.

For in-game testing, put renamed Mysix CDs and vanilla discs in a chest playlist, listen in the open and inside a stone room, then move behind a thick wall. Compare with Sound Physics disabled. Verify track transitions and disc removal still stop the old music, including on a dedicated server with two clients.

## Candidates for a later compatibility pass

These are research results, not implemented or verified integrations:

| Candidate | Forge + 26.3 release | What to investigate |
|---|---|---|
| [Current Game Music Track](https://modrinth.com/mod/current-game-music-track/version/JFSS1HV4) | `26.3.0-2.7-fabric+forge+neo` | Whether its HUD can identify online song titles instead of a generic custom sound identifier. |

Also investigated: Etched, Looping Jukebox, Redstone Jukeboxes, Music Player, Iam Music Player, and Jukebox Custom Disc Fix. Their available Forge releases did not target Minecraft 26.3 on the check date, so they are not current integration targets. A mod using standard sounds may already coexist without a dedicated adapter; testing should establish whether one is needed first.
