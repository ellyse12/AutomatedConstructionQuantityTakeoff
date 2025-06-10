package com.constructiontakeoff.util.geometry;

public enum EntityType {
    LWPOLYLINE("m"),
    LINE("m"),
    POLYLINE("m"),
    CIRCLE("m²"),
    INSERT("pcs"),
    BLOCK("pcs");

    private final String defaultUnit;

    EntityType(String defaultUnit) {
        this.defaultUnit = defaultUnit;
    }

    public String getDefaultUnit() {
        return defaultUnit;
    }

    public static EntityType fromString(String type) {
        if (type == null)
            return null;

        try {
            return EntityType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
