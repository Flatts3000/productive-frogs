# In-game guidebook (2.0 build spec)

> **Status: SPEC / not yet built.** Tracks issue #318. The 1.x Patchouli book
> (frozen on `mc-1.21.1`, contract in `docs/patchouli_guide.md`) is **not**
> ported; 2.0 is a content rewrite on a new engine. This doc is the contract for
> that work: the engine, the dependency posture, the scope (what earns an entry
> and what does not), the voice, and the build order.
>
> **Decisions locked (2026-07-18, maintainer):**
> 1. **Engine: Modonomicon** (`com.klikli_dev:modonomicon-26.1.2-neoforge:2.1.0`).
>    Stable 26.1.2 release, MIT, JSON book/category/entry model - the closest
>    migration from the retired Patchouli structure. Chosen over Patchouli (its
>    fresh 26.1 build is beta with known multiblock-render issues) and GuideME
>    (beta, Markdown rewrite, LGPL). See the engine survey in #318.
> 2. **Dependency posture: soft-dep.** Ship the book as inert data; the player
>    installs Modonomicon to read it. Keeps PF's "no hard mod dependencies" rule.
> 3. **Config-gated content: document all, mark optional.** Every shipped feature
>    earns coverage; gated ones carry an "optional - toggle in config" note.
> 4. **EE lane: full chapter** (not a pointer), despite defaulting OFF.
> 5. **Species spawns: one consolidated entry** with a table, not six per-species
>    pages (reverses the 1.x granularity).

## Engine and dependency posture

- **Book id:** `productivefrogs:guide` (stable forever - packs key off it, same as
  the 1.x contract).
- **Maven:** Cloudsmith - `https://dl.cloudsmith.io/public/klikli-dev/mods/maven/`
  (`content { includeGroup "com.klikli_dev" }`).
- **Gradle:** `runtimeOnly` only, with `{ transitive = false }`. The book is pure
  data/assets - **no Java plugin, no compile-time reference** - so PF needs no
  `implementation`/`compileOnly`. `runtimeOnly` pulls Modonomicon into dev runs so
  `runClient` actually renders the book; it does **not** bundle into the published
  jar (same posture the retired RS/Curios dev-deps used). No `run/mods` drop-in to
  double-load against, so `runtimeOnly` is safe here (unlike Jade).
- **Obtaining in-game:** a craftable guide item, recipe gated by
  `neoforge:conditions -> mod_loaded: modonomicon`. Inert if the engine is absent,
  live if present. Modonomicon's own book-open path also works.
- **Data layout:** Modonomicon books live under `data/<ns>/modonomicon/books/<book>/`
  (book.json, `categories/`, `entries/`) - confirm the exact 2.x schema against the
  Modonomicon docs when building the skeleton; it is **not** identical to Patchouli's
  `assets/.../patchouli_books/` layout.

## Scope: the chapter map

Ten categories. `sortnum`s spaced 100 apart so packs can slot content between
(the 1.x spacing convention). Icon ids are real 2.0 registry ids.

| # | Category | Entries | Notes |
|---|----------|---------|-------|
| 1 | Getting Started | The core loop | slime -> frog eats -> Froglight -> smelt -> resource |
| 2 | Frogs | Six species; Stats & breeding; Frog Net; Lily Pad Perch | one `resource_frog` entity, identity via `FrogKind` |
| 3 | Slimes & Milk | Resource Slimes; Where slimes spawn (one table entry); Slime Milk; Milk catalysts; Slime Milk Basin | catalysts gated `MILK_CATALYSTS_ENABLED` |
| 4 | Froglights | Froglights; Brewed Froglights (Potion of Hopping) | brewing gated `BREWED_FROGLIGHTS_ENABLED`/`HOPPING_ENABLED` |
| 5 | **Predation** | Predator tier (Prowler/Cinder/Gulper/Rift); How a frog eats (player-credited loot, looting scales with Bounty, XP as orbs); Prey; Ender Net + Slurry Press + Mob Slurry + Mob Slurry Basin; Liquid Experience | the 2.0 centerpiece; document observable behavior, not the fake-player internals |
| 6 | Appliances | Slime Milker; Slime Churn; Spawnery; Crucible + Casting Mold; Slurry Press | Spawnery gated `SPAWNERY_ENABLED` (off by default) |
| 7 | Terrarium | Overview; Controller; Sprinkler; Incubator; Hatch | **1 multiblock page** |
| 8 | Boss Altars | Apex tier intro; Wither; Dragon; Warden; Elder Guardian | **4 multiblock pages**; each pays raw boss drops + Liquid Experience |
| 9 | The Equivalence Lane | Overview (off by default, how to enable); Alembic; Mimic Slime; Mimic Milk; Midas frog; Prismatic Froglight; Distiller | whole chapter gated `EQUIVALENCE_ENABLED` |
| 10 | Optional Extras | Froglight Cleaver; Frog Legs; Princess's Kiss; Config toggles overview | each config-gated; the toggles entry orients pack authors |

### Multiblock sourcing (no drift)

The four altar layouts already ship as GameTest-locked structure NBTs on `main`
(`data/productivefrogs/structure/{wither,dragon,warden,elder}_altar.nbt`) - the
altar multiblock pages **import those**, so a layout change fails the existing
`BossAltarTests` before it can desync the guide. The Terrarium has no structure
NBT (it is validator-driven); its page uses an inline pattern generated from
`TerrariumValidator` (7x7x6 shell, 5x5x4 cavity - the "5x5x5" string elsewhere is
stale). Keep the Terrarium pattern in sync with the validator by hand, or add a
`terrarium.nbt` + GameTest to lock it the way the altars are locked.

