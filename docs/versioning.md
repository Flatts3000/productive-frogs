# Versioning & Scope

How features are split across releases. V1 is the foundation; V2 layers automation on top without breaking V1.

## V1 — Base Mechanics + Appliances

The "playable foundation" release. **Appliance blocks** (single-block hand-operated stations, like vanilla brewing stand or composter) are in scope. **Automation machinery** (power-fed, multi-block, hopper-integrated, pipe-fed) is not.

**In scope:**

- **Frogspawn bottling via vanilla glass bottle** (no custom tool item — vanilla `minecraft:glass_bottle` is consumed on use against frogspawn)
- **Frog Egg** (item + block, placed on water by hand)
- **Slime Bucket** (item, transport mechanic)
- **Primed Frog Eggs** (6 blocks, one per category) — primed by right-clicking with a category material
- **Resource Tadpoles** and **Resource Frogs** (6 categories)
- **Resource Slimes** (N data-driven variants)
- **Parent slime species** for non-vanilla categories (Cave / Geode / Tide / Void Slime)
- **Slime acquisition** via two paths: vanilla-slime-split random discovery + immediate-transformation infusion
- **Slime Milker** (single appliance block, no power, hand-operated press) — converts a slime bucket to a typed milk bucket
- **Slime Milk fluid** (lava-flow, source-block-spawns-slimes, configurable depletion)
- **Froglight drops** (item entities — vanilla-hopper collectable)
- **Smelting recipes** (universal across categories) — players get 1× resource yield from any Froglight via vanilla furnace
- **Primer tags** and **slime category tags**
- **Cross-mod compat** via JSON for variant pools (Mekanism, Create, Thermal, Mythic Metals slime-variant entries gated by `mod_loaded`). Cross-mod *recipe* compat (crush 2× yields) lives in V2.

**The V1 rule of thumb:** if vanilla has a single-block appliance equivalent (furnace, brewing stand, composter, cauldron) that's V1 scope. If we'd be adding power, pipes, or multiblocks, that's V2.

**Why this scope:** establishes the entire mechanical loop the mod is "about" *with a working scalable economy*. The Milker is the production keystone — without it, the mod has no farming loop. V2 layers automation on top of an already-functional V1.

## V1.0.x — Port to MC 1.21.1 / NeoForge 21.1.230 (BLOCKS EVERYTHING ELSE)

[Sky Frogs](../../sky-frogs) — the modpack PF was built to anchor — is locked to MC 1.21.1 because its load-bearing dependencies (Ex Deorum, Skyblock Builder) have no 1.21.4+ NeoForge builds. PF must rebuild on 1.21.1 to ship inside the pack.

This is a **port, not a version bump**. Between 1.21.1 and our current 1.21.11 target, many APIs changed: `Identifier`/`ResourceLocation` rename, `ValueInput`/`ValueOutput` (1.21.5+) revert to `CompoundTag`, NeoForge transfer API rewrite (`ItemStacksResourceHandler` → `IItemHandler`), GameTest annotation revert, tint pipeline revert (delete `items/*.json` model definitions + restore `RegisterColorHandlersEvent.Item`), several singular-plural data folder renames (`tags/item/`→`tags/items/`, `recipe/`→`recipes/`, `loot_table/`→`loot_tables/`, `structure/`→`structures/`).

Estimated **3-4 weeks** across 11 phase PRs on a long-lived branch.

**Blocks**: the data-driven refactor, V1.1, V1.2 all wait on this. Refactoring code about to be ported is wasted effort — many of the refactor's load-bearing APIs differ between target versions.

Full design in [port_mc_1_21_1.md](./port_mc_1_21_1.md).

## V1.0.x — Data-Driven Variant Architecture Refactor (V1.1 prerequisite, post-port)

Internal architecture refactor. Removes the per-variant Java hardcoding in `PFFluidTypes.VARIANTS` and `PFItems.buildSlimeVariantSpawnEggs()` so that modpacks can add a Resource Slime variant by JSON only — no Java edits, no recompilation, no lang file changes.

**Why this lands before V1.1:** V1.1 adds 16 new variants. Shipping V1.1 on the current hardcoded architecture would lock that "hardcoded variants" debt deeper and make the refactor harder to do later (more variants to migrate). Refactor must come first.

**Scope:** spawn-egg collapse to component-based item, dynamic milk-fluid registration via mod-resource scanning at boot, lang derivation pattern, asset-generation scripts that scan variant JSONs.

**Estimated:** 2-3 weeks across ~4 PRs. Once shipped, V1.1 becomes a JSON-only authoring task.

Full design in [refactor_data_driven_variants.md](./refactor_data_driven_variants.md).

