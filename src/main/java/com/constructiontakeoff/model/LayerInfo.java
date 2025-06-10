package com.constructiontakeoff.model;

import com.constructiontakeoff.util.geometry.EntityType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class LayerInfo {
    private final String layerName;
    private String material;
    private double totalLength = 0.0;
    private double totalArea = 0.0;
    private int entityCount = 0;
    private Map<EntityType, Integer> entityTypeCounts = new HashMap<>();
    private EntityType primaryEntityType = null;

    public LayerInfo(String layerName, String material) {
        this.layerName = layerName;
        this.material = material;
    }

    public String getLayerName() {
        return layerName;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public double getTotalLength() {
        return totalLength;
    }

    public void addLength(double length) {
        this.totalLength += length;
    }

    public double getTotalArea() {
        return totalArea;
    }

    public void addArea(double area) {
        this.totalArea += area;
    }

    public int getEntityCount() {
        return entityCount;
    }

    public void incrementEntityCount() {
        this.entityCount++;
    }

    public void incrementEntityCount(EntityType entityType) {
        if (entityType == null)
            return;

        this.entityCount++;

        int currentCount = entityTypeCounts.getOrDefault(entityType, 0);
        entityTypeCounts.put(entityType, currentCount + 1);

        if (primaryEntityType == null ||
                entityTypeCounts.get(entityType) > entityTypeCounts.getOrDefault(primaryEntityType, 0)) {
            primaryEntityType = entityType;
        }
    }

    public EntityType getPrimaryEntityType() {
        return primaryEntityType;
    }

    public boolean containsEntityType(EntityType entityType) {
        return entityTypeCounts.containsKey(entityType) && entityTypeCounts.get(entityType) > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        LayerInfo layerInfo = (LayerInfo) o;
        return Objects.equals(layerName, layerInfo.layerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(layerName);
    }

    @Override
    public String toString() {
        return "LayerInfo{" +
                "layerName='" + layerName + '\'' +
                ", material='" + material + '\'' +
                ", totalLength=" + totalLength +
                ", totalArea=" + totalArea +
                ", entityCount=" + entityCount +
                '}';
    }
}
