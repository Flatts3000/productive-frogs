# Dev Setup

How to get a productive playtest loop running locally.

## Required

- JDK 21 (set as `JAVA_HOME` or via Gradle toolchain auto-download)
- Gradle wrapper (shipped - `./gradlew` / `.\gradlew.bat`)
- IntelliJ IDEA recommended; VSCode + Eclipse work too

## First run

```
./gradlew runClient
```

First boot downloads NeoForge + vanilla Minecraft assets - takes a few minutes. Subsequent runs are fast.

## QoL mods for playtesting

These aren't dependencies of the mod itself, but they make in-game testing much faster.

### JEI - auto-installed via Gradle

[JEI (Just Enough Items)](https://www.curseforge.com/minecraft/mc-mods/jei) is wired as a `runtimeOnly` dependency in `build.gradle`, so it loads automatically on every `runClient`. Use it to:

- See every registered item in the right-hand sidebar (identifies our items by display name).
- Search by name or mod (filter by `@productivefrogs` to see only our content).
- View recipes by clicking an item (smelting recipes are auto-discovered). The mod also ships a JEI plugin (`client/jei/ProductiveFrogsJeiPlugin`) that subtypes variant/category items and adds Information pages, so each variant shows as a distinct entry.

Pinned version: `mezz.jei:jei-1.21.1-neoforge:19.27.0.340` (1.21.1 line), with a matching `api` artifact `compileOnly` for the plugin. Bump in `build.gradle` if a newer 1.21.1-compatible build is needed.

### Jade - drop-in install (manual)

[Jade](https://www.curseforge.com/minecraft/mc-mods/jade) (the NeoForge fork of WAILA) shows an overlay at the top of the screen telling you what entity/block you're looking at. Indispensable for identifying Resource Slimes by category in-world.

Jade is a `compileOnly` API dependency in `build.gradle` (Modrinth maven), which is what our `@WailaPlugin` (`client/jade/ProductiveFrogsJadePlugin`) compiles against. It is *not* a runtime dependency: adding it `runtimeOnly` would double-load against the drop-in below and trip NeoForge's duplicate-modid check. So Jade still needs a manual run/mods drop-in to actually run in the dev client. Install once into the dev environment:

1. Download the 1.21.1 NeoForge build from [the CurseForge page](https://www.curseforge.com/minecraft/mc-mods/jade). (The 1.21.x line ships a separate jar per minor version - pick the 1.21.1 file specifically, not 1.21.11.)
2. Drop the jar into `run/mods/` (create the directory if it doesn't exist).
3. `./gradlew runClient` - Jade loads alongside JEI and Productive Frogs.

Steps 1-2 are one-time setup; the jar stays in `run/mods/` across runs.

## Smoke-testing the cross-mod crush recipes (pre-release)

The v1.3 crush recipes (`data/productivefrogs/recipe/<modid>/`) are `mod_loaded`-gated, so they are inert - and untestable - unless an actual crusher mod is present. CI can't install Mekanism / Immersive Engineering / EnderIO (heavy, version-churning, and it would cut against the no-hard-mod-dependency rule), so this is a **manual `runClient` pass before each release** - the same posture as the client-tint work that GameTest is blind to. `CrushRecipeTest` already pins the JSON shape; this confirms the recipes actually *load and run* with the mods present.

These mods are **not** dependencies of Productive Frogs - they are drop-ins for the dev run, exactly like Jade above.

### 1. Fetch the crusher mods

```
python scripts/fetch_dev_mods.py
```

Queries Modrinth for the latest 1.21.1 / NeoForge build of Mekanism, Immersive Engineering, EnderIO, and AllTheOres (the dust + smelt-back layer), verifies each SHA-1, and drops them into `run/mods/`. Re-runnable (skips files already present). Manual alternative: download the 1.21.1 NeoForge jar for each from its CurseForge/Modrinth page into `run/mods/`. If EnderIO logs a missing-library error at launch, grab its companion lib from the same page (Modrinth currently reports no required deps for the 8.x build).

### 2. Launch and verify

```
.\gradlew runClient
```

Give yourself a variant-stamped Froglight (component syntax - the value is the variant id):

```
/give @s productivefrogs:configurable_froglight[productivefrogs:slime_variant="productivefrogs:iron"]
/give @s productivefrogs:configurable_froglight[productivefrogs:slime_variant="productivefrogs:tin"]
```

Then walk the checklist:

| # | Check |
|---|---|
| 1 | **No datapack error in `run/logs/latest.log`** - with the mods present the recipes now resolve instead of being gated out. A parse error here is the headline failure. |
| 2 | **Mekanism first** (the one runtime-unverified path): crush an iron Froglight in an Enrichment Chamber -> **2 dust** -> smelt -> **2 ingots**. Mekanism never uses a `neoforge:components` ingredient in its own datagen, so this confirms its loader accepts the nested type. If it chokes, switch the recipe `type` to `mekanism:crushing` in `scripts/generate_crush_recipes.ps1` and regenerate. |
| 3 | **EnderIO**: crush iron in a SAG Mill; confirm `"bonus": "none"` yields a flat 2x with no grinding-ball RNG. |
| 4 | **Immersive Engineering**: crush iron in a Crusher; **re-verify the grit set** - confirm IE actually ships `dust_<metal>` for the metals the generator routes natively (the per-mod map in the generator is the documented research baseline, not runtime-verified). |
| 5 | **ATO fallback spot-check**: with IE **and** AllTheOres present, crush a `tin` Froglight in the IE Crusher and confirm it yields `alltheores:tin_dust` (IE has no native tin grit). |

Record the results in the release PR description. To return to vanilla behavior, just delete the jars from `run/mods/`.

## Recommended workflow

1. **Make a change** in Java code or assets.
2. **`./gradlew test`** - runs the JUnit suite (registry-loaded tests via moddev's unitTest integration). ~10s, catches registration regressions.
3. **`./gradlew runGameTestServer`** - runs in-world GameTests headless. ~15s, catches behavior regressions.
4. **`./gradlew runClient`** - full client for visual / interaction playtest.

`./gradlew build` runs `compileJava` + the JUnit suite + assembles the jar. It does NOT run the in-world GameTests - those live in `runGameTestServer`. CI runs both as separate required status checks (`build` and `gameTest`) on every PR targeting `main`.

## Other Gradle tasks

- **`./gradlew runServer`** - dedicated server for testing multi-client scenarios.
- **`./gradlew runClientData`** / **`./gradlew runServerData`** - regenerate datagen output into `src/generated/resources`. the moddev 2.0.x plugin split the old `data` task into client- and server-side variants; use the matching variant for the asset/data you're regenerating.
- **`./gradlew clean`** - wipe `build/`; useful when assets drift.

`build/` and `run/` are git-ignored.

## Where things live

- `src/main/java/com/flatts/productivefrogs/` - mod code
- `src/main/resources/` - assets, data packs, lang entries
- `src/test/java/` - JUnit tests
- `src/main/java/com/flatts/productivefrogs/gametest/` - in-world GameTests
- `docs/` - design docs (read these before non-trivial design changes)
