package com.constructiontakeoff.model;

import java.util.HashMap;
import java.util.Map;

public class ElementReference {
    public final String elementId;
    public final String sheetName;
    public final String layer;
    public final double[] coordinates;
    public final String elementType;
    public final Map<String, String> properties;

    public ElementReference(String elementId, String sheetName, String layer,
            double[] coordinates, String elementType, Map<String, String> properties) {
        this.elementId = elementId;
        this.sheetName = sheetName;
        this.layer = layer;
        this.coordinates = coordinates;
        this.elementType = elementType;
        this.properties = properties != null ? properties : new HashMap<>();
    }

    public String getProperty(String key) {
        return properties.get(key);
    }

    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }

    @Override
    public String toString() {
        return "ElementReference{" +
                "elementId='" + elementId + '\'' +
                ", sheetName='" + sheetName + '\'' +
                ", layer='" + layer + '\'' +
                ", elementType='" + elementType + '\'' +
                '}';
    }
}
