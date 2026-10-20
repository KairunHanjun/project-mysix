# Project Mysix Forge 0.6.0

Initial Forge release for **Minecraft 26.3**, **Minecraft Forge 66.0.13**, and **Java 25**. This is a prerelease. Forge and NeoForge are separate loaders.

Install `project-mysix-forge-0.6.0.jar` on the Forge server and every Forge client. The sources JAR is for developers. Fabric API is not needed. Run `/mysix setup` once to install the managed audio tools, then craft a colored CD, rename it to an artist/title or YouTube link, and insert it into a jukebox.

Includes the sixteen craftable CDs, vanilla song selection, YouTube search and direct links, recording persistence, cached audio, failure ejection, and multiplayer playback.

Optional integrations:

- **Jukebox Playlists 1.0.1 for Forge 26.3:** mixed vanilla/Mysix chest playlists, loading holds, actual-duration transitions, skipping and repeating.
- **Sound Physics Remastered Forge 1.5.1+26.3:** uses Minecraft's normal positional audio channels. On clients wanting record effects, enable `update_moving_sounds=true` in `config/sound_physics_remastered/soundphysics.properties`, then restart Minecraft.

These optional mods are installed separately. The existing Traveler's Backpack, Additional Additions, Sculk Radio, and Visual Jukebox adapters remain Fabric-only.

Validation includes 61 JUnit tests, three baseline Forge GameTests, six playlist GameTests with both optional mods loaded, windowless client mixin/decoder checks, and Fabric playback regression tests. Sound effects depend on client settings, and YouTube availability can vary by server connection.

This release uses the MIT license. Existing Fabric releases retain their original licenses and files.

Forge uses its own `forge-v0.6.0` tag. The existing Fabric `v0.6.0` release is unchanged. Future Forge bug fixes can use `forge-v0.6.1`, `forge-v0.6.2`, etc. without changing Fabric's version.
