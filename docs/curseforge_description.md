<!--
This file WAS the canonical source for the CurseForge project Description.
SUPERSEDED (2026-07-05): the canonical copy now lives on the `main` branch
(the 2.x line), which describes BOTH release lines - the one CF page serves
both. Do not edit or mirror from this branch's copy; it is kept as the 1.x
historical record.

Workflow:
  1. Edit this file when you want to update the CF page.
  2. Copy the body (everything below this comment) into the CF Description
     field at https://authors.curseforge.com/#/projects/1552728/edit
  3. Save on CF.

Why a separate doc instead of reusing README.md: the CF Description is a
self-contained sales / onboarding surface for players who land on the CF
page without ever visiting GitHub. README.md targets contributors and
existing players. They overlap in spirit, diverge in structure.

This page is FEATURE-focused, NOT a changelog. Do not mirror the per-version
history here - it lives in CHANGELOG.md and is linked below. When a release
adds a headline feature, fold it into the relevant feature section; otherwise
the page stands unchanged across point releases.

Guardrails (per project memory):
  - No hard counts of species / variants / features (changes every release)
  - No mod-internal jargon (species-matched, Resource Slime, datapack)
  - No em-dashes (use hyphens, commas, or restructure)
  - Lead with player benefit; concrete examples beat abstract claims
  - Links use absolute GitHub URLs (CF readers don't have repo context)
-->

**Renewable resources, farmed by frogs.** Prime slimes with iron, copper, diamonds, or whatever resource you want, feed them to your frogs, and harvest the Froglights they drop. Slime Milkers keep the loop running unattended.

## How it works

1. Find a slime (they spawn in themed biomes).
2. Right-click it with the resource you want: iron ingot, copper ingot, diamond, prismarine shard, ender pearl, and more.
3. Feed the slime to the matching frog.
4. The frog drops a Froglight tied to that resource.
5. Smelt the Froglight in a vanilla furnace. Get your resource back.

That is the whole loop. Everything below makes it bigger, faster, or hands-off.

## Farm it hands-off

Stop running around priming wild slimes. Build a **Slime Milker**:

* Drop a Slime Bucket of the variant you want into the input slot.
* Wait a short cook. A bucket of matching Slime Milk comes out.
* Place the Slime Milk in the world. It spawns more of the same slime nearby.

No power, no pipes required, and hoppers feed and drain it. Toss **catalysts** into a placed Slime Milk source to push it further: more spawns before it runs dry, faster spawning, more slimes per spawn, or one that never runs dry at all. And every variant's milk is its own liquid, so fluid pipes and tanks can pump it, carry it, and place it back as the same slime - a fully automated milk line if you want one.

## Melt and cast

Froglights are not just decor. Build a **Froglight Crucible** and park it over a heat source - a torch works on day one, lava and fire melt faster:

* Water and Lava Froglights melt into real, bucketable water and lava. Renewable lava on a skyblock, no nether trips.
* Metal Froglights melt into molten metal worth two ingots each. Stack a **Casting Mold** on top and it casts the molten straight back into ingots - a three-block smelting tower with no power and no pipes.
* Some Froglights are heat sources themselves: a Lava Froglight burns like lava, a Blaze Froglight hotter than fire. The Crucible will even render cobblestone and stone straight down to lava.

## Breed better frogs

Your frogs are not interchangeable. Each one has stats that decide how much it eats, how much it drops, and how far it reaches, and you improve them by breeding. Pair two frogs on a treat and the offspring blends its parents and can climb a notch higher, so you ladder a line toward the cap and merge two specialists into one well-rounded producer. A breed never comes out worse than its parents, and a pack that just wants the plain loop can switch breeding off.

## Automate the whole loop

Build the **Terrarium**, a sealed multiblock habitat, and the loop runs on its own. Pipe Slime Milk into the controller, ceiling sprinklers rain slimes into the cavity, frogs raised inside eat them, and the Froglights collect in an output hatch for piping out. Bred stats carry through. It is the hands-off endgame for a resource farm, and a frog perch (a sweetslimed lily pad) pins a single frog over a collection point if you would rather keep things simple.

## The endgame

There is a tier past the everyday loop. Splash a potion onto a slime before a frog eats it and the Froglight keeps that effect, ready to place as a glowing aura, hold for yourself, or wear. The trophy resources (Wither Skeleton Skull, Nether Star, Dragon Egg, Dragon Breath) are farmable, but you earn them: prime the first with the real drop and wall its toxic milk inside a six-sided catalyst altar. The Froglight Cleaver is a sword that drops a slime's Froglight on the kill; the Ender Dragon drops a Princess's Kiss that turns a frog into a tradeable villager. And any frog drops Frog Legs, a renewable meat for a world short on animals.

## Learn as you play

New to the mod? Craft the in-game guide book (a book plus a slime ball) for an illustrated walkthrough of the whole thing, from your first slime to the boss altars, with build diagrams for the Terrarium and each altar. It needs [Patchouli](https://www.curseforge.com/minecraft/mc-mods/patchouli) installed, and modpack authors can extend it with their own pages.

## Modpack-friendly

Data-driven and extensible: adding a new resource is a single JSON file, no code and no recompile. JEI Information pages auto-extend to teach the new entry. Cross-mod resources for Mekanism, Create, Applied Energistics 2, AllTheOres, Mystical Agriculture, Powah, Industrial Foregoing, Refined Storage, and more come included, each gated behind a soft mod-loaded check that silently skips when the source mod is absent, so the same jar is safe in any pack. A deep set of config switches lets a pack ship exactly the parts it wants, down to individual resources or whole systems.

Full authoring guide: [docs on GitHub](https://github.com/Flatts3000/productive-frogs/tree/main/docs).

## Companion mods (recommended, optional)

* **[JEI](https://www.curseforge.com/minecraft/mc-mods/jei)**: Information pages on every Productive Frogs item explain the production role, who hunts what, and what smelts to what. Hover an item, press **U** or **R**.
* **[Jade](https://www.curseforge.com/minecraft/mc-mods/jade)**: shows the species and variant in the in-world tooltip.
* **[Patchouli](https://www.curseforge.com/minecraft/mc-mods/patchouli)**: unlocks the in-game guide book.

They are all optional. The mod works fine without them; you just lose the contextual UI and the in-game guide.

## Platform

* **Minecraft:** 1.21.1
* **Loader:** NeoForge 21.1.230 (NeoForge only; no Fabric port planned)
* **Java:** 21

## Links

* [GitHub repository](https://github.com/Flatts3000/productive-frogs)
* [Issue tracker](https://github.com/Flatts3000/productive-frogs/issues)
* [Full version history (CHANGELOG)](https://github.com/Flatts3000/productive-frogs/blob/main/CHANGELOG.md)
* [Source + design docs](https://github.com/Flatts3000/productive-frogs/tree/main/docs)

## What's next

Productive Frogs is mature and stable. New resources, cross-mod support, and polish land as they are ready - there are no fixed version gates, and updates keep both new and existing worlds working. Have a request? The [issue tracker](https://github.com/Flatts3000/productive-frogs/issues) is open.

**Not planned:** a Fabric port. Productive Frogs is NeoForge-only by design and will stay that way.
