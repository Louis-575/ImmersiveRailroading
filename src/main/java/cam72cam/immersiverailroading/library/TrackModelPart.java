package cam72cam.immersiverailroading.library;

public enum TrackModelPart {
    RAIL_LEFT,
    RAIL_RIGHT,
    RAIL_BASE,
    HEEL_BLOCK,
    TOE_LEFT,
    TOE_RIGHT,
    POINT_LEFT,
    POINT_RIGHT,
    STRETCHER_BAR,
    WING_RAIL_LEFT,
    WING_RAIL_RIGHT,
    CHECK_RAIL_LEFT,
    CHECK_RAIL_RIGHT,
    TABLE;

    public boolean is(String str) {
        if(this != RAIL_BASE) {
            return str.startsWith(this.name());
        }
        for (TrackModelPart part : values()) {
            if (part != RAIL_BASE && part.is(str)) {
                return false;
            }
        }
        return true;
    }

    public boolean isDefaultRender() {
        return this == RAIL_LEFT || this == RAIL_RIGHT || this == RAIL_BASE;
    }
}
