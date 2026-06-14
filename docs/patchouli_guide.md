# Patchouli Guide Book

> The in-game guide book Productive Frogs ships (issue #243). Stable book id
> **`productivefrogs:guide`**. Built to be **extended by modpacks** - this doc is the
> contract for pack authors.

## What ships

- **Book definition:** `data/productivefrogs/patchouli_books/guide/book.json`
  (`use_resource_pack: true`, so content is loaded from `assets/` and is
  resource-pack-mergeable).
- **Content:** `assets/productivefrogs/patchouli_books/guide/en_us/{categories,entries}/...`
  - Categories (spaced `sortnum`s, 100 apart, so new ones slot between): `getting_started` (0),
    `frogs` (100), `slimes_and_milk` (200), `appliances` (300), `terrarium` (400), `advanced` (500).
  - Entries cover the core loop, the six species + stats/breeding + Frog Net + lily pad perch,
    Resource Slimes + Slime Milk + catalysts, the Spawnery / Milker+Churn / Crucible+Mold, the
    Terrarium (with a `multiblock` page), and the Advanced tier (Brewed Froglights + the boss tier).
- **Obtaining:** a craftable book (vanilla book + slime ball -> `patchouli:guide_book` stamped
  with the `productivefrogs:guide` component), recipe gated by `neoforge:conditions -> mod_loaded: patchouli`
  at `data/productivefrogs/recipe/guide_book.json`. Patchouli's `/patchouli` command also opens it.

## Dependency posture

Productive Frogs does **not** depend on Patchouli. The book is pure data/assets - inert if
Patchouli is absent, live if present - and the recipe is mod-gated. For dev/testing, drop a
`Patchouli-1.21.1-*-NEOFORGE` jar into `run/mods` (Sky Frogs already pins one); the guide only
renders under Patchouli at runtime, so it is verified by a manual `runClient` pass, not the build.

## How a modpack extends the book

Add files into **PF's book namespace** via your pack/resource pack - they merge into the same
book:

- New category: `assets/productivefrogs/patchouli_books/guide/<lang>/categories/<your_category>.json`
- New entry: `assets/productivefrogs/patchouli_books/guide/<lang>/entries/<category>/<your_entry>.json`
  (set the entry's `"category"` to any PF category id above, or your own new one).
- Pick `sortnum`s between PF's 100-spaced values to place your content where you want it.

**Known Patchouli limitation:** you can add **new categories and new entries**, but you cannot
append **pages to an existing PF entry** (Patchouli 1.20+; VazkiiMods/Patchouli#553). If you need
to expand a topic, add a sibling entry in the same category.

## Maintenance notes

- Keep the book id `productivefrogs:guide` and the category ids stable - packs key off them forever.
- The Terrarium `multiblock` page mirrors `TerrariumValidator`: a 7-wide x 7-deep x 6-tall shell
  around a **5x5x4** cavity (the old "5x5x5" string in some surfaces is stale; it is not a cube -
  6 tall, not 7). If the structure changes, update the `pattern` in `entries/terrarium/terrarium.json`.
- New content is en_us; other languages add `guide/<lang>/...`.
