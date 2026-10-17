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

## What's next

1. Validate playback and synchronization with multiple players on a dedicated server.
2. Improve first-playback speed and progress feedback.
3. Add cache cleanup that accounts for CDs being moved, stored, or destroyed.
4. Explore compatibility with **Traveler's Backpack jukebox upgrades**, **Additional Additions' Pocket Jukebox and Albums**, and **Sculk Radio**, where matching versions are available.

These integrations are planned; current external-audio playback supports vanilla jukebox blocks.
