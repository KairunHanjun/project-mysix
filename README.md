# Project Mysix

**Craft a CD, rename it, and play your music in Minecraft.**

A Fabric mod for **Minecraft Java 26.3** with vanilla music, YouTube song search, and multiplayer jukebox playback.

[Download the prerelease](https://github.com/KairunHanjun/project-mysix/releases) · [Report a bug](https://github.com/KairunHanjun/project-mysix/issues) · [Release notes](CHANGELOG.md)

## What it does

- Adds **16 craftable colored CDs**, one for each dye.
- Uses anvil names to select music: `C418 - Cat`, `Dhyo Haw - Ada Aku Disini`, or a **YouTube video link**.
- Plays recognized vanilla songs directly; searches YouTube for other artist/title names.
- Downloads external audio on the server and delivers positional playback to nearby players, up to **64 blocks** away.
- Saves the selected recording on each CD and reuses cached audio. Renaming the CD selects a new song.
- Ejects the CD and shows a hotbar error if downloading fails.
- Works in survival. Removing the CD or breaking the jukebox stops playback.
- Optional **Traveler's Backpack 11.4.1** support: play Mysix CDs through the equipped backpack's Jukebox Upgrade, audible only to its wearer.
- Optional **Additional Additions 10.0.14** support: Mysix CDs in Albums and the Pocket Jukebox, including mixed vanilla/external playlists.
- Optional **Sculk Radio 1.0.0+26.3** support: route external CDs through connected speakers.
- Optional **Visual Jukebox 1.2.0+26.3** support: display and animate external Mysix CDs.

## How to use

Craft one CD with **seven glass panes, one redstone, and one dye**:

| Left | Center | Right |
|---|---|---|
| Glass pane | Glass pane | Glass pane |
| Glass pane | Redstone | Glass pane |
| Glass pane | Any dye | Glass pane |

Rename it in an anvil, then insert it into an empty jukebox. An unnamed CD plays **C418 - Cat**. If search picks the wrong recording, rename the CD to a direct YouTube link.

## Requirements

- **Java 25**, **Fabric Loader 0.19.5+**, and **Fabric API for 26.3**.
- Install Mysix and Fabric API on **both the server and every client**.
- In **0.6.0**, run **`/mysix setup`** once to install and manage the server's downloader tools and anonymous token helper. Players need no external tools or Google login. Manual yt-dlp, FFmpeg/ffprobe, and Deno on PATH remain supported.

Download the regular JAR; the `-sources.jar` is for developers. No songs are bundled with the mod.

## Automatic setup (0.6.0)

On a dedicated server, run `mysix setup` in its console, or `/mysix setup` as a level-4 operator. A singleplayer world owner can run setup without enabling cheats. Mysix detects OS/CPU, downloads checksum-pinned tools into `mysix-tools/`, installs locked helper dependencies, and starts the helper on localhost. It then tries an actual anonymous audio download; installation success and YouTube access are reported separately. First setup needs internet access, disk space, and permission to run executables and may take several minutes. No system PATH changes, administrator installation, cookies, or Google sign-in are required by the setup flow.

With cheats/operator access, `/mysix check` retests retrieval and conversion; `/mysix check Artist - Title` or a YouTube link tests your choice. Normal CD use remains available in survival. Downloads and helper startup run off the Minecraft tick thread. The helper restarts when needed and shuts down with the server. `managedTools: true` in `config/mysix-download.json` selects the installed tools after restart; set it to `false` to return to your saved manual command.

Automatic bundles cover Windows x64, Linux x64/ARM64 with glibc, and macOS x64/ARM64. macOS and ARM64 bundles still need runtime validation. Alpine/musl, Windows ARM64, and other platforms require manual tools. YouTube may still reject a VPS even with the helper; setup does not guarantee access. In 0.4.0–0.5.0, tools must be installed manually.

## Current limits

This is an **early prerelease**. External songs finish downloading before playback; first playback can take time. The limit is **eight active online jukeboxes** and **30 MiB per recording**. Cache files are retained. After a chunk unload or server restart, eject and reinsert the CD to resume. Live multiplayer testing is still pending.

## Backpack compatibility (0.2.0)

Install **Traveler's Backpack Fabric 26.3-11.4.1** and its **Forge Config API Port** dependency on client and server. Equip the backpack, add a Jukebox Upgrade, insert a renamed Mysix CD, and use the upgrade's play/stop controls. Stopping, removing the CD or upgrade, or taking off the backpack ends playback. The CD keeps its resolved recording when stored or moved. On download failure, playback resets and the CD stays in the upgrade slot.

Mysix also runs without Backpack installed. Only Backpack **11.4.1** is currently targeted; other versions leave this adapter disabled. Automated checks exercise the actual mod JAR and dedicated-server mode; the complete backpack GUI flow still needs an in-game check. See [third-party notices](THIRD_PARTY_NOTICES.md).

For a development launch with Backpack and its dependency included, run `.\gradlew.bat runClient -PwithBackpack`.

## Additional Additions compatibility (0.3.0)

Install **Additional Additions Fabric 10.0.14 for 26.3** on client and server. Put renamed Mysix CDs in an Album, then insert it into a jukebox or Pocket Jukebox. A single Mysix CD also works in the Pocket Jukebox. Albums play tracks in their stored order; pocket playback is audible only to its holder. Removing the media stops playback. Resolved recordings stay on the nested CDs and reuse the server cache.

On failure, a placed jukebox ejects the whole album; a Pocket Jukebox stops and keeps its contents. Reinsert to restart. External playlist tracks need a known duration. Other Additional Additions versions leave the adapter disabled. Real-mod client/server tests pass; audible playback, the full inventory UI, and live multiplayer still need an in-game check.

Development launch: `.\gradlew.bat runClient -PwithAdditions`. Add `-PwithBackpack` to include both mods. Neither optional mod is bundled with Mysix.

## What's next

1. Validate playback and synchronization with multiple players on a dedicated server.
2. Improve first-playback speed and progress feedback.
3. Add cache cleanup that accounts for CDs being moved, stored, or destroyed.
4. Validate optional integrations together in game. Sophisticated Backpacks porting is paused.

The public 0.4.0 prerelease includes Backpack, Additional Additions, and Sculk Radio support. Visual Jukebox (0.5.0) and automatic setup (0.6.0) are currently unreleased.

## Sculk Radio compatibility (0.4.0)

Install **Sculk Radio 1.0.0+26.3** and **Fabric Language Kotlin** alongside Mysix on server and clients. Build Sculk Radio's radio (sculk shrieker above a jukebox) and speaker (sculk sensor above a note block), connected directly or through its relays/antennas. Insert an external Mysix CD. The radio sends music to the network instead of playing at the source jukebox.

Each player hears the nearest connected, loaded speaker. Speaker changes and late arrivals keep the song's timeline; disconnecting the speaker or ejecting the CD stops its sound. Range follows Sculk Radio's setting, capped at Mysix's 64 blocks. The server downloads each recording once and shares its existing audio data. Simple Voice Chat and AudioPlayer are not required for Mysix playback.

This adapter covers external audio routing; it does not synthesize jukebox redstone output or implement voice/microphone features. Unsupported Sculk Radio versions disable the adapter. Kotlin is needed by Sculk Radio's stream graph. In-game audio and live multiplayer checks remain pending.

Development launch: `.\gradlew.bat runClient -PwithSculkRadio`. Combine with the other compatibility flags as needed.

## Visual Jukebox compatibility (0.5.0)

Install **Visual Jukebox 1.2.0+26.3** and **Polymer 0.18.4+26.3** alongside Mysix. The actual colored CD appears on the jukebox; animation follows external playback, waits during download, and stops when the song ends. Ejection removes the display. Both static insertion/ejection and spinning-disc modes are supported without starting extra vanilla audio.

Development launch: `.\gradlew.bat runClient -PwithVisualJukebox`. Add `-PwithSculkRadio` to test radio routing alongside the display. Visual Jukebox defaults to static mode; for spinning discs, set `"static": false` in `run/config/visualjukebox.json` after the first launch and restart. Mysix's hook targets this exact Visual Jukebox release. Real-mod client/server tests cover display state and ejection; an in-game visual check remains pending.
