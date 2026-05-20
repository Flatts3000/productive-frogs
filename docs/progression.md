# Player Progression

This is the intended player journey from a fresh world to endgame Productive Frogs content. It encodes the order in which content gates open and what unlocks each tier.

## Tier 0 — Vanilla Setup

The player is doing normal early-game things. No mod intervention yet.

- Find vanilla frogs in a swamp/mangrove biome.
- Mine iron, get string.

## Tier 1 — Metallic (gateway tier)

**Unlock cost:** 1 empty glass bottle + 2 slimeballs (vanilla, to breed frogs) + 1 iron ingot (or any metallic primer).

1. Make an empty **glass bottle** (vanilla recipe: 3 glass blocks).
2. Find or trap two vanilla frogs and breed them with slimeballs (vanilla mechanic).
3. Wait for them to lay frogspawn on water.
4. Right-click the frogspawn with the empty glass bottle → bottle is consumed, get a **Frog Egg** item.
5. Place the Frog Egg item on a water source → it becomes a Frog Egg block.
6. Right-click the Frog Egg block with any **metallic primer** (iron ingot, copper ingot, gold ingot, or modded metals via tag).
   → Block becomes a **Metallic Frog Egg**, ingot consumed.
7. Wait for it to hatch → **Metallic Tadpole** → grows into a **Metallic Frog**.
8. Obtain an **Iron Slime**:
   - **Discovery path:** kill a vanilla slime (in a slime chunk or swamp). Each split offspring has a 5% chance (default, configurable) to be a Resource Slime from the metallic pool — iron, copper, gold, or any modded metallic.
   - **Infusion path:** right-click a vanilla slime with an iron ingot → slime is immediately transformed into an Iron Slime. Fully deterministic, costs one ingot per slime.
   - Both paths in detail: [slime_sourcing.md](./slime_sourcing.md).
9. **Scale up via milking** (see [farming.md](./farming.md)):
   - Bucket the Iron Slime at size 1 → **Bucket of Iron Slime**.
   - Place the bucket in a **Slime Milker** appliance block → **Bucket of Iron Slime Milk**.
   - Place milk source blocks near your Metallic Frog enclosure. Each source spawns size-1 Iron Slimes at ~20s intervals (default 16 spawns per block before drying up).
10. The Metallic Frog eats spawning Iron Slimes → drops **Iron Froglight** item entities.
11. Collect with hoppers. **Smelt** → 1 iron ingot per Froglight, or install Create / Mekanism / Thermal and **crush+smelt** → 2 iron ingots per Froglight.

At this point the player has bootstrapped iron production via frog farming.

## Tier 2 — Mineral

**Unlock cost:** any item in `primer/mineral` (redstone dust is the gateway).

- Same loop as T1, but prime the egg with redstone dust → **Mineral Frog**.
- Slimes in this category: Redstone Slime, Lapis Slime, Coal Slime.
- Drops: redstone-glow froglights → redstone dust, lapis froglights → lapis, etc.

## Tier 3 — Gem

**Unlock cost:** Diamond (or quartz, emerald, amethyst shard).

- Standard mid-game mining unlocks this. Same equipment gate as T1/T2 (iron pickaxe).
- Prime an egg → **Gem Frog**.
- Discovery parent: **Geode Slime** (spawns near amethyst geodes / mountain biomes).
- Slimes: Diamond Slime, Emerald Slime, Quartz Slime, Amethyst Slime.
- Drops: diamond froglights → diamonds, etc.

## Tier 4 — Aquatic

**Unlock cost:** Prismarine Shard (or prismarine crystals, sponge, nautilus shell).

- Requires water breathing potion + decent armor to raid an ocean monument.
- Prime an egg → **Aquatic Frog**.
- Discovery parent: **Tide Slime** (spawns in deep / lukewarm / warm ocean biomes; especially near ocean monuments).
- Slimes: Sponge Slime, Prismarine Slime, Coral Slime, Kelp Slime, Ink Slime, Nautilus Slime.
- Drops: sponge froglights → sponge blocks, prismarine froglights → prismarine shards, etc.

## Tier 5 — Infernal

**Unlock cost:** Nether access (need magma cream, blaze powder, or ghast tear as primer).

- Requires diamond pickaxe → obsidian → Nether portal.
- Reach the Nether, kill a magma cube (or blaze) to get the primer.
- Prime an egg → **Infernal Frog**.
- Discovery parent: `minecraft:magma_cube` (vanilla, Nether-only).
- Slimes: Magma Slime, Blaze Slime, Ghast Slime, Wither Slime.
- Drops: blaze froglights → blaze rods, ghast tear froglights → ghast tears, etc.

## Tier 6 — Arcane

**Unlock cost:** Ender Pearl (or echo shard, end stone).

- Endermen drop → unlock T6.
- Prime an egg → **Arcane Frog**.
- Discovery parent: **Void Slime** (spawns on outer End islands).
- Slimes: Ender Slime, Echo Slime, End Slime.
- Drops: ender froglights → ender pearls, echo froglights → echo shards.

## Modded Tier Extensions

Mods don't add new tiers — they add new **slime species within existing tiers**. Example:

- With Mekanism installed, the Metallic category gains Osmium Slime, Tin Slime, Lead Slime, Uranium Slime.
- With Mythic Metals installed, the Gem category gains Ruby Slime, Sapphire Slime, Topaz Slime.

A player who has progressed to T1 (Metallic Frog) can immediately farm all metallic slimes the moment they encounter them — modded or not. No additional gating.

## Progression Design Notes

- **Each tier costs one primer material.** No accumulating ladder cost — once you can pay tier N's primer, you can pay it again to make more frogs.
- **Tiers are unordered in code.** A player could theoretically rush to the Nether and unlock Infernal (T3) before completing Mineral (T2). The "tier order" above is *suggested progression based on cost-of-primer accessibility*, not enforced.
- **No experience or research tree.** Player skill = exploring biomes to find slimes + managing the breeding/feeding loop.
