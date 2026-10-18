# Project Mysix 0.6.0 (prerelease)

- Added `/mysix setup`: detects OS/CPU and installs checksum-pinned yt-dlp,
  Deno, FFmpeg/ffprobe, and an anonymous PO-token helper in Mysix's own folder.
- Dedicated-server setup requires console/level-4 permission; a singleplayer
  owner can install without enabling the cheat-only download/play commands.
- Helper dependencies use the upstream lockfile. The helper binds only to
  localhost, is restarted when needed, and stops with the server.
- Managed tools are selected persistently without changing system PATH;
  the previous manual command remains saved for opting out.
- Added `/mysix check [song or YouTube link]` to verify real server-side audio
  retrieval and conversion independently of client playback.
- Setup reports tool installation separately from YouTube acceptance. No account
  login/cookies are requested; YouTube may still refuse some VPS connections.
- Added installer/checksum/archive/permission tests and Windows/Linux setup CI.
- Local managed-tool audio download and Minecraft Vorbis decoding passed.
- Managed downloads now try default YouTube clients before the token-helper
  fallback, with separate route diagnostics and a shared per-request time budget.
- Search pins one video before audio retrieval; retries do not select a substitute.
- Fixed Creative Pocket Jukebox playback when inventory synchronization bypasses
  the server's normal disc insertion hook. Added inventory/cursor regression tests.
- The project owner confirmed YouTube playback on a dedicated VPS and Creative
  Pocket Jukebox playback after these fixes.
- Includes the Visual Jukebox integration described below for 0.5.0.
- macOS/ARM64 runtime validation, longer multiplayer sessions, and remaining
  in-game optional integration checks are pending; this remains a prerelease.

# Project Mysix 0.5.0 (included in 0.6.0)

- Optional Visual Jukebox 1.2.0+26.3 integration with Polymer 0.18.4+26.3.
- External Mysix CDs use their actual item/color/name in the display.
- Static and spinning modes follow Mysix playback without starting a vanilla song.
- Download preparation is stopped presentation; completion/ejection ends animation.
- Added released-mod client/server presentation tests and combined-mod hook checks.
- In-game rendering and audible multiplayer checks remain pending.

# Project Mysix 0.4.0

- Optional Sculk Radio 1.0.0+26.3 external-audio routing through its public stream API.
- Uses connected speakers, including paths through Sculk Radio relays and antennas.
- Each listener hears the nearest loaded speaker; the source radio jukebox is silent.
- Late arrival and speaker changes keep the existing playback timeline.
- Removing a speaker or ejecting the CD cancels the appropriate playback.
- Existing download/cache and bounded packet transfer are shared across the network.
- Added real-mod client/server, remote listener, speaker-switching, relay traversal,
  and combined optional-mod checks.
- Requires Fabric Language Kotlin for Sculk Radio's stream graph.
- External-song redstone output and in-game audio/multiplayer checks remain outside this adapter.

# Project Mysix 0.3.0 (unreleased)

- Optional Additional Additions Fabric 10.0.14 integration for Minecraft 26.3.
- Mysix CDs work in Albums, placed jukeboxes holding Albums, and the Pocket Jukebox.
- Mixed albums play external and vanilla tracks in stored order without skipping pending downloads.
- Pocket playback uses the server cache and is audible only to its holder.
- Resolved recording data persists inside album contents and pocket containers.
- Removing media cancels playback and ignores late download results.
- Failed placed-album tracks eject the whole album; pocket failures preserve stored media.
- External playlist tracks without a known duration are rejected.
- Added actual-mod client/server, serialization, failure, playlist, and combined Backpack integration tests.
- Audio protocol v3 requires the same Mysix version on server and every client.
- Full inventory UI, audible playback, and live multiplayer validation remain pending.

# Project Mysix 0.2.0 (unreleased)

- Optional Traveler's Backpack Fabric 26.3-11.4.1 Jukebox Upgrade integration.
- External Mysix CDs are accepted in the upgrade and use its play/stop controls.
- Server-side downloads and the existing cache deliver music only to the wearer.
- Stopping, removing the CD/upgrade/backpack, death, or logout cancels playback,
  including downloads that finish after cancellation.
- Resolved recording data is saved back into the backpack's stored CD.
- Failed downloads stop the upgrade and preserve the CD in its slot.
- Mysix runs without Backpack; unsupported Backpack versions disable the adapter.
- Added real-mod hook tests and dedicated-server compatibility checks.
- Updated audio protocol: use the same Mysix version on server and every client.
- Full backpack GUI and live multiplayer checks remain pending.

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
