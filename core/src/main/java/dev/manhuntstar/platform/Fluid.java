package dev.manhuntstar.platform;

/** The fluid in a block. */
public enum Fluid {
    EMPTY, WATER, FLOWING_WATER, LAVA, FLOWING_LAVA;

    public boolean isWater() {
        return this == WATER || this == FLOWING_WATER;
    }

    public boolean isLava() {
        return this == LAVA || this == FLOWING_LAVA;
    }
}
