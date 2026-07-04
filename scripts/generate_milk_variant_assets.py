#!/usr/bin/env python3
"""SUPERSEDED - per-variant Slime Milk assets were collapsed in R-1 (26.1 port).

Slime Milk is now a SINGLE component-carrying fluid: one `slime_milk` fluid +
`slime_milk_source` block + `slime_milk_bucket` item, with the variant riding the
`SLIME_VARIANT` data component (the 26.1 transfer API preserves it through
automation). There are no longer per-variant `<v>_slime_milk.json` blockstates or
`<v>_slime_milk_bucket.json` item models to generate.

The single-set assets are hand-committed:
  - assets/productivefrogs/blockstates/slime_milk_source.json
  - assets/productivefrogs/models/item/slime_milk_bucket.json
  - assets/productivefrogs/items/slime_milk_bucket.json   (component-read variant tint)

This script is kept as a no-op stub (see docs/port_mc_26_1_reimplementation.md, R-1)
rather than deleted, so a stale reference to it fails loudly instead of silently
regenerating deleted per-variant files.
"""


def main() -> None:
    print("generate_milk_variant_assets: no-op - per-variant milk collapsed to a "
          "single component-carrying fluid in R-1 (26.1 port). Nothing to generate.")


if __name__ == "__main__":
    main()
