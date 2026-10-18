# Project Mysix

**Craft a CD, rename it, and play your music in Minecraft.**

Mysix is a Fabric mod that brings vanilla music, YouTube song search, and multiplayer jukebox playback to Minecraft Java Edition.

[Download](https://github.com/KairunHanjun/project-mysix/releases) · [Report a bug](https://github.com/KairunHanjun/project-mysix/issues) · [Changelog](CHANGELOG.md)

## What it does

- Adds **16 craftable colored CDs**, one for each dye.
- Selects music from anvil names: a vanilla song, `Artist - Title`, or a YouTube video link.
- Downloads external audio on the server and plays it for nearby players, up to **64 blocks** away.
- Remembers the selected recording on the CD and reuses cached audio. Renaming the CD selects a new song.
- Stops playback when the CD is removed or the jukebox is broken. A failed download ejects the CD and displays a hotbar message.
- Works in singleplayer and on dedicated servers, with optional portable players, albums, speakers, and jukebox displays.

## Install

1. Choose a release matching your Minecraft version. Its release notes list the required Java, Fabric Loader, and Fabric API versions.
2. Install the regular Mysix JAR and Fabric API on **the server and every client**. The `-sources.jar` is for developers.
3. Start Minecraft or the server, then run **`/mysix setup`**. In the dedicated-server console, use `mysix setup` without the slash.

Setup downloads and manages the server's required tools. Players need no external tools or Google login. It may take several minutes and reports installation and YouTube access separately. A dedicated server requires console access or a level-4 operator; a singleplayer world owner can run setup without enabling cheats.

See the [setup guide](docs/SETUP.md) for supported operating systems, manual tools, configuration, and troubleshooting. Forge and Paper are not currently supported.

## Craft and play

Craft one CD with **seven glass panes, one redstone, and one dye**:

| Left | Center | Right |
|---|---|---|
| Glass pane | Glass pane | Glass pane |
| Glass pane | Redstone | Glass pane |
| Glass pane | Any dye | Glass pane |

Rename the CD in an anvil, then insert it into an empty jukebox:

| CD name | Playback |
|---|---|
| Unnamed | C418 - Cat |
| `C418 - Cat` | The recognized vanilla recording |
| `Dhyo Haw - Ada Aku Disini` | The first YouTube search result |
| A YouTube video link | That specific video |

If search picks the wrong recording, rename the CD to a direct YouTube link. Normal CD use works in survival and needs no commands.

## Optional integrations

| Mod | What you can do |
|---|---|
| Traveler's Backpack | Play a CD through an equipped backpack's Jukebox Upgrade, audible only to its wearer. |
| Additional Additions | Put CDs in Albums or a Pocket Jukebox, including playlists mixing vanilla and external songs. Pocket playback is personal. |
| Sculk Radio | Send external jukebox music through connected speakers instead of the source jukebox. |
| Visual Jukebox | Display the actual colored CD and animate it during playback. |

These mods are optional and installed separately. Check the [compatibility guide](docs/COMPATIBILITY.md) for supported mod versions, dependencies, and usage instructions.

## Troubleshooting and limits

- Use `/mysix check Artist - Title` or `/mysix check <YouTube link>` to test server retrieval and conversion. This requires cheats/operator access; console commands omit the slash.
- First playback waits for the download. YouTube may refuse some server connections even after setup; see the [diagnostics guide](docs/SETUP.md).
- The limit is **eight active online music sessions** and **30 MiB per recording**.
- After a chunk unload or server restart, eject and reinsert the CD to resume.
- Cached audio is retained. Automatic cleanup and the planned `/mysix clear <music | entire-cache>` command are not implemented yet.

For release status, changes, and version-specific requirements, see the [releases](https://github.com/KairunHanjun/project-mysix/releases) and [changelog](CHANGELOG.md).

## License

Mysix is licensed under [MIT](LICENSE). Earlier CC0 permissions remain valid; see the [changelog](CHANGELOG.md). Third-party tools and mods retain their [own licenses](THIRD_PARTY_NOTICES.md). No songs are bundled, and the mod's license does not grant rights to downloaded music.
