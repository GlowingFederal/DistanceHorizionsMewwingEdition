# Distant Horizons: Java 8 Edition

## Overview

**Distant Horizons: Java 8 Edition** is an unofficial native Java 8 / LWJGL 2 port of Distant Horizons for Minecraft Forge 1.7.10. It builds on the [official Distant Horizons development code][dh], [DH core][core], and the [GTNewHorizons Minecraft 1.7.10 backport][backport].

This distribution makes DH usable in Minecraft 1.7.10's original Java 8 / LWJGL 2 environment without LWJGL3ify. Distant Horizons supplies the distant-terrain LOD system; this fork supplies a standalone native legacy-runtime distribution and the fixes described below. It is **not an official DH release** and is not endorsed by the Distant Horizons team.

### Versions and artifacts

The upstream DH baseline and this port's version are tracked independently:

| Component | Initial release |
| --- | --- |
| Upstream DH baseline | `3.3.5-dev` (development snapshot, not a claim of a final upstream 3.3.5 release) |
| Native Java 8 port version | `1.0.0` |
| Reported mod version | `3.3.5-dev-java8-native.1.0.0` |
| Release channel | **Beta**, recorded separately from the version and filename |
| Minecraft target | Forge `1.7.10` |

The production artifact is:

```text
DistantHorizons-3.3.5-dev-java8-native-1.0.0-1.7.10.jar
```

The naming scheme is `DistantHorizons-<DH version>-java8-native-<port version>-<Minecraft version>.jar`. Port releases can advance independently of the DH baseline. Alpha/beta/stable labels do not automatically enter JAR names. The upstream `-dev` suffix identifies the actual source baseline; a separate development artifact ends in `-dev.jar` and must not be installed as the production mod.

## Differences from Upstream

The original DH project and 1.7.10 backport provide the LOD renderer, generation, networking, persistence, version integration, and existing graphics abstraction. This distribution integrates that work and adds or carries these changes relative to its imported baseline:

- **Native Java 8 runtime compatibility and standalone Forge 1.7.10 packaging.** Production classes, including bundled libraries, are checked for Java 8 bytecode compatibility. Incompatible multi-release library entries are excluded from the legacy package.
- **Direct LWJGL 2 backend selection.** The Forge 1.7.10 runtime selects the existing LWJGL 2 implementation directly. LWJGL3ify, LWJGL 3, GLFW, and RetroFuturaBootstrap are not runtime requirements. Build-time abstraction generation still references LWJGL 3 APIs for other targets.
- **Mandatory UniMixins integration with optional Angelica support.** Native operation does not depend on Angelica. Its existing DH integration is retained, with a correction for generic shader setup on LWJGL 2.
- **Generation scheduling corrections.** Candidate sections refresh after available queue slots fill, so newly loaded nearby LOD sections are not starved behind a distant generation backlog.
- **Coverage-edge geometry corrections.** Unknown neighboring LOD data is distinguished from confirmed air, preventing artificial terrain cliffs while preserving real cliff and island sides.
- **Framebuffer depth attachment corrections.** Native depth textures are attached after vanilla framebuffer setup completes, addressing water transition seams and tracking attachment ownership through recreation and deletion.
- **Decorative vegetation color corrections.** Ignored flowers and similar decorations tint a bounded surface layer instead of repainting an entire terrain column. Coarse simplification preserves the terrain color where a separate surface layer cannot be retained.
- **Refinement metadata corrections.** Coarse fallback data does not relabel retained detailed columns as coarse, and compression metadata is taken from the correct sampled column.
- **Additional regression coverage and compatibility validation.** Tests cover terrain boundaries, vegetation tinting, refinement metadata, and network buffer ownership. Production JARs have also been exercised in isolated Forge modpack environments.

See [CHANGELOG.md](CHANGELOG.md) for this fork's changes. These additions do not imply original authorship of upstream DH functionality.

## Requirements

- **Minecraft Java Edition 1.7.10** and **Minecraft Forge 10.13.4.1614**.
- **Java 8** for Minecraft. A newer JDK is used to build the project; it is not required to play.
- Minecraft's original **LWJGL 2.9.1** runtime libraries.
- **[UniMixins][unimixins] 0.2.1 or newer.** The declared minimum is 0.2.1; versions 0.2.1 and 0.3.1 were tested. This does not establish compatibility with every intervening or future version. Keep its original leading `+` in the filename for bootstrap ordering.
- On clients, a graphics driver supporting **OpenGL 3.3 or newer**. Dedicated servers do not need a graphics context.

**[Angelica][angelica] is optional.** The integration enforces a minimum version of 2.1.5; the tested versions are listed below. Install the dependencies required by your chosen Angelica version, including [GTNHLib][gtnhlib] where required. Shader packs must support DH through the Iris shader interface.

**LWJGL3ify is not required.** Use a normal Forge Java 8 installation; no LWJGL 3 or RetroFuturaBootstrap setup is needed.

## Installation

1. Back up your world, existing Distant Horizons configuration, and DH databases before switching builds.
2. Install Forge 1.7.10 and select Java 8 for the instance.
3. Place the **production JAR** from this fork's [releases][releases] and UniMixins in the instance's `mods` folder. Do not install a JAR ending in `-dev.jar`, `-sources.jar`, or `-all.jar`.
4. If wanted, install Angelica and its required dependencies, then launch Minecraft.

