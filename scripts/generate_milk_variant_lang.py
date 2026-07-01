#!/usr/bin/env python3
"""SUPERSEDED - per-variant Slime Milk lang keys were collapsed in R-1 (26.1 port).

Slime Milk is now a SINGLE component-carrying fluid (the variant rides the
`SLIME_VARIANT` component), so there are no per-variant milk lang keys to fill.
The bucket / placed source / fluid names come from one key family plus a
variant-aware dynamic name (title-cased variant):

  item.productivefrogs.slime_milk_bucket        = "Slime Milk Bucket"
  item.productivefrogs.slime_milk_bucket.item   = "Bucket of %s Slime Milk"
  block.productivefrogs.slime_milk_source       = "Slime Milk"
  fluid_type.productivefrogs.slime_milk         = "Slime Milk"

These four keys are hand-committed in en_us.json and pinned by the JEI info-key
check in LangCompletenessTest (the per-variant milk templates were removed there).
This script is kept as a no-op stub (see docs/port_mc_26_1_reimplementation.md, R-1)
rather than deleted.
"""


def main() -> None:
    print("generate_milk_variant_lang: no-op - per-variant milk lang keys collapsed "
          "to a single key family in R-1 (26.1 port). Nothing to generate.")


if __name__ == "__main__":
    main()
