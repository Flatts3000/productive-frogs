# Farming (V1)

The end-to-end production loop a player runs in V1, centered on the **Slime Milker** appliance block, the **Slime Milk** fluid, and the resulting **slime fountain** that feeds Resource Frogs at scale.

## The Loop

```
1. Source slime  (vanilla farm, or infusion of a vanilla slime to make a Resource Slime)
       ↓ bucket the slime at size 1 (Slime Bucket item)
2. Slime Milker (appliance block; right-click with slime bucket)
       ↓ consume the slime, output 1 Bucket of <Variant> Slime Milk
3. Place Slime Milk in the world  (becomes a flowing liquid block, lava-like flow)
       ↓ each milk SOURCE block spawns 1 size-1 slime at a configurable interval
4. Matching Resource Frog nearby
       ↓ frog tongue-eats the spawned slime
5. Frog drops a Froglight block of that slime's variant
       ↓ break Froglight
6. Process to base resource (smelt always; crush only if metallic and a compat mod is installed)
```

## Slime Milker (block)

- **ID**: `productivefrogs:slime_milker`
- **Visual**: themed as a single-block mechanical press. Animation/sound when activated.
- **Power**: none. V1 appliance — no fuel, no redstone, no automation hookup. Sits in the same conceptual bucket as a brewing stand or composter.
- **Interaction**: right-click while holding a `Slime Bucket` (size-1 slime captured, any variant):
  - The slime inside the bucket is consumed.
  - The bucket transforms into a `Bucket of <Variant> Slime Milk`.
  - Brief press animation + squelch sound feedback.
- **No GUI / no internal storage** — instant operation, player retains immediate control. V2 may add an auto-feeding variant.
- **Input restriction**: must be a `Slime Bucket` (i.e. only bucketed slimes work). Direct living-slime contact does nothing.

## Slime Milk (fluid)

- One fluid variant per slime variant: `productivefrogs:iron_slime_milk`, `productivefrogs:copper_slime_milk`, `productivefrogs:sponge_slime_milk`, `productivefrogs:ender_slime_milk`, etc.
- Plus `productivefrogs:vanilla_slime_milk` (from milking a vanilla green slime — useful for slime farming itself) and `productivefrogs:magma_slime_milk` (from magma cubes).
- **Flow**: lava-style. Slower than water; 4-block flow distance in overworld. Limits sprawling milk floods.
- **Pickup**: empty bucket scoops a source block, converting it to a typed milk bucket. Other buckets (water, etc.) do not interact.
- **Walking on it**: passive — no damage, no slowdown beyond normal liquid penalties. Visible color tint per variant.
- **Spawning**: only **source blocks** spawn slimes. Flowing blocks are decorative.

## Slime spawning from milk

Each milk source block independently rolls a spawn tick:

- **Default interval**: 20 seconds, ± uniform jitter in [-10s, +10s] (so spawns appear at irregular real times in [10s, 30s]).
- **Cap**: 1 slime per source block per spawn event. There is no mob-occupancy gate — entities (frogs, players, other slimes) standing on or near the source do NOT block the spawn. Production is intentionally insensitive to traffic so a frog mid-tongue can't stall a fountain.
- **Spawned slime**: always size 1, matching the milk's variant. Vanilla milk spawns size-1 vanilla slimes; Iron Slime Milk spawns size-1 Iron Resource Slimes; etc.
- **Spawn position**: scan the 26 surrounding blocks in the 3×3×3 cube around the source for any with a sturdy top face whose block-above is non-motion-blocking; the slime lands on top of the first match. Iteration order biases toward natural rim spawns (same-y cardinals → same-y diagonals → below plane → above plane), so a milk pool on solid ground produces horizontally-adjacent slimes by default. If no sturdy neighbour exists anywhere in the 3×3×3, the slime spawns inside the source block itself (the milk fluid is non-collision, so this fallback always succeeds — spawns never fail and never need to retry).

## Depletion

- **Configurable** (mod config `productivefrogs-common.toml`): `depletionEnabled` ON or OFF (default ON), `depletionCount` 1–16 (default 16).
- **When ON**: each milk source block carries a `spawns_remaining` blockstate that starts at `depletionCount` on placement and decrements by one per spawn. When it reaches zero the next tick replaces the block with air (true drain — not a fluid-state reset).
- **When OFF**: source blocks ignore the counter and spawn indefinitely. Best for creative-mode play or pack authors who want low-friction production.
- **Visual countdown** (e.g. subtle color desaturation as the counter approaches zero) is deferred to the polish backlog — V1 ships with the counter persisted in the blockstate but no visual feedback.

## Frog Feeding

