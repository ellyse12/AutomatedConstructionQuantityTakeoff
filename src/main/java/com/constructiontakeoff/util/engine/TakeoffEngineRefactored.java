package com.constructiontakeoff.util.engine;

import com.constructiontakeoff.model.LayerInfo;
import com.constructiontakeoff.model.QuantityItem;
import com.constructiontakeoff.model.User;
import com.constructiontakeoff.model.TakeoffRecord;
import com.constructiontakeoff.util.DatabaseService;
import com.constructiontakeoff.util.ThreadLocalUserContext;

import com.constructiontakeoff.util.dxf.*;
import com.constructiontakeoff.util.geometry.CircleProcessor;
import com.constructiontakeoff.util.geometry.EntityProcessor;
import com.constructiontakeoff.util.geometry.EntityType;
import com.constructiontakeoff.util.material.BlockMaterialProvider;
import com.constructiontakeoff.util.material.LayerMaterialProvider;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TakeoffEngineRefactored {
    private static final Logger logger = Logger.getLogger(TakeoffEngineRefactored.class.getName());

    public static final String ENTITY_LWPOLYLINE = "LWPOLYLINE";
    public static final String ENTITY_LINE = "LINE";
    public static final String ENTITY_POLYLINE = "POLYLINE";
    public static final String ENTITY_CIRCLE = "CIRCLE";
    public static final String ENTITY_INSERT = "INSERT";
    public static final String ENTITY_BLOCK = "BLOCK";

    public static final String UNIT_METERS = "m";
    public static final String UNIT_SQUARE_METERS = "m²";
    public static final String UNIT_PIECES = "pcs";

    private final LayerMaterialProvider layerMaterialProvider;
    private final BlockMaterialProvider blockMaterialProvider;

    private final Map<String, EntityProcessor> entityProcessors;

    private final Map<String, Double> materialScaleFactors;
    private final DatabaseService databaseService;

    private TakeoffEngineRefactored(Builder builder) {
        this.layerMaterialProvider = builder.layerMaterialProvider;
        this.blockMaterialProvider = builder.blockMaterialProvider;
        this.materialScaleFactors = builder.materialScaleFactors;
        this.databaseService = DatabaseService.getInstance();

        this.entityProcessors = new HashMap<>();

        entityProcessors.put(ENTITY_LINE,
                new LineProcessor(layerMaterialProvider, materialScaleFactors));

        entityProcessors.put(ENTITY_LWPOLYLINE,
                new PolylineProcessor(EntityType.LWPOLYLINE, layerMaterialProvider, materialScaleFactors));

        entityProcessors.put(ENTITY_POLYLINE,
                new PolylineProcessor(EntityType.POLYLINE, layerMaterialProvider, materialScaleFactors));

        entityProcessors.put(ENTITY_CIRCLE,
                new CircleProcessor(layerMaterialProvider, materialScaleFactors));

        entityProcessors.put(ENTITY_INSERT,
                new InsertProcessor(layerMaterialProvider, blockMaterialProvider));
    }

    public CompletableFuture<Map<String, Object>> processDxf(File dxfFile, User user, String pdfAbsolutePath) {
        return processDxf(dxfFile, user, pdfAbsolutePath, true);
    }

    public CompletableFuture<Map<String, Object>> processDxf(File dxfFile, User user) {
        return processDxf(dxfFile, user, null, true);
    }

    public CompletableFuture<Map<String, Object>> processDxf(File dxfFile) {
        return processDxf(dxfFile, null, null, true);
    }

    public CompletableFuture<Map<String, Object>> processDxf(File dxfFile, User user, String pdfAbsolutePath,
            boolean saveToHistory) {
        logger.info("Starting DXF processing for file: " + dxfFile.getPath());

        return CompletableFuture.<Map<String, Object>>supplyAsync(() -> {
            Map<String, LayerInfo> layerInfoMap = new HashMap<>();

            try {
                DxfParser parser = new DxfParser();
                parser.parse(dxfFile, (entityType, coordinates, layer, blockName, properties) -> {
                    EntityProcessor processor = entityProcessors.get(entityType);

                    if (processor != null) {
                        processor.process(coordinates, layer, blockName, properties, layerInfoMap);
                    } else {
                        logger.warning("No processor found for entity type: " + entityType);
                    }
                });
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error parsing DXF file: " + dxfFile.getPath(), e);

                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("items", FXCollections.observableArrayList());
                errorResult.put("recordId", -1);
                return errorResult;
            }

            ObservableList<QuantityItem> items = FXCollections.observableArrayList();
            logger.info("Converting LayerInfo map (size: " + layerInfoMap.size() + ") to QuantityItems.");

            for (LayerInfo info : layerInfoMap.values()) {
                String layerName = info.getLayerName();

                if (info.getEntityCount() > 0 &&
                        (info.containsEntityType(EntityType.INSERT) || info.containsEntityType(EntityType.BLOCK))) {
                    items.add(new QuantityItem(
                            layerName,
                            info.getEntityCount(),
                            UNIT_PIECES));
                }

                if (info.getTotalLength() > 0) {
                    items.add(new QuantityItem(
                            layerName,
                            info.getTotalLength(),
                            UNIT_METERS));
                }

                if (info.getTotalArea() > 0 &&
                        (info.containsEntityType(EntityType.CIRCLE) ||
                                info.containsEntityType(EntityType.LWPOLYLINE) ||
                                info.containsEntityType(EntityType.POLYLINE))) {
                    items.add(new QuantityItem(
                            layerName,
                            info.getTotalArea(),
                            UNIT_SQUARE_METERS));
                }

                if (info.getTotalLength() == 0 && info.getTotalArea() == 0 && info.getEntityCount() == 0) {
                    logger.warning("Layer " + layerName + " has no quantities to report.");
                }
            }
            logger.info("QuantityItems list created. Size: " + items.size());
            items.sort(Comparator.comparing(QuantityItem::getMaterial));

            int recordId = -1;
            if (saveToHistory) {
                recordId = saveToHistory(dxfFile, user, pdfAbsolutePath, items);
            } else {
                logger.info("Skipping saving to history as requested");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("items", items);
            result.put("recordId", recordId);
            if (pdfAbsolutePath != null && !pdfAbsolutePath.isEmpty()) {
                result.put("pdfAbsolutePath", pdfAbsolutePath);
            }
            return result;
        });
    }

    public int saveToHistory(File dxfFile, User user, String pdfAbsolutePath, ObservableList<QuantityItem> items) {
        try {
            logger.info("Starting database save process for takeoff history...");

            User userToUse = user;
            boolean newUserCreated = false;
            if (userToUse == null) {
                String currentUsername = System.getProperty("user.name");
                logger.info("No user provided, retrieved system username: '" + currentUsername + "'");
                if (currentUsername == null || currentUsername.trim().isEmpty()) {
                    currentUsername = "default_user";
                    logger.warning("System 'user.name' property is not set or empty. Using fallback username: "
                            + currentUsername);
                }
                logger.info("Calling databaseService.getOrCreateUser for username: '" + currentUsername + "'");
                userToUse = this.databaseService.getOrCreateUser(currentUsername);
                newUserCreated = true;
            } else {

                logger.info("Ensuring user exists in database: " + userToUse.getUsername());
                try {
                    User existingUser = this.databaseService.getOrCreateUser(userToUse.getUsername());
                    if (existingUser != null && existingUser.getId() != userToUse.getId()) {
                        userToUse = existingUser;
                        newUserCreated = true;
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error ensuring user exists in database: " + userToUse.getUsername(), e);
                }
            }

            logger.info("User object: " + (userToUse != null
                    ? "User ID=" + userToUse.getId() + ", username='" + userToUse.getUsername() + "'"
                    : "null"));

            String originalFileName = dxfFile.getName();
            String projectName = dxfFile.getName();

            if (userToUse != null) {
                List<TakeoffRecord> existingRecords = databaseService.getTakeoffHistoryForUser(userToUse);
                if (existingRecords != null && !existingRecords.isEmpty()) {

                    String baseFileName = originalFileName;
                    String extension = "";
                    int dotIndex = baseFileName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        extension = baseFileName.substring(dotIndex);
                        baseFileName = baseFileName.substring(0, dotIndex);
                    }

                    if (baseFileName.matches(".*\\([0-9]+\\)$")) {
                        baseFileName = baseFileName.replaceAll("\\([0-9]+\\)$", "");
                    }

                    baseFileName = baseFileName + extension;

                    String fileNameWithoutExt = baseFileName;
                    extension = "";
                    dotIndex = baseFileName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        fileNameWithoutExt = baseFileName.substring(0, dotIndex);
                        extension = baseFileName.substring(dotIndex);
                    }

                    int count = 0;
                    for (TakeoffRecord record : existingRecords) {
                        String recordFileName = record.getProcessedFileName();
                        String recordFileNameWithoutExt = recordFileName;
                        String recordExtension = "";
                        int recordDotIndex = recordFileName.lastIndexOf('.');
                        if (recordDotIndex > 0) {
                            recordExtension = recordFileName.substring(recordDotIndex);
                            recordFileNameWithoutExt = recordFileName.substring(0, recordDotIndex);
                        }

                        if (recordFileNameWithoutExt.matches(".*\\([0-9]+\\)$")) {
                            recordFileNameWithoutExt = recordFileNameWithoutExt.replaceAll("\\([0-9]+\\)$", "");
                        }

                        recordFileName = recordFileNameWithoutExt + recordExtension;

                        String recordBaseNameOnly = recordFileName;
                        int recordLastDotIndex = recordFileName.lastIndexOf('.');
                        if (recordLastDotIndex > 0) {
                            recordBaseNameOnly = recordFileName.substring(0, recordLastDotIndex);
                        }

                        String baseFileNameOnly = baseFileName;
                        int baseLastDotIndex = baseFileName.lastIndexOf('.');
                        if (baseLastDotIndex > 0) {
                            baseFileNameOnly = baseFileName.substring(0, baseLastDotIndex);
                        }

                        if (recordBaseNameOnly.equals(baseFileNameOnly)) {
                            count++;
                        }
                    }

                    if (count > 0) {
                        originalFileName = fileNameWithoutExt + "(" + (count + 1) + ")" + extension;
                        projectName = originalFileName;
                        logger.info(
                                "Found " + count + " existing records with the same filename. Using versioned name: "
                                        + originalFileName);
                    }
                }
            }

            List<QuantityItem> itemsToSave = new ArrayList<>(items);

            logger.info("Attempting to save takeoff history for user: "
                    + (userToUse != null ? userToUse.getUsername() : "null user") +
                    ", file: " + originalFileName +
                    ", project: " + projectName +
                    ", items count: " + itemsToSave.size());

            if (userToUse == null) {
                logger.severe("Cannot save takeoff history: User object is null.");
                return -1;
            } else {
                String processedFileName = dxfFile.getName();
                String status = "Completed";

                int historyId = this.databaseService.saveTakeoff(userToUse, originalFileName, processedFileName, status,
                        projectName, itemsToSave, pdfAbsolutePath);
                logger.info("Successfully saved takeoff history with ID: " + historyId + " for original file: "
                        + originalFileName + ", processed file: " + processedFileName + ", PDF path: "
                        + pdfAbsolutePath);

                if (newUserCreated) {
                    ThreadLocalUserContext.setCurrentUser(userToUse);
                    logger.info(
                            "Set ThreadLocalUserContext for newly created/retrieved user: " + userToUse.getUsername());
                }
                return historyId;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE,
                    "Failed to save takeoff history for file: " + (dxfFile != null ? dxfFile.getName() : "N/A"), e);
            logger.severe("Exception details: " + e.getMessage());
            if (e.getCause() != null) {
                logger.severe("Cause: " + e.getCause().getMessage());
            }
            return -1;
        }
    }

    public void updateMaterialMapping(String layer, String material) {
        layerMaterialProvider.updateMapping(layer, material);
    }

    public void updateBlockMapping(String blockName, String material) {
        blockMaterialProvider.updateMapping(blockName, material);
    }

    public static class Builder {
        private LayerMaterialProvider layerMaterialProvider;
        private BlockMaterialProvider blockMaterialProvider;
        private Map<String, Double> materialScaleFactors;

        public Builder() {

            this.layerMaterialProvider = new LayerMaterialProvider();
            this.blockMaterialProvider = new BlockMaterialProvider();
            this.materialScaleFactors = new HashMap<>();

            materialScaleFactors.put("Walls", 1.0);
            materialScaleFactors.put("Columns", 1.0);
            materialScaleFactors.put("Beams", 1.0);
            materialScaleFactors.put("Floor", 1.0);
            materialScaleFactors.put("Roof", 1.0);
        }

        public Builder withLayerMaterialProvider(LayerMaterialProvider provider) {
            if (provider != null) {
                this.layerMaterialProvider = provider;
            }
            return this;
        }

        public Builder withBlockMaterialProvider(BlockMaterialProvider provider) {
            if (provider != null) {
                this.blockMaterialProvider = provider;
            }
            return this;
        }

        public Builder withLayerMapping(String layer, String material) {
            layerMaterialProvider.updateMapping(layer, material);
            return this;
        }

        public Builder withBlockMapping(String blockName, String material) {
            blockMaterialProvider.updateMapping(blockName, material);
            return this;
        }

        public Builder withMaterialScaleFactor(String material, double scaleFactor) {
            materialScaleFactors.put(material, scaleFactor);
            return this;
        }

        public TakeoffEngineRefactored build() {
            return new TakeoffEngineRefactored(this);
        }
    }

    public static TakeoffEngineRefactored createDefault() {
        return new Builder().build();
    }
}