## V1.1 — Vanilla Resource Coverage

After the V1.0.x refactor lands, this becomes a pure-JSON data release adding every vanilla item fitting cleanly into one of the existing 6 categories.

**16 new variants** (28 total after V1.1):

| Category | New variants |
|---|---|
| METALLIC (+1) | netherite_scrap |
| MINERAL (+2) | gunpowder, clay_ball |
| GEM (+2) | quartz, amethyst |
| AQUATIC (+2) | ink_sac, glow_ink_sac |
| INFERNAL (+6) | blaze, glowstone_dust, soul_sand, soul_soil, obsidian, netherrack |
| ARCANE (+3) | echo_shard, chorus_fruit, shulker_shell |

Tier B candidates (`prismarine_crystals`, `nautilus_shell`, `ghast_tear`, `wither_rose`, `end_stone`) tracked in [v1_1_scope.md](./v1_1_scope.md) with default decisions if not resolved by freeze.

**Why not V2:** V2 is automation work that requires new blocks/code. V1.1 is content-completion that's pure JSON post-refactor, ships fast, and unblocks "the mod feels complete vs. the mod has obvious holes" — independent of when V2 is ready.

Full design lives in [v1_1_scope.md](./v1_1_scope.md).

## V1.2 — New Category for Biological Mob Drops

Adds a 7th category covering vanilla items that are harvested from living/undead mobs and don't fit any existing category:

- `bone` (skeleton)
- `rotten_flesh` (zombie)
- `string` (spider)
- `leather` (cow / horse)
- `feather` (chicken)

**Category name** — undecided. Candidates: BESTIAL, MORTAL, VISCERAL, FAUNA, CARNAL. Could also be split into UNDEAD (bone, rotten_flesh) + BESTIAL (string, leather, feather). Decided at V1.2 design time.

**Why this is V1.2 and not V1.1:** adding a new category is a Java edit — new `Category` enum constant + ARGB tint + primer tag + slime category tag + parent-species mapping for the new biome. V1.1's "JSON-only" charter excludes it.

**Likely additional V1.2 work** (rounding out the new category): a parent slime species for the new category (parallel to Cave/Geode/Tide/Void Slime) and biome-conditioned natural spawning (parallel to the existing biome modifier JSONs).

## V2 — Automation

Tools and blocks that let the player scale and automate the V1 loop. Built on top of V1; never replaces it.

**In scope:**

- **Auto-fed Slime Milker** — hopper-integrated variant of the V1 Milker, accepts slime buckets from a hopper and pushes milk buckets to an output side
- **Frog Terrarium / Habitat** block — placeable frog housing with input/output inventory
- **Auto-feeders** — hopper-fed slime delivery to nearby frogs (alternative to milk-spawn proximity)
- **Capacity / efficiency upgrades** for habitat blocks
- **Native crusher block** — optional in-house version (so the 2× crush path works without external mods)
- **Cross-mod crush 2× recipes** for metallic Froglights via Create / Mekanism / Thermal — conditional `mod_loaded` JSON recipes. The `productivefrogs:crushable/metallic` item tag is already reserved in V1; the recipes themselves wait on a multi-mod test environment that can validate each target mod's recipe shape.
- **Pipe/hopper-aware fluid handling** for Slime Milk
- Potentially: power compatibility (FE / NeoForge Energy)

**Why deferred:** these are all "scaling solutions" — they make V1 faster, denser, hands-off. They don't change *what the mod is*. Building V1 first ensures the design holds up without machines propping it up.

## V2 or V3 — Parked Ideas

Features captured during V1 design that aren't in scope yet but should be remembered:

- **Potion Slime / Alchemical category.** A 7th category (or a special variant within an existing one) tied to brewing. Possible mechanics: infuse with a potion item (instead of an ingot) → slime takes on that potion's effect → killing it drops the corresponding potion, or splashes its effect in an area. Parent could be a "Brew Slime" spawning near witch huts or nether wart. Worth scoping when V1 ships and we know how much hook-into-brewing the framework can support.

## V3+ — Speculative

Not committed. Possible future directions:

- Deep genetics / breeding tree on the frog side (Forestry-style)
- New dimensions (Frog Realm?)
- Tinkers-style upgradable frogs (gold trim, diamond skin)
- Quest/advancement integration

**Explicitly NOT planned:** Fabric port. Productive Frogs is NeoForge-only by design. See [architecture.md](./architecture.md).

## Compatibility Promise

- V2 datapacks must not break V1 worlds.
- V2 machines must remain optional — a player who never crafts a Slime Nursery in V2 still has every V1 capability.
- Cross-mod compat datapacks are independent of V1/V2 split.
