package com.constructiontakeoff.util.geometry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UnitScaleManager {
    private static final Logger logger = Logger.getLogger(UnitScaleManager.class.getName());

    public static final double MM_TO_M = 0.001;
    public static final double CM_TO_M = 0.01;
    public static final double INCH_TO_M = 0.0254;
    public static final double FOOT_TO_M = 0.3048;

    public enum UnitType {
        MILLIMETERS,
        CENTIMETERS,
        METERS,
        INCHES,
        FEET,
        UNKNOWN
    }

    private UnitType currentUnitType = UnitType.UNKNOWN;
    private double scaleFactor = 1.0;

    private static final Pattern SCALE_PATTERN = Pattern.compile("(?i)SCALE\\s*(?:1\\s*:\\s*(\\d+)|(\\d+)\\s*=\\s*1)");

    private final Map<String, Double> referenceObjects = new HashMap<>();

    public UnitScaleManager() {
        referenceObjects.put("DOOR", 0.9);
        referenceObjects.put("WINDOW", 1.2);
        referenceObjects.put("PARKING", 2.5);
        referenceObjects.put("STAIR", 0.3);
        referenceObjects.put("TOILET", 0.7);
        referenceObjects.put("COLUMN", 0.3);
    }

    public void setUnitType(UnitType unitType) {
        this.currentUnitType = unitType;
        updateScaleFactor();
        logger.info("Unit type set to: " + unitType);
    }

    public void setScaleFactor(double factor) {
        if (factor > 0) {
            this.scaleFactor = factor;
            logger.info("Scale factor set to: " + factor);
        }
    }

    private void updateScaleFactor() {
        switch (currentUnitType) {
            case MILLIMETERS:
                scaleFactor = MM_TO_M;
                break;
            case CENTIMETERS:
                scaleFactor = CM_TO_M;
                break;
            case METERS:
                scaleFactor = 1.0;
                break;
            case INCHES:
                scaleFactor = INCH_TO_M;
                break;
            case FEET:
                scaleFactor = FOOT_TO_M;
                break;
            case UNKNOWN:
                break;
        }
    }

    public UnitType detectUnitType(Map<String, String> drawingHeader) {
        if (drawingHeader.containsKey("$INSUNITS")) {
            String unitCode = drawingHeader.get("$INSUNITS");
            switch (unitCode) {
                case "1":
                    currentUnitType = UnitType.INCHES;
                    break;
                case "2":
                    currentUnitType = UnitType.FEET;
                    break;
                case "4":
                    currentUnitType = UnitType.MILLIMETERS;
                    break;
                case "5":
                    currentUnitType = UnitType.CENTIMETERS;
                    break;
                case "6":
                    currentUnitType = UnitType.METERS;
                    break;
                default:
                    break;
            }
        }

        if (currentUnitType == UnitType.UNKNOWN && drawingHeader.containsKey("$TITLE")) {
            String title = drawingHeader.get("$TITLE");
            if (title.toUpperCase().contains("MM") || title.contains("MILLIMETER")) {
                currentUnitType = UnitType.MILLIMETERS;
            } else if (title.toUpperCase().contains("CM") || title.contains("CENTIMETER")) {
                currentUnitType = UnitType.CENTIMETERS;
            } else if (title.toUpperCase().contains(" M ") || title.contains("METER")) {
                currentUnitType = UnitType.METERS;
            } else if (title.contains("\"") || title.contains("INCH")) {
                currentUnitType = UnitType.INCHES;
            } else if (title.contains("'") || title.toUpperCase().contains("FT") || title.contains("FEET")) {
                currentUnitType = UnitType.FEET;
            }
        }

        updateScaleFactor();
        logger.info("Detected unit type: " + currentUnitType);
        return currentUnitType;
    }

    public boolean detectScaleFromText(List<Map<String, String>> textEntities) {
        for (Map<String, String> text : textEntities) {
            if (text.containsKey("TEXT")) {
                String content = text.get("TEXT");
                Matcher matcher = SCALE_PATTERN.matcher(content);
                if (matcher.find()) {
                    double scaleValue;
                    if (matcher.group(1) != null) {
                        scaleValue = Double.parseDouble(matcher.group(1));
                        logger.info("Detected scale 1:" + scaleValue + " from text");
                        return true;
                    } else if (matcher.group(2) != null) {
                        scaleValue = Double.parseDouble(matcher.group(2));
                        logger.info("Detected scale " + scaleValue + "=1 from text");
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean calibrateUsingReferenceObject(String objectName, double measuredSize) {
        String upperName = objectName.toUpperCase();

        Double expectedRealSize = null;
        for (Map.Entry<String, Double> entry : referenceObjects.entrySet()) {
            if (upperName.contains(entry.getKey())) {
                expectedRealSize = entry.getValue();
                break;
            }
        }

        if (expectedRealSize != null && measuredSize > 0) {
            double newScaleFactor = expectedRealSize / measuredSize;
            detectUnitTypeFromScaleFactor(newScaleFactor);
            setScaleFactor(newScaleFactor);
            logger.info("Calibrated scale using reference object '" + objectName +
                    "'. New scale factor: " + newScaleFactor);
            return true;
        }

        return false;
    }

    private void detectUnitTypeFromScaleFactor(double factor) {
        double mmDiff = Math.abs(factor - MM_TO_M);
        double cmDiff = Math.abs(factor - CM_TO_M);
        double mDiff = Math.abs(factor - 1.0);
        double inchDiff = Math.abs(factor - INCH_TO_M);
        double footDiff = Math.abs(factor - FOOT_TO_M);

        double minDiff = Math.min(Math.min(Math.min(mmDiff, cmDiff), Math.min(mDiff, inchDiff)), footDiff);

        if (minDiff == mmDiff) {
            currentUnitType = UnitType.MILLIMETERS;
        } else if (minDiff == cmDiff) {
            currentUnitType = UnitType.CENTIMETERS;
        } else if (minDiff == mDiff) {
            currentUnitType = UnitType.METERS;
        } else if (minDiff == inchDiff) {
            currentUnitType = UnitType.INCHES;
        } else {
            currentUnitType = UnitType.FEET;
        }
    }

    public void addReferenceObject(String objectName, double realSizeInMeters) {
        referenceObjects.put(objectName.toUpperCase(), realSizeInMeters);
    }

    public double toMeters(double value) {
        return value * scaleFactor;
    }

    public double fromMeters(double meters) {
        return meters / scaleFactor;
    }

    public UnitType getCurrentUnitType() {
        return currentUnitType;
    }

    public double getScaleFactor() {
        return scaleFactor;
    }
}
