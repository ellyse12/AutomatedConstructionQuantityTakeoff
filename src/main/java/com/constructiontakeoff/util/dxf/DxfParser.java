package com.constructiontakeoff.util.dxf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class DxfParser {
    private static final Logger logger = Logger.getLogger(DxfParser.class.getName());
    private static final int BUFFER_SIZE = 8192;

    public interface EntityHandler {

        void handleEntity(String entityType, double[] coordinates, String layer,
                String blockName, Map<String, String> properties) throws DxfParsingException;
    }

    public void parse(File dxfFile, EntityHandler handler) throws DxfParsingException {
        if (dxfFile == null || !dxfFile.exists()) {
            throw new DxfParsingException("DXF file does not exist");
        }

        logger.info("Starting to parse DXF file: " + dxfFile.getPath());
        AtomicInteger processedEntities = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        try (BufferedReader reader = new BufferedReader(new FileReader(dxfFile), BUFFER_SIZE)) {
            String line;
            String currentLayer = "";
            String currentBlockName = "";
            String currentEntity = "";
            Map<String, String> entityProperties = new HashMap<>();
            List<Double> coordinatesList = new ArrayList<>();
            boolean inEntity = false;
            boolean inBlock = false;

            while ((line = reader.readLine()) != null) {
                try {
                    line = line.trim();

                    if (line.equals("SECTION")) {
                        String nextLine = reader.readLine();
                        if (nextLine != null && nextLine.trim().equals("2")) {
                            String sectionName = reader.readLine();
                            if (sectionName != null) {
                                logger.fine("Entering section: " + sectionName.trim());
                            }
                        }
                    }

                    if (line.equals("LAYER")) {
                        while ((line = reader.readLine()) != null && !line.equals("0")) {
                            if (line.equals("2")) {
                                currentLayer = reader.readLine();
                                if (currentLayer != null) {
                                    currentLayer = currentLayer.trim();
                                    logger.fine("Processing layer: " + currentLayer);
                                }
                                break;
                            }
                        }
                    }

                    if (line.equals("BLOCK")) {
                        inBlock = true;
                        while ((line = reader.readLine()) != null && !line.equals("0")) {
                            if (line.equals("2")) {
                                currentBlockName = reader.readLine();
                                if (currentBlockName != null) {
                                    currentBlockName = currentBlockName.trim();
                                    logger.fine("Processing block definition: " + currentBlockName);
                                }
                                break;
                            }
                        }
                    } else if (line.equals("ENDBLK")) {
                        inBlock = false;
                        currentBlockName = "";
                    }

                    if (isEntityStart(line)) {
                        inEntity = true;
                        currentEntity = line;
                        entityProperties.clear();
                        coordinatesList.clear();

                        entityProperties.put("type", currentEntity);
                        entityProperties.put("layer", currentLayer);

                        if (inBlock) {
                            entityProperties.put("blockName", currentBlockName);
                        }

                    }

                    if (inEntity) {
                        if (line.equals("8")) {
                            String layerName = reader.readLine();
                            if (layerName != null) {
                                currentLayer = layerName.trim();
                                entityProperties.put("layer", currentLayer);
                            }
                        } else if (line.equals("2") && currentEntity.equals("INSERT")) {
                            String blockName = reader.readLine();
                            if (blockName != null) {
                                currentBlockName = blockName.trim();
                                entityProperties.put("blockName", blockName.trim());
                            }
                        } else if (isCoordinateCode(line)) {
                            String value = reader.readLine();
                            if (value != null) {
                                try {
                                    coordinatesList.add(Double.parseDouble(value.trim()));
                                } catch (NumberFormatException e) {
                                    logger.warning("Failed to parse coordinate value: " + value);
                                    errorCount.incrementAndGet();
                                }
                            }
                        } else if (isPropertyCode(line)) {
                            String code = line;
                            String value = reader.readLine();
                            if (value != null) {
                                entityProperties.put(code, value.trim());
                            }
                        }

                        if (line.equals("0") && !coordinatesList.isEmpty()) {
                            inEntity = false;

                            double[] coordinates = new double[coordinatesList.size()];
                            for (int i = 0; i < coordinatesList.size(); i++) {
                                coordinates[i] = coordinatesList.get(i);
                            }

                            try {
                                handler.handleEntity(currentEntity, coordinates, currentLayer,
                                        currentBlockName, entityProperties);
                                processedEntities.incrementAndGet();
                            } catch (DxfParsingException e) {
                                logger.warning("Error processing entity: " + e.getMessage());
                                errorCount.incrementAndGet();
                            }
                        }
                    }
                } catch (Exception e) {

                    logger.warning("Error processing line in DXF file: " + e.getMessage());
                    errorCount.incrementAndGet();

                    inEntity = false;
                    coordinatesList.clear();
                }
            }

            if (errorCount.get() > 0) {
                logger.warning(String.format(
                        "DXF parsing completed. Processed %d entities with %d errors. Check logs for details.",
                        processedEntities.get(), errorCount.get()));
            } else {
                logger.info(String.format("DXF parsing completed successfully. Processed %d entities.",
                        processedEntities.get()));
            }

        } catch (IOException e) {
            logger.severe("Failed to read DXF file: " + e.getMessage());
            throw DxfParsingException.fileReadError(e);
        }
    }

    private boolean isEntityStart(String line) {
        return line.equals("LWPOLYLINE") ||
                line.equals("LINE") ||
                line.equals("POLYLINE") ||
                line.equals("CIRCLE") ||
                line.equals("INSERT");
    }

    private boolean isCoordinateCode(String code) {
        return code.equals("10") || code.equals("20") || code.equals("30") ||
                code.equals("11") || code.equals("21") || code.equals("31") ||
                code.equals("40") || code.equals("41") || code.equals("42");
    }

    private boolean isPropertyCode(String code) {

        return code.equals("70") || code.equals("71") || code.equals("72") ||
                code.equals("73") || code.equals("62") || code.equals("6");
    }
}
