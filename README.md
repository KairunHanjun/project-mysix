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
- The server needs **yt-dlp, FFmpeg/ffprobe, and Deno** on its PATH. For singleplayer, install these on your own computer.

Download the regular JAR; the `-sources.jar` is for developers. No songs are bundled with the mod.

## Current limits

This is an **early prerelease**. External songs finish downloading before playback; first playback can take time. The limit is **eight active online jukeboxes** and **30 MiB per recording**. Cache files are retained. After a chunk unload or server restart, eject and reinsert the CD to resume. Live multiplayer testing is still pending.

## Backpack compatibility (0.2.0)

Install **Traveler's Backpack Fabric 26.3-11.4.1** and its **Forge Config API Port** dependency on client and server. Equip the backpack, add a Jukebox Upgrade, insert a renamed Mysix CD, and use the upgrade's play/stop controls. Stopping, removing the CD or upgrade, or taking off the backpack ends playback. The CD keeps its resolved recording when stored or moved. On download failure, playback resets and the CD stays in the upgrade slot.

Mysix also runs without Backpack installed. Only Backpack **11.4.1** is currently targeted; other versions leave this adapter disabled. Automated checks exercise the actual mod JAR and dedicated-server mode; the complete backpack GUI flow still needs an in-game check. See [third-party notices](THIRD_PARTY_NOTICES.md).

For a development launch with Backpack and its dependency included, run `.\gradlew.bat runClient -PwithBackpack`.

## What's next

1. Validate playback and synchronization with multiple players on a dedicated server.
2. Improve first-playback speed and progress feedback.
3. Add cache cleanup that accounts for CDs being moved, stored, or destroyed.
4. Explore compatibility with **Additional Additions' Pocket Jukebox and Albums** and **Sculk Radio**, where matching versions are available.

Those integrations remain planned. External-audio playback currently supports vanilla jukebox blocks and the optional Backpack upgrade above.
