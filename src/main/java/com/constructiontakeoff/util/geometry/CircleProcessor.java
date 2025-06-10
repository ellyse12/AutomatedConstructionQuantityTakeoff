package com.constructiontakeoff.util.geometry;

import com.constructiontakeoff.model.LayerInfo;
import com.constructiontakeoff.util.dxf.DxfParsingException;
import com.constructiontakeoff.util.material.MaterialProvider;

import java.util.Map;
import java.util.logging.Logger;

public class CircleProcessor implements EntityProcessor {
    private static final Logger logger = Logger.getLogger(CircleProcessor.class.getName());
    private final MaterialProvider materialProvider;
    private final Map<String, Double> materialScaleFactors;

    public CircleProcessor(MaterialProvider materialProvider, Map<String, Double> materialScaleFactors) {
        this.materialProvider = materialProvider;
        this.materialScaleFactors = materialScaleFactors;
    }

    @Override
    public void process(double[] coordinates, String layer, String blockName,
            Map<String, String> properties, Map<String, LayerInfo> layerInfoMap)
            throws DxfParsingException {

        if (coordinates.length < 3) {
            throw DxfParsingException.invalidCoordinates("CIRCLE");
        }

        String material = materialProvider.getMaterial(layer);

        LayerInfo layerInfo = layerInfoMap.computeIfAbsent(layer, k -> new LayerInfo(layer, material));
        if (material != null && !material.equals(layerInfo.getMaterial())) {
            layerInfo.setMaterial(material);
        }

        double radius = coordinates[2];
        double area = GeometryCalculator.calculateCircleArea(radius);

        double scaleFactor = materialScaleFactors.getOrDefault(material, 1.0);
        double scaledArea = area * scaleFactor;

        layerInfo.addArea(scaledArea);

        layerInfo.incrementEntityCount(EntityType.CIRCLE);

        logger.fine("Processed CIRCLE with radius: " + radius +
                ", area: " + scaledArea + " on layer: " + layer + " for material: " + material);
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.CIRCLE;
    }
}
