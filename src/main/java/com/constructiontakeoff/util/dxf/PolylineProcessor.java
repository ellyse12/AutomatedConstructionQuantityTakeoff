package com.constructiontakeoff.util.dxf;

import com.constructiontakeoff.model.LayerInfo;
import com.constructiontakeoff.util.geometry.EntityProcessor;
import com.constructiontakeoff.util.geometry.EntityType;
import com.constructiontakeoff.util.geometry.GeometryCalculator;
import com.constructiontakeoff.util.material.MaterialProvider;

import java.util.Map;
import java.util.logging.Logger;

public class PolylineProcessor implements EntityProcessor {
    private static final Logger logger = Logger.getLogger(PolylineProcessor.class.getName());
    private final MaterialProvider materialProvider;
    private final Map<String, Double> materialScaleFactors;
    private final EntityType entityType;

    public PolylineProcessor(EntityType entityType, MaterialProvider materialProvider,
            Map<String, Double> materialScaleFactors) {
        this.entityType = entityType;
        this.materialProvider = materialProvider;
        this.materialScaleFactors = materialScaleFactors;
    }

    @Override
    public void process(double[] coordinates, String layer, String blockName,
            Map<String, String> properties, Map<String, LayerInfo> layerInfoMap)
            throws DxfParsingException {

        if (coordinates.length < 4) {
            throw DxfParsingException.invalidCoordinates(entityType.name());
        }

        String material = materialProvider.getMaterial(layer);

        LayerInfo layerInfo = layerInfoMap.computeIfAbsent(layer, k -> new LayerInfo(layer, material));
        if (material != null && !material.equals(layerInfo.getMaterial())) {
            layerInfo.setMaterial(material);
        }
        boolean isClosed = isClosedPolyline(properties, coordinates);

        if (isClosed && coordinates.length >= 6) {
            double area = GeometryCalculator.calculatePolygonArea(coordinates);

            if (area > 0.01) {
                double scaleFactor = materialScaleFactors.getOrDefault(material, 1.0);
                double scaledArea = area * scaleFactor;

                layerInfo.addArea(scaledArea);

                layerInfo.incrementEntityCount(entityType);

                logger.fine("Processed closed " + entityType.name() +
                        " with area: " + scaledArea + " on layer: " + layer + " for material: " + material);
                return;
            }
        }

        double length = GeometryCalculator.calculateLength(coordinates);

        double scaleFactor = materialScaleFactors.getOrDefault(material, 1.0);
        double scaledLength = length * scaleFactor;

        layerInfo.addLength(scaledLength);

        layerInfo.incrementEntityCount(entityType);

        logger.fine("Processed open " + entityType.name() +
                " with length: " + scaledLength + " on layer: " + layer + " for material: " + material);
    }

    private boolean isClosedPolyline(Map<String, String> properties, double[] coordinates) {
        if (properties.containsKey("70")) {
            try {
                int flag = Integer.parseInt(properties.get("70"));
                if ((flag & 1) == 1) {
                    return true;
                }
            } catch (NumberFormatException e) {
                logger.warning("Failed to parse polyline flag: " + properties.get("70"));
            }
        }

        return GeometryCalculator.isClosedByEndpoints(coordinates);
    }

    @Override
    public EntityType getEntityType() {
        return entityType;
    }
}