Install **only one Distant Horizons JAR**. Remove another DH distribution before adding this one. The original `distanthorizons` mod ID, configuration namespaces, network protocol identifiers, and database format identifiers are retained. Dedicated servers use the same production JAR and UniMixins.

For Java 8 Edition updates, use [this fork's releases][releases]. The inherited DH update checker still queries upstream; it does not track this distribution's independent port releases.

## Compatibility

The following environments were exercised with production builds during development. This is bounded compatibility evidence, not a guarantee for every modpack or every version of these mods.

| Environment | Observed coverage |
| --- | --- |
| Windows, Java 8, LWJGL 2.9.1, Forge 10.13.4.1614 | Native startup, existing and fresh singleplayer worlds, generation, and rendering without Angelica |
| Java 8 dedicated Forge server | Client connection, LOD generation/download, saved database reload, and reconnection |
| UniMixins | Minimal setup with 0.2.1; production modpack with 0.3.1 |
| Angelica 2.2.12 / GTNHLib 0.11.47 | Shaders disabled and Complementary Unbound 5.8.1 enabled, through native LWJGL 2 |
| Angelica 2.1.29 / GTNHLib 0.11.4 | Production modpack with shaders enabled and disabled |
| Universal Adaptive Backporter and other production-pack mods | Integrated-world rendering, sampled vanilla/UAB vegetation and terrain colors, persistence, and configuration interaction |

The graphics observations are from a Windows / AMD Radeon RX 7800 XT setup. These checks do not establish exhaustive survival gameplay coverage or performance benchmarks. Other Minecraft targets present in the source tree are not qualified by this distribution's 1.7.10 tests.

## Known Limitations

- Distant geometry may jitter or Z-fight in particular viewing conditions; coverage of these conditions remains limited.
- Leaves, flowers, and other vegetation are approximated at coarse LOD levels. Individual leaf cutouts and small decorative shapes are not preserved in simplified geometry.
- Initial structure geometry can be coarse or incomplete and improve as coverage loads and LOD data is refined. This alone does not establish a structure-rendering defect.
- Initial generation can take substantial time at large LOD distances; unavailable coverage remains missing until generated or received from a DH server.
- GPU-vendor and operating-system coverage is limited. Other drivers and operating systems remain unqualified.
- Long-duration memory stability and performance validation are incomplete. The beta should not be treated as a fully qualified stable release.

## Building and Contributing

Use **JDK 25** for the pinned Gradle wrapper and provide a **Java 8 compiler toolchain**. Forge/LWJGL sources compile with Java 8, while the platform-neutral core/API target Java 8 bytecode. Gradle's test JVM is separate from the Minecraft runtime.

From the repository root:

```sh
./gradlew clean check assemble
```

On Windows, use `gradlew.bat`. The root wrapper selects Minecraft 1.7.10, runs the core/API regression suites and Forge release verifier, and collects the production and development JARs into `build/libs`. The remapped production JAR also appears in `dh/forge17/build/libs` under the same production filename. Intermediate loader JARs are build inputs, not release downloads.

`dh/gradle.properties` owns `mod_version` (the upstream baseline), `java8_port_version` (this port), and `java8_release_channel` (release metadata only). Change the appropriate component for subsequent releases. Forge metadata, the in-game version display, manifest attributes, and root publication version derive from these properties. No Git tag or working-tree suffix is appended to the production filename.

The root Gradle project handles collection/publication. `dh/` contains the imported DH tree, and `dh/coreSubProjects/` contains the imported core/API tree. Preserve these subtree boundaries when preparing upstream contributions. Keep fixes focused, retain existing compatibility contracts, and include relevant regression coverage.

## Credits and Licensing

- **[Distant Horizons developers][dh]** and **[DH core contributors][core]**, including James Seibel and the authors credited in the source and mod metadata, provide the underlying LOD system.
- **[GTNewHorizons DistantHorizons contributors][backport]**, including DarkShadow44 and Fabian, provide the Minecraft 1.7.10 backport and wrapper on which this distribution is based.
- **[This fork's contributors][fork]** maintain the native Java 8 distribution and the additional compatibility/correctness changes described above.
- Distant Horizons logos are credited to **Pankakes** under **CC BY-SA 4.0**, as specified in [LICENSE-LOGOS.txt](dh/LICENSE-LOGOS.txt).

This is an **unofficial fork**. Existing copyright and license notices remain in place. The DH code is licensed under the **GNU Lesser General Public License v3**; see [LICENSE.LESSER.txt](dh/LICENSE.LESSER.txt), the accompanying [LICENSE.txt](dh/LICENSE.txt), and the corresponding [core licenses](dh/coreSubProjects/LICENSE.LESSER.txt). Bundled third-party components retain their own notices and licenses. This distribution does not replace those terms or claim independent authorship of the full codebase.

[dh]: https://gitlab.com/distant-horizons-team/distant-horizons
[core]: https://gitlab.com/jeseibel/distant-horizons-core
[backport]: https://github.com/GTNewHorizons/DistantHorizons
[fork]: https://github.com/GlowingFederal/DistanceHorizionsMewwingEdition
[releases]: https://github.com/GlowingFederal/DistanceHorizionsMewwingEdition/releases
[unimixins]: https://github.com/LegacyModdingMC/UniMixins
[angelica]: https://github.com/GTNewHorizons/Angelica
[gtnhlib]: https://github.com/GTNewHorizons/GTNHLib
