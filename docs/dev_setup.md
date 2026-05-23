# Dev Setup

How to get a productive playtest loop running locally.

## Required

- JDK 21 (set as `JAVA_HOME` or via Gradle toolchain auto-download)
- Gradle wrapper (shipped — `./gradlew` / `.\gradlew.bat`)
- IntelliJ IDEA recommended; VSCode + Eclipse work too

## First run

```
./gradlew runClient
```

First boot downloads NeoForge + vanilla Minecraft assets — takes a few minutes. Subsequent runs are fast.

## QoL mods for playtesting

These aren't dependencies of the mod itself, but they make in-game testing much faster.

### JEI — auto-installed via Gradle

[JEI (Just Enough Items)](https://www.curseforge.com/minecraft/mc-mods/jei) is wired as a `runtimeOnly` dependency in `build.gradle`, so it loads automatically on every `runClient`. Use it to:

- See every registered item in the right-hand sidebar (identifies our items by display name).
- Search by name or mod (filter by `@productivefrogs` to see only our content).
- View recipes by clicking an item (when we ship smelting recipes for Froglights, they'll show here automatically — no JEI plugin code on our side).

Pinned version: `mezz.jei:jei-1.21.11-neoforge:27.4.0.22`. Bump in `build.gradle` if a newer version is needed.

### Jade — drop-in install (manual)

[Jade](https://www.curseforge.com/minecraft/mc-mods/jade) (the NeoForge fork of WAILA) shows an overlay at the top of the screen telling you what entity/block you're looking at. Indispensable for identifying Resource Slimes by category in-world.

We don't depend on Jade in `build.gradle` because it doesn't ship to a maven repo we use cleanly. Install once into the dev environment:

1. Download the 1.21.11 NeoForge build from [the CurseForge page](https://www.curseforge.com/minecraft/mc-mods/jade).
2. Drop the jar into `run/mods/` (create the directory if it doesn't exist).
3. `./gradlew runClient` — Jade loads alongside JEI and Productive Frogs.

Steps 1–2 are one-time setup; the jar stays in `run/mods/` across runs.

## Recommended workflow

1. **Make a change** in Java code or assets.
2. **`./gradlew test`** — runs the JUnit suite (registry-loaded tests via moddev's unitTest integration). ~10s, catches registration regressions.
3. **`./gradlew runGameTestServer`** — runs in-world GameTests headless. ~15s, catches behavior regressions.
4. **`./gradlew runClient`** — full client for visual / interaction playtest.

`./gradlew build` runs `compileJava` + the JUnit suite + assembles the jar. It does NOT run the in-world GameTests — those live in `runGameTestServer`. CI runs both as separate required status checks (`build` and `gameTest`) on every PR targeting `main`.

## Other Gradle tasks

- **`./gradlew runServer`** — dedicated server for testing multi-client scenarios.
- **`./gradlew runClientData`** / **`./gradlew runServerData`** — regenerate datagen output into `src/generated/resources`. moddev 2.0.x split the old `data` task into client- and server-side variants (1.21.4+); use the matching variant for the asset/data you're regenerating.
- **`./gradlew clean`** — wipe `build/`; useful when assets drift.

`build/` and `run/` are git-ignored.

## Where things live

- `src/main/java/com/flatts/productivefrogs/` — mod code
- `src/main/resources/` — assets, data packs, lang entries
- `src/test/java/` — JUnit tests
- `src/main/java/com/flatts/productivefrogs/gametest/` — in-world GameTests
- `docs/` — design docs (read these before non-trivial design changes)
