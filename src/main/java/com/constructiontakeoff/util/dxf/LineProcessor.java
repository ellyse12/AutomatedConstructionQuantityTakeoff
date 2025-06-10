package com.constructiontakeoff.util.dxf;

import com.constructiontakeoff.model.LayerInfo;
import com.constructiontakeoff.util.geometry.EntityProcessor;
import com.constructiontakeoff.util.geometry.EntityType;
import com.constructiontakeoff.util.geometry.GeometryCalculator;
import com.constructiontakeoff.util.material.MaterialProvider;

import java.util.Map;
import java.util.logging.Logger;

public class LineProcessor implements EntityProcessor {
    private static final Logger logger = Logger.getLogger(LineProcessor.class.getName());
    private final MaterialProvider materialProvider;
    private final Map<String, Double> materialScaleFactors;

    public LineProcessor(MaterialProvider materialProvider, Map<String, Double> materialScaleFactors) {
        this.materialProvider = materialProvider;
        this.materialScaleFactors = materialScaleFactors;
    }

    @Override
    public void process(double[] coordinates, String layer, String blockName,
            Map<String, String> properties, Map<String, LayerInfo> layerInfoMap)
            throws DxfParsingException {

        if (coordinates.length < 4) {
            throw DxfParsingException.invalidCoordinates("LINE");
        }

        String material = materialProvider.getMaterial(layer);

        LayerInfo layerInfo = layerInfoMap.computeIfAbsent(layer, k -> new LayerInfo(layer, material));

        if (material != null && !material.equals(layerInfo.getMaterial())) {
            layerInfo.setMaterial(material);
        }

        double length = GeometryCalculator.calculateLength(coordinates);

        double scaleFactor = materialScaleFactors.getOrDefault(material, 1.0);
        double scaledLength = length * scaleFactor;

        layerInfo.addLength(scaledLength);

        layerInfo.incrementEntityCount(EntityType.LINE);

        logger.fine(
                "Processed LINE with length: " + scaledLength + " on layer: " + layer + " for material: " + material);
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.LINE;
    }
}
