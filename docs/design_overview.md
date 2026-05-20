# Design Overview

## Inspiration

Two existing pieces of Minecraft design seeded this mod:

1. **Vanilla froglight mechanic (1.19+).** When a frog eats a small magma cube, the dropped block depends on the frog's biome variant (temperate → ochre, warm → pearlescent, cold → verdant). It's a unique predator-eats-prey resource interaction.
2. **Productive Bees genre.** A small breed of bee/specialty creature produces a specialized resource via a structured loop (collect → produce → process).

Productive Frogs generalizes (1) into a Productive-Bees-shaped loop, but with frogs and slimes instead of bees and flowers.

## Core Mental Model

Two orthogonal axes:

```
              Frog (category / diet)
              ----------------------
              Metallic   Mineral   Infernal   Gem   Arcane
Slime (species/resource):
  iron        ✓
  copper      ✓
  gold        ✓
  redstone               ✓
  lapis                  ✓
  blaze                            ✓
  diamond                                     ✓
  ender                                              ✓
  ...
```

- A **frog** has exactly one **category**.
- A **slime** has exactly one **category** and one **resource drop**.
- The frog *eats* (and produces from) a slime **only when their categories match**.
- Multiple slime species can share a category. They all feed the same frog type, but each drops its own specific resource.

This is a tag-driven model: frog categories and slime categories are tags in our namespace. Matching is a tag-set equality check.

## Why this split is good

- **Small frog roster.** ~5 frogs, not 30. Players don't need to collect dozens of trivially-different frogs.
- **Deep slime side.** Slime species can grow without bound — vanilla resources, modded resources, joke variants, joke modded variants. Unlimited expansion.
- **Cross-mod is automatic.** Adding "osmium slime" (Mekanism) is a JSON file in the metallic category. The metallic frog instantly eats it. No code change.
- **Data-pack overridable.** Modpack authors can rebalance, add, or remove slime variants without recompiling the mod.
- **Clear progression dimension.** Tiers come from the *frog* (which category you've unlocked), variety comes from the *slime* (what you've farmed within that category).

## Gameplay Loop

```
1. Find vanilla frogs and breed them (vanilla mechanic, slimeball + slimeball).
2. Resulting frogspawn placed by frogs over water.
3. Use an empty glass bottle on the frogspawn → Frog Egg item in inventory.
4. Place Frog Egg on water → Frog Egg block.
5. Right-click Frog Egg block with any material in a category's primer tag
   (e.g., iron ingot, copper ingot, osmium ingot, bronze ingot all prime
   the metallic category). Material consumed.
6. Block becomes a primed egg of that category (e.g., Metallic Frog Egg).
7. Egg hatches → Resource Tadpole → Resource Frog of that category.
8. Bring or breed a matching slime species near the frog.
9. Frog eats slime, drops the slime species's specific resource block.
10. Optionally process the dropped block (centrifuge/press — deferred to v0.2).
```

## Non-goals (v0.1)

- **No deep genetics tree.** Productive Bees has multi-step breeding parents. We don't.
- **No bespoke processing machine.** Drops are usable directly (e.g. iron froglight breaks into iron nuggets). Centrifuge/press deferred.
- **No new dimensions.** All content lives in vanilla dimensions + biomes.
- **No mob-combat reframe.** Frogs are not pets/fighters.
- **No changes to vanilla frog mechanics.** Vanilla `minecraft:frog` (warm/temperate/cold variants) and the vanilla magma-cube → froglight loop are untouched. Vanilla frogs serve our mod only as the source of frogspawn (which an empty glass bottle captures into Frog Eggs). Our Resource Frogs are a separate entity type.

## What's distinct vs. Productive Bees

| Productive Bees | Productive Frogs |
|---|---|
| Pollinator + flower | Predator + prey |
| ~120 bees, each is its own resource | ~5 frogs (categories) + N slimes (resources) |
| Centrifuge processing is core | Direct drops; processing optional/deferred |
| Hive multiblock | Frog placed in water + slime nearby; no required structure |
| Breeding tree creates new bees | Eggs primed by material; no breeding tree on the frog side |
