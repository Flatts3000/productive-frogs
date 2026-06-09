public class SweetslimeItem extends Item {
    // ... existing code ...

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext flag) {
        if (!PFConfig.frogStatsEnabled.get()) {
            return;
        }

        // ... existing code ...
    }

    // ... existing code ...
}