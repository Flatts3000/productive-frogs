public class FrogEntity extends Entity {
    // ... existing code ...

    public FrogEntity(World world, double x, double y, double z) {
        super(world, x, y, z);

        if (!PFConfig.frogStatsEnabled.get()) {
            this.appetite = 10; // default appetite
            this.bounty = 10; // default bounty
            this.reach = 10; // default reach
        } else {
            // ... existing code ...
        }
    }

    // ... existing code ...

    public void readCustomDataToBuffer(CompoundTag tag) {
        if (PFConfig.frogStatsEnabled.get()) {
            // ... existing code ...
        } else {
            // freeze existing stat NBT
            if (tag.contains("appetite")) {
                this.appetite = tag.getInt("appetite");
            } else {
                this.appetite = 10; // default appetite
            }

            if (tag.contains("bounty")) {
                this.bounty = tag.getInt("bounty");
            } else {
                this.bounty = 10; // default bounty
            }

            if (tag.contains("reach")) {
                this.reach = tag.getInt("reach");
            } else {
                this.reach = 10; // default reach
            }
        }
    }

    // ... existing code ...
}