## Out of scope

- **Retired - never document:** the 21 mob-derived slime variants; the v1.14
  catalyst-altar (`spawn_catalyst`) mechanism; per-variant milk fluids (v1.8,
  collapsed to one component fluid); the 1.x release history.
- **Deferred - document when they return (2.x minors, maintainer ruling
  2026-07-05):** cross-mod partner content; the Curios worn-charm form of Brewed
  Froglights. No entries until the features ship.
- **Internal - never document:** transfer-API mechanics, the fake-player-kill
  implementation, registry/lifecycle wiring, GameTest layout. The guide documents
  **observable behavior**, not implementation. (The predation chapter explains
  *that* a frog's kill drops loot as if a player killed it and scales with Bounty -
  never *how* the FakePlayer does it.)
- **Not its own entry:** the "Prismatic / synthesized Froglight" is a
  component-stamped state of `configurable_froglight`, not a separate item - cover
  it inside the EE-lane Distiller/Froglight entry, not as a standalone page.

### Config-gated policy

Per ruling #3, cover every shipped feature regardless of default state. Gated
entries carry a short, factual line - e.g. "Off by default; a pack enables it in
the config" - never hype, never a full config dump. The Optional Extras "Config
toggles" entry is the one place that enumerates the flags, for pack authors.

## Voice

The guidebook uses the **existing pack voice** - no new voice. Source of truth is
the mc-pack-toolkit: `F:\minecraft-repos\mc-pack-toolkit\quest-voice\voice_spec.md`,
the **Guides** surface. Do not reinvent it here; this section only calibrates that
spec to this book.

- **Register (from the spec's Guides surface):** explanatory reference - calm,
  factual, second or third person, less imperative than a quest, more complete.
  No marketing verbs, always concrete. A guide explains a *system* end to end; it
  can run longer, but **length is never license to pad** - every sentence carries
  real information.
- **Decompose every entry**, exactly like a quest, just at system scale: name the
  **teach payload** (what a new player must learn to use this system - the concept,
  the non-obvious mechanic, the one gotcha) and the **guide payload** (what a
  veteran needs pointed at - the specific block, the number that matters). Write
  only those two. An entry that teaches and guides nothing new (the UI/JEI already
  shows it) earns little or no prose.
- **Tables and multiblock diagrams over prose** wherever a layout is clearer than a
  paragraph (the species-spawn table, catalyst effects, altar drops).
- **Personality lives in entry titles/subtitles only** (Do #4). Bodies stay purely
  functional. Titles may carry a light, grounded wink; a plain title is fine.
- **The spine test, by hand:** would someone who actually played PF write this, or
  does it sound like a marketer describing the mod? The linter cannot judge register.
- **Lint as a backstop:**
  `python "F:\minecraft-repos\mc-pack-toolkit\quest-voice\lint_quest_voice.py" <entry-dir>`.
  Clean lint is necessary, not sufficient.
- **House rule:** no em/en-dashes anywhere in the copy.

If a calibration here turns out to be pack-agnostic (true for any mod's Modonomicon
guide, not just PF), promote it up into the toolkit's Guides surface rather than
letting it live only here.

## Build order

Per #318, prove the engine before committing the full content rewrite.

1. **Skeleton proof-of-fit** (one PR): wire the Gradle dep; `book.json` + landing;
   two categories (Getting Started + Boss Altars); one text entry (the core loop,
   proving text/spotlight pages); one multiblock page **imported from a shipped
   altar `.nbt`** (proving the highest-risk page type against the real structure);
   the `mod_loaded:modonomicon`-gated recipe + lang keys. Verify by `runClient`.
2. **Content chapters**, roughly in map order, decomposed per the voice section.
   Each chapter is a reasonable PR unit.
3. **Pack-extension contract:** once the category ids are stable, document them for
   pack authors (mirror `docs/patchouli_guide.md`'s "How a modpack extends the book"
   section, adjusted for Modonomicon's `data/.../modonomicon/books/` layout and
   whatever its page-append limitations turn out to be).

## Verification

The guide is **client-render-only** - GameTest and the JUnit build are blind to it
(same as the 1.x Patchouli book). Every verification is a manual `runClient` pass:
book opens, categories render in `sortnum` order, text/spotlight/multiblock pages
draw, the multiblock diagram matches the in-world structure, icons resolve (no
missing-texture items), and the recipe appears only with Modonomicon present. Note
any icon-id typos - a wrong id renders a missing item silently.

## Open items (not blockers)

- Confirm the exact Modonomicon 2.x JSON schema (book/category/entry/page/multiblock
  fields) against the live docs at the skeleton stage - the format differs from
  Patchouli's and from older Modonomicon lines.
- Confirm Modonomicon's multiblock page can import from a vanilla structure `.nbt`
  (dense-multiblock-from-structure). If it cannot, fall back to an inline
  pattern/mapping generated from `AltarGeometry` + the validators, and lock it with
  a GameTest the way the `.nbt`s are locked.
- Decide whether to add a `terrarium.nbt` + GameTest to lock the Terrarium page the
  same way the altar pages are locked.
