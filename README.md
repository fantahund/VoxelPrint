# VoxelPrint

**Take the thing you built in Minecraft and print it.**

A Fabric mod for Minecraft **26.3** that reads a region of the world — the real
baked models, not a bounding box per block — and writes it to a `.mcprint`
project file. The [web platform](https://github.com/fantahund/VoxelPrint-Web)
turns that file into a multi-colour 3MF or an STL.

Runs entirely on the client. No server plugin, no permissions: it works on any
server you can walk around on.

```
/vp pos1          mark one corner
/vp pos2          mark the other
/vp export house  write run/voxelprint/exports/house.mcprint
```

---

## What it actually exports

Most exporters take the block grid and give every block a cube. This one reads
what the game draws.

A torch comes out as a stick with a lit head, because `ModelCapture` walks the
baked model and `QuadSplitter` cuts each face along its own texture — so the
head is a different material from the shaft. Grass comes out green, and the
green of *your* biome, because the tint is read at the block's own position.

A bell comes out as a bell. Its block model is only the frame; the bell itself
is drawn by a block entity renderer, out of an entity model, and
`BlockEntityCapture` reads that too.

Nothing but derived geometry and averaged colours leaves the game. No texture
images, no resource pack contents, no mod jars.

### The `.mcprint` file

A ZIP holding:

| Entry | What is in it |
|---|---|
| `manifest.json` | size, origin, dimension, versions |
| `structure.schem` | Sponge Schematic V3, gzipped NBT (or `structure.json`) |
| `block-summary.json` | a count per block state |
| `shapes.json` | each palette entry's solid shape, as boxes |
| `models.json` | each palette entry's real model: faces, material, colour |

Index order throughout: `x + z * width + y * width * depth`.

---

## Commands

All under `/voxelprint` or `/vp`.

| | |
|---|---|
| `pos1` · `pos2` | corners at your feet |
| `hpos1` · `hpos2` | corners at the block you are looking at (up to 128 blocks) |
| `status` · `clear` | what is selected, and drop it |
| `export <name>` | write the project file |
| `exports` | list what you have exported |
| `config` | open the settings screen |

The selection is drawn as a particle outline while you work.

Names are checked against `[A-Za-z0-9_-]{1,64}` and the names Windows reserves;
an existing file is never overwritten, and a selection too large to hold is
refused rather than attempted.

---

## Building

Java 25 and a JDK on `JAVA_HOME`:

```bash
./gradlew build          # jar in build/libs
./gradlew runClient      # a dev client, exports land in run/voxelprint/exports
./gradlew test           # 54 tests
```

Minecraft 26.3 ships deobfuscated, so there is no `mappings` block and
dependencies are plain `implementation(...)` rather than `modImplementation`.

Configuration goes through **VoxelConfig**, shaded into the jar. **Mod Menu** is
supported but never required.

---

## How it is put together

`de.tobi.voxelprint`

| Package | |
|---|---|
| `selection/` | two corners, one `AtomicReference`, a validator and a particle outline |
| `command/` | argument checking and feedback, and nothing else |
| `export/` | `SnapshotCapture` is the only class that reads the world; everything after it works on the frozen snapshot, which is what lets the file be written off the client thread |
| `format/` | manifest, block summary, shapes, models |
| `schematic/` | Sponge V3 and an internal JSON writer behind one interface |

---

## Licence

MIT. See [LICENSE](LICENSE).

Built by [fantahund](https://github.com/fantahund), who also maintains VoxelMap
and Durability Viewer.
