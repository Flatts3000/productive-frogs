package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.event.SlimeInfusionHandler;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.monster.Slime;

/**
 * Shared assertions / setup used across the per-domain GameTest files. Keep test
 * logic that more than one domain needs here rather than duplicating it.
 */
final class PFGameTestHelpers {

    private PFGameTestHelpers() {
    }

    /** Fail unless {@code slime} resolves to the expected parent {@link Category}. */
    static void assertResolves(GameTestHelper helper, Slime slime, Category expected) {
        Category got = SlimeInfusionHandler.resolveParentSpecies(slime);
        if (got != expected) {
            helper.fail(slime.getClass().getSimpleName() + " resolved to " + got + ", expected " + expected);
        }
    }
}
