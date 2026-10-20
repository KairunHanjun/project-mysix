# Optional integrations

This guide covers **Fabric integrations**. For the separate Forge port, see its [compatibility notes](FORGE.md#optional-mods).

Install optional mods separately on the server and clients, with their required dependencies. Mysix targets the versions below; unsupported versions leave the relevant adapter disabled. Development commands assume the repository root as the working directory.

## Traveler's Backpack

Install **Traveler's Backpack Fabric 26.3-11.4.1** and its **Forge Config API Port** dependency on client and server. Equip the backpack, add a Jukebox Upgrade, insert a renamed Mysix CD, and use the upgrade's play/stop controls. Stopping, removing the CD or upgrade, or taking off the backpack ends playback. The CD keeps its resolved recording when stored or moved. On download failure, playback resets and the CD stays in the upgrade slot.

Mysix also runs without Backpack installed. Only Backpack **11.4.1** is currently targeted; other versions leave this adapter disabled. Automated checks exercise the actual mod JAR and dedicated-server mode; the complete backpack GUI flow still needs an in-game check. See [third-party notices](../THIRD_PARTY_NOTICES.md).

For a development launch with Backpack and its dependency included, run `.\gradlew.bat runClient -PwithBackpack`.

## Additional Additions

Install **Additional Additions Fabric 10.0.14 for 26.3** on client and server. Put renamed Mysix CDs in an Album, then insert it into a jukebox or Pocket Jukebox. A single Mysix CD also works in the Pocket Jukebox. Albums play tracks in their stored order; pocket playback is audible only to its holder. Removing the media stops playback. Resolved recordings stay on the nested CDs and reuse the server cache.

On failure, a placed jukebox ejects the whole album; a Pocket Jukebox stops and keeps its contents. Reinsert to restart. External playlist tracks need a known duration. Other Additional Additions versions leave the adapter disabled. Real-mod client/server tests pass, and the project owner confirmed Creative Pocket Jukebox playback in game. The complete mixed-album UI flow still needs an in-game check.

Development launch: `.\gradlew.bat runClient -PwithAdditions`. Add `-PwithBackpack` to include both mods. Neither optional mod is bundled with Mysix.

## Sculk Radio

Install **Sculk Radio 1.0.0+26.3** and **Fabric Language Kotlin** alongside Mysix on server and clients. Build Sculk Radio's radio (sculk shrieker above a jukebox) and speaker (sculk sensor above a note block), connected directly or through its relays/antennas. Insert an external Mysix CD. The radio sends music to the network instead of playing at the source jukebox.

Each player hears the nearest connected, loaded speaker. Speaker changes and late arrivals keep the song's timeline; disconnecting the speaker or ejecting the CD stops its sound. Range follows Sculk Radio's setting, capped at Mysix's 64 blocks. The server downloads each recording once and shares its existing audio data. Simple Voice Chat and AudioPlayer are not required for Mysix playback.

This adapter covers external audio routing; it does not synthesize jukebox redstone output or implement voice/microphone features. Unsupported Sculk Radio versions disable the adapter. Kotlin is needed by Sculk Radio's stream graph. In-game audio and live multiplayer checks remain pending.

Development launch: `.\gradlew.bat runClient -PwithSculkRadio`. Combine with the other compatibility flags as needed.

## Visual Jukebox

Install **Visual Jukebox 1.2.0+26.3** and **Polymer 0.18.4+26.3** alongside Mysix. The actual colored CD appears on the jukebox; animation follows external playback, waits during download, and stops when the song ends. Ejection removes the display. Both static insertion/ejection and spinning-disc modes are supported without starting extra vanilla audio.

Development launch: `.\gradlew.bat runClient -PwithVisualJukebox`. Add `-PwithSculkRadio` to test radio routing alongside the display. Visual Jukebox defaults to static mode; for spinning discs, set `"static": false` in `run/config/visualjukebox.json` after the first launch and restart. Mysix's hook targets this exact Visual Jukebox release. Real-mod client/server tests cover display state and ejection; an in-game visual check remains pending.

