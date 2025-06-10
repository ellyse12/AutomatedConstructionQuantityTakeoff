package com.constructiontakeoff.util.dxf;

import com.constructiontakeoff.model.ElementReference;
import com.constructiontakeoff.util.geometry.UnitScaleManager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DxfProcessingHelper {
    private static final Logger logger = Logger.getLogger(DxfProcessingHelper.class.getName());

    private final UnitScaleManager unitScaleManager;

    public DxfProcessingHelper(UnitScaleManager unitScaleManager) {
        this.unitScaleManager = unitScaleManager;
    }

    public List<Map<String, String>> collectTextAndHeaderInfo(File dxfFile, String sheetName) {
        logger.info("Collecting text entities and header information from: " + dxfFile.getPath());

        List<Map<String, String>> textEntities = new ArrayList<>();

        try {
            DxfParser parser = new DxfParser();
            parser.parse(dxfFile, (entityType, coordinates, layer, blockName, properties) -> {
                if ("TEXT".equals(entityType) || "MTEXT".equals(entityType)) {

                    Map<String, String> textInfo = new HashMap<>();
                    textInfo.put("type", entityType);
                    textInfo.put("layer", layer);
                    textInfo.put("x", String.valueOf(coordinates[0]));
                    textInfo.put("y", String.valueOf(coordinates[1]));

                    if (properties.containsKey("text")) {
                        textInfo.put("content", properties.get("text"));

                        String textContent = properties.get("text");

                    }

                    textInfo.putAll(properties);

                    textEntities.add(textInfo);
                }

                if (entityType.equals("HEADER") && properties != null) {
                    if (properties.containsKey("$INSUNITS")) {
                        try {
                            int unitCode = Integer.parseInt(properties.get("$INSUNITS"));
                            switch (unitCode) {
                                case 1:
                                    unitScaleManager.setUnitType(UnitScaleManager.UnitType.INCHES);
                                    break;
                                case 2:
                                    unitScaleManager.setUnitType(UnitScaleManager.UnitType.FEET);
                                    break;
                                case 4:
                                    unitScaleManager.setUnitType(UnitScaleManager.UnitType.MILLIMETERS);
                                    break;
                                case 5:
                                    unitScaleManager.setUnitType(UnitScaleManager.UnitType.CENTIMETERS);
                                    break;
                                case 6:
                                    unitScaleManager.setUnitType(UnitScaleManager.UnitType.METERS);
                                    break;
                                default:
                                    logger.warning("Unknown unit code: " + unitCode);
                            }
                        } catch (NumberFormatException e) {
                            logger.warning("Could not parse $INSUNITS value: " + properties.get("$INSUNITS"));
                        }
                    }

                    if (properties.containsKey("$DIMSCALE")) {
                        try {
                            double dimScale = Double.parseDouble(properties.get("$DIMSCALE"));
                            unitScaleManager.setScaleFactor(dimScale);
                            logger.info("Set drawing scale factor to " + dimScale + " from $DIMSCALE");
                        } catch (NumberFormatException e) {
                            logger.warning("Could not parse $DIMSCALE value: " + properties.get("$DIMSCALE"));
                        }
                    }
                }
            });

            logger.info("Collected " + textEntities.size() + " text entities from sheet: " + sheetName);

        } catch (DxfParsingException e) {
            logger.log(Level.SEVERE, "Error parsing DXF file during text collection: " + e.getMessage(), e);
            throw new RuntimeException("Failed to collect text information from DXF file: " + e.getMessage(), e);
        }

        return textEntities;
    }

    public void detectUnitsAndScale(List<Map<String, String>> textEntities) {
        logger.info("Detecting units and scale from collected information");

        if (textEntities == null || textEntities.isEmpty()) {
            logger.warning("No text entities found for unit/scale detection");
            return;
        }

        boolean scaleDetected = unitScaleManager.detectScaleFromText(textEntities);

        if (!scaleDetected) {
            logger.info("Attempting to detect scale using reference objects");

            for (Map<String, String> entity : textEntities) {
                String content = entity.get("content");
                if (content != null) {
                    content = content.toUpperCase();

                    if (content.contains("DOOR") || content.contains("WINDOW") ||
                            content.contains("TOILET") || content.contains("STAIR")) {

                        try {
                            double x = Double.parseDouble(entity.get("x"));
                            double y = Double.parseDouble(entity.get("y"));

                            logger.info("Found potential reference object: " + content +
                                    " at (" + x + ", " + y + ")");
                        } catch (NumberFormatException e) {
                        }
                    }
                }
            }
        }

        if (unitScaleManager.getCurrentUnitType() == UnitScaleManager.UnitType.UNKNOWN) {
            logger.info("Could not detect units, defaulting to millimeters");
            unitScaleManager.setUnitType(UnitScaleManager.UnitType.MILLIMETERS);
        }

        logger.info("Unit detection complete. Using unit type: " + unitScaleManager.getCurrentUnitType() +
                " with scale factor: " + unitScaleManager.getScaleFactor());
    }

    public String generateEntityId(String entityType, double[] coordinates, String layer) {
        StringBuilder sb = new StringBuilder();
        sb.append(entityType).append("-");
        sb.append(layer).append("-");

        if ("LINE".equals(entityType) && coordinates.length >= 4) {

            sb.append(Math.round(coordinates[0] * 100) / 100.0).append("-");
            sb.append(Math.round(coordinates[1] * 100) / 100.0).append("-");
            sb.append(Math.round(coordinates[2] * 100) / 100.0).append("-");
            sb.append(Math.round(coordinates[3] * 100) / 100.0);
        } else if (("LWPOLYLINE".equals(entityType) || "POLYLINE".equals(entityType)) && coordinates.length >= 4) {

            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
            double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

            for (int i = 0; i < coordinates.length; i += 2) {
                if (i + 1 < coordinates.length) {
                    double x = coordinates[i];
                    double y = coordinates[i + 1];
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }

            sb.append(Math.round(minX * 100) / 100.0).append("-");
            sb.append(Math.round(minY * 100) / 100.0).append("-");
            sb.append(Math.round(maxX * 100) / 100.0).append("-");
            sb.append(Math.round(maxY * 100) / 100.0);
        } else if ("CIRCLE".equals(entityType) && coordinates.length >= 3) {

            sb.append(Math.round(coordinates[0] * 100) / 100.0).append("-");
            sb.append(Math.round(coordinates[1] * 100) / 100.0).append("-");
            sb.append(Math.round(coordinates[2] * 100) / 100.0);
        } else {

            for (int i = 0; i < Math.min(coordinates.length, 6); i++) {
                sb.append(Math.round(coordinates[i] * 100) / 100.0);
                if (i < Math.min(coordinates.length, 6) - 1) {
                    sb.append("-");
                }
            }
        }

        return sb.toString();
    }

    public ElementReference createElementReference(String entityId, String sheetName, String layer,
            double[] coordinates, String entityType, Map<String, String> properties) {
        return new ElementReference(entityId, sheetName, layer, coordinates, entityType, properties);
    }
}
