# Optional integrations

## Managed external tools

`/mysix setup` downloads official upstream artifacts separately; these programs
are not bundled in Mysix's JAR. `mysix-tools.json` records pinned releases, URLs,
and SHA-256 hashes. They execute as separate processes using their own licenses:

- yt-dlp 2026.08.19: https://github.com/yt-dlp/yt-dlp (see upstream licensing for
  source and standalone binaries).
- Deno 2.9.7: https://github.com/denoland/deno (MIT and third-party notices).
- FFmpeg/ffprobe b6.1.1 distribution: https://github.com/eugeneware/ffmpeg-static
  and https://ffmpeg.org. The distribution's per-platform LICENSE and README
  are installed alongside the binaries, including its source/build references.
- BgUtils POT Provider 2.0.2: https://github.com/Brainicism/bgutil-ytdlp-pot-provider
  (GPL-3.0-only). Source, LICENSE, and lockfiles are retained in its installed
  directory. Its dependencies are fetched by Deno under their upstream licenses.

No endorsement or affiliation with these projects is implied.

## Optional mod adapters

Traveler's Backpack is developed by Tiviacz1337 and contributors and is published
under LGPL-3.0-only: https://modrinth.com/mod/travelersbackpack

Mysix's independently written adapter targets Traveler's Backpack Fabric
26.3-11.4.1. Install Backpack separately with its required dependencies.
Backpack's code, assets, and JAR are not included in Mysix's distributable JAR.
The official Backpack and Forge Config API Port artifacts are downloaded only
for the optional integration-test tasks.

This integration is not an endorsement by or affiliation with Backpack's authors.

Additional Additions is developed by Dqu1J, Additional Mods, and contributors and
is published under LGPL-3.0: https://modrinth.com/mod/addadd
Source: https://github.com/Additional-Mods/additionaladditions

Mysix's independently written adapter targets Additional Additions Fabric
10.0.14 for Minecraft 26.3. Its official JAR is downloaded for optional tests or
an explicitly enabled development launch. Additional Additions code, assets,
and JAR are not included in Mysix's distributable JAR. Install it separately.
This integration is not an endorsement by or affiliation with its authors.

Sculk Radio is developed by Gjorgdy: https://modrinth.com/mod/sculk-radio
Source: https://github.com/gjorgdy/Sculk-Radio
Its source LICENSE.txt and Modrinth metadata specify Apache-2.0; its released
fabric.mod.json labels the mod All-Rights-Reserved. Mysix uses an independently
written optional adapter and does not include Sculk Radio code, assets, or JAR.
Install Sculk Radio 1.0.0+26.3 and Fabric Language Kotlin separately. Their official
artifacts are used only for optional tests or an explicitly enabled development
launch. This integration is not an endorsement by or affiliation with its author.

Visual Jukebox is developed by tomalbrc and contributors and published under
LGPL-3.0: https://github.com/tomalbrc/VisualJukebox
Polymer is developed by Patbox and contributors: https://github.com/Patbox/polymer
Mysix's independently written hooks target Visual Jukebox 1.2.0+26.3, tested with
Polymer 0.18.4+26.3. Install these mods separately. Their code, assets, and JARs
are not included in Mysix's distributable JAR; official artifacts are used only
for optional tests and explicitly enabled development launches. This integration
is not an endorsement by or affiliation with their authors.