- Frogs in range of a spawned slime detect it via vanilla-style tongue AI, with a species-match check.
- If `frog.getCategory() == slime.getCategory()`, the frog targets the slime and eats it on its next tongue tick.
- Frog drops a `<Variant> Configurable Froglight` block at its current position (item entity that vanilla hoppers can collect).
- Cooldown: vanilla frog tongue has a natural cooldown (~1 second) — that pacing carries over.

## Drop Processing

| Step | What | Notes |
|---|---|---|
| Break the Froglight | Get 1 Froglight item | Any tool, instant break (like vanilla glowstone) |
| Smelt the Froglight | Get 1 unit of the base resource | Works for every variant: Iron Froglight → Iron Ingot, Sponge Froglight → Sponge Block, Ender Froglight → Ender Pearl, etc. |
| Crush the Froglight (Cave variants only, V2) | Get 2× powder/dust | Requires an installed crushing mod — see Cross-Mod section below |
| Smelt the powder | Get 1 ingot per powder (2 total from one crushed Froglight) | Net: crush+smelt path yields 2× material vs direct smelt |

**Smelting**: works on every variant Froglight. Each variant has its own smelt recipe → its base resource.

**Crushing**: applies **only** to Cave-species metal variants (iron, copper, gold). Non-metal Froglights have no crush recipe; if a player tries to crush one, it passes through unchanged. Cross-mod crush recipes are V2 scope — the `productivefrogs:crushable/metallic` tag is reserved.

## Cross-Mod: Crushing

V1 does NOT ship its own crusher block or pestle tool. Crushing requires an installed processing mod. We ship compat recipes (conditional `mod_loaded` JSON) for the popular options:

| Mod | Block | Recipe consumed |
|---|---|---|
| Create | Crushing Wheels / Millstone | 1 Iron Froglight → 2 Crushed Iron |
| Mekanism | Crusher / Enrichment Chamber | 1 Iron Froglight → 2 Iron Dust |
| Thermal Series | Pulverizer | 1 Iron Froglight → 2 Iron Dust |

We tag Froglights with `productivefrogs:crushable/metallic` so other mods can reference the full set. Mods produce their tag-standard outputs (`c:dusts/iron`, `c:raw_materials/iron`, etc.), which smelt back into ingots via vanilla furnace recipes the mods ship anyway.

If no crushing mod is installed, players can still smelt directly. The 2× path is a soft incentive to install a processing mod — but it's never required.

## Throughput Sketch (default settings)

Rough numbers a player can expect in V1 with default config:

- 1 milk source, no depletion: ~3 slimes/min (every 20s avg)
- 8 milk sources, depletion ON (16 each): 128 slimes → ~7 minutes of throughput before refilling
- 8 frogs nearby: easily keep up with 8 milk blocks at 3/min each = 24 frog-feedings/min
- 1 ingot of input → 16 ingots out (direct smelt) or 32 ingots out (crushed via compat mod)

These are the levers to tune balance:
- Spawn interval (config)
- Depletion count (config)
- Smelting output (loot table per Froglight)
- Crush recipe output (compat recipe per mod)

## What's NOT in V1

- **Automated milker** (hopper-fed). The Milker is single-block, hand-operated. V2 may add an auto-fed variant or pipe support.
- **Frog terrarium / housing block.** Frogs in V1 just live where you place them, near water.
- **Drop-collection block.** Use vanilla hoppers under the frog area to collect Froglight item drops.
- **Crusher / Pestle.** Deferred to other mods; we ship the compat recipes.
- **Milk in non-bucket containers** (jugs, tanks, etc.). Bucket only in V1.

## Summary: V1 Farm Layout (Iron variant, idealized)

```
 +---------------------------+
 |  Cave Slime hunt          |   ← dripstone caves, deep dark, lush caves
 |  (player infuses with     |
 |   iron ingot, buckets at  |
 |   size 1)                 |
 +-----------+---------------+
             ↓ Iron Slime buckets
       [ Slime Milker ]        ← furnace-shaped block, 100-tick cook
             ↓ Iron Slime Milk buckets
 +---------------------------+
 |  Field of milk source     |   ← lava-like flow, source blocks spawn Iron Slimes
 |  blocks (8-16 of them)    |
 |    + Cave Frogs           |   ← eat spawning Iron Slimes, drop Iron Froglights
 |    + hoppers below        |   ← vanilla item-collection
 +-----------+---------------+
             ↓ hopper to chest
 +---------------------------+
 |  Furnace (smelt direct)   |
 |   OR                      |
 |  Create/Mekanism/Thermal  |   ← compat crusher → 2× powder → smelt (V2)
 +---------------------------+
```
