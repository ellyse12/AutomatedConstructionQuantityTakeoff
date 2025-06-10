package com.constructiontakeoff.util.dxf;

import com.constructiontakeoff.model.LayerInfo;
import com.constructiontakeoff.util.geometry.EntityProcessor;
import com.constructiontakeoff.util.geometry.EntityType;
import com.constructiontakeoff.util.material.MaterialProvider;

import java.util.Map;
import java.util.logging.Logger;

public class InsertProcessor implements EntityProcessor {
    private static final Logger logger = Logger.getLogger(InsertProcessor.class.getName());
    private final MaterialProvider layerMaterialProvider;
    private final MaterialProvider blockMaterialProvider;

    public InsertProcessor(MaterialProvider layerMaterialProvider, MaterialProvider blockMaterialProvider) {
        this.layerMaterialProvider = layerMaterialProvider;
        this.blockMaterialProvider = blockMaterialProvider;
    }

    @Override
    public void process(double[] coordinates, String layer, String blockName,
            Map<String, String> properties, Map<String, LayerInfo> layerInfoMap)
            throws DxfParsingException {

        if (blockName == null || blockName.isEmpty()) {
            logger.warning("INSERT entity without block name on layer: " + layer);
            return;
        }

        String materialName = blockMaterialProvider.getMaterial(blockName);

        if (materialName == null || materialName.startsWith("Unknown")) {
            materialName = layerMaterialProvider.getMaterial(layer);
        }

        final String finalMaterialName = materialName;

        String blockKey = blockName;

        LayerInfo blockInfo = layerInfoMap.computeIfAbsent(blockKey, k -> new LayerInfo(blockName, blockName));

        if (finalMaterialName != null && !finalMaterialName.equals(blockInfo.getMaterial())) {
            blockInfo.setMaterial(finalMaterialName);
        }

        blockInfo.incrementEntityCount(EntityType.INSERT);

        if (coordinates.length >= 2) {

            double scaleX = 1.0;
            double scaleY = 1.0;

            if (properties.containsKey("41")) {
                try {
                    scaleX = Double.parseDouble(properties.get("41"));
                } catch (NumberFormatException e) {
                }
            }

            if (properties.containsKey("42")) {
                try {
                    scaleY = Double.parseDouble(properties.get("42"));
                } catch (NumberFormatException e) {
                }
            }

            double area = Math.abs(scaleX * scaleY);
            if (area > 0) {
                blockInfo.addArea(area);
            } else {

                blockInfo.addArea(1.0);
            }
        }

        logger.info("Processed INSERT of block: " + blockName + " on layer: " + layer +
                " as material: " + blockInfo.getMaterial() + ", count: " + blockInfo.getEntityCount());
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.INSERT;
    }
}
