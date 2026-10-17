# Project Mysix 0.1.0

First public prerelease for Minecraft Java 26.3 and Fabric. Requires Java 25,
Fabric Loader 0.19.5 or newer, and Fabric API for Minecraft 26.3.

## Features

- Sixteen craftable colored CDs: seven glass panes, one redstone, and one dye.
- Rename CDs in an anvil to select vanilla music or search YouTube by artist/title.
- Direct YouTube video links select a specific recording.
- Server-side yt-dlp downloads produce mono Vorbis Ogg audio.
- Nearby clients receive positional jukebox playback with a shared timeline.
- CDs remember resolved recordings and reuse the server cache.
- Download failures eject the CD and display an error above the hotbar.
- Cheat/operator-only `/mysix download`, `/mysix play`, and `/mysix stop` test commands.

## Installation

Install `project-mysix-0.1.0.jar` and Fabric API on the client and server.
The `-sources.jar` is for developers and does not go in the mods folder.
Install yt-dlp, FFmpeg (including ffprobe), and Deno on the server's PATH.
For singleplayer, install those tools on the same computer as Minecraft.
No downloaded songs are included in the release.

## Current limits

- Eight active online jukeboxes; maximum recording size is 30 MiB.
- The recording finishes downloading before client delivery and playback.
- Search can select a different recording; use a direct link when that happens.
- Chunk unloading/server restart stops sessions; eject and reinsert to resume.
- Cache files are retained; automatic cleanup when CDs disappear is not implemented.
- Backpack players and other mods' jukeboxes are not integrated yet.
- Live two-player playback still needs an in-game test. Automated tests cover
  jukebox lifecycle, packet transfer, dedicated-server mode, and real-song decoding.

See README.md for configuration and development instructions.
