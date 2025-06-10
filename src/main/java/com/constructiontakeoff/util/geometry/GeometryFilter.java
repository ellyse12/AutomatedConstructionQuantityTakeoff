package com.constructiontakeoff.util.geometry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class GeometryFilter {
    private static final Set<String> ANNOTATION_LAYER_PATTERNS = new HashSet<>(Arrays.asList(
            "(?i).*ANNO.*", "(?i).*TEXT.*", "(?i).*DIM.*", "(?i).*HATCH.*",
            "(?i).*GRID.*", "(?i).*NOTE.*", "(?i).*LABEL.*", "(?i).*SYMB.*",
            "(?i).*LEGEND.*", "(?i).*TITLE.*", "(?i).*MARK.*"));

    private final List<Pattern> annotationPatterns = new ArrayList<>();

    private final List<Pattern> customExclusionPatterns = new ArrayList<>();

    private double minLineLength = 0.05;
    private double minAreaThreshold = 0.01;

    private final UnitScaleManager unitScaleManager;

    public GeometryFilter(UnitScaleManager unitScaleManager) {
        this.unitScaleManager = unitScaleManager;

        for (String pattern : ANNOTATION_LAYER_PATTERNS) {
            annotationPatterns.add(Pattern.compile(pattern));
        }
    }

    public void setMinLineLength(double minLength) {
        this.minLineLength = minLength;
    }

    public void setMinAreaThreshold(double minArea) {
        this.minAreaThreshold = minArea;
    }

    public void addExclusionPattern(String pattern) {
        customExclusionPatterns.add(Pattern.compile(pattern));
    }

    public boolean shouldExcludeLayer(String layer) {
        if (isAnnotationLayer(layer)) {
            return true;
        }

        for (Pattern pattern : customExclusionPatterns) {
            if (pattern.matcher(layer).matches()) {
                return true;
            }
        }

        return false;
    }

    public boolean passesFilter(String entityType, double[] coordinates) {
        if (!meetsMinimumSize(entityType, coordinates)) {
            return false;
        }

        return true;
    }

    public boolean shouldIncludeEntity(String entityType, String layer,
            double[] coordinates, Map<String, String> properties) {
        if (isAnnotationLayer(layer)) {
            return false;
        }

        if (!meetsMinimumSize(entityType, coordinates)) {
            return false;
        }

        switch (entityType) {
            case "LINE":
                return shouldIncludeLine(coordinates, properties);

            case "LWPOLYLINE":
            case "POLYLINE":
                return shouldIncludePolyline(coordinates, properties);

            case "CIRCLE":
                return shouldIncludeCircle(coordinates, properties);

            case "INSERT":
                return shouldIncludeBlock(properties);

            default:
                return true;
        }
    }

    public boolean isAnnotationLayer(String layer) {
        if (layer == null) {
            return false;
        }

        for (Pattern pattern : annotationPatterns) {
            if (pattern.matcher(layer).matches()) {
                return true;
            }
        }

        for (Pattern pattern : customExclusionPatterns) {
            if (pattern.matcher(layer).matches()) {
                return true;
            }
        }

        return false;
    }

    private boolean meetsMinimumSize(String entityType, double[] coordinates) {
        if (coordinates == null || coordinates.length < 2) {
            return false;
        }

        switch (entityType) {
            case "LINE":
            case "LWPOLYLINE":
            case "POLYLINE":
                double length = GeometryCalculator.calculateLength(coordinates);
                return unitScaleManager.toMeters(length) >= minLineLength;

            case "CIRCLE":
                if (coordinates.length >= 3) {
                    double radius = coordinates[2];
                    double area = GeometryCalculator.calculateCircleArea(radius);
                    return unitScaleManager.toMeters(area) >= minAreaThreshold;
                }
                return false;

            default:
                return true;
        }
    }

    private boolean shouldIncludeLine(double[] coordinates, Map<String, String> properties) {
        if (properties.containsKey("62")) {
            String colorCode = properties.get("62");
            if (colorCode.equals("1") || colorCode.equals("2") || colorCode.equals("3")) {
                double length = GeometryCalculator.calculateLength(coordinates);
                if (unitScaleManager.toMeters(length) < minLineLength * 2) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean shouldIncludePolyline(double[] coordinates, Map<String, String> properties) {
        if (properties.containsKey("70")) {
            String flags = properties.get("70");
            try {
                int flagValue = Integer.parseInt(flags);
                if ((flagValue & 1) == 1) {
                    if (properties.containsKey("8")) {
                        String layer = properties.get("8");
                        if (layer.toUpperCase().contains("HATCH") ||
                                layer.toUpperCase().contains("FILL") ||
                                layer.toUpperCase().contains("PATT")) {
                            return false;
                        }
                    }
                }
            } catch (NumberFormatException e) {
            }
        }

        if (GeometryCalculator.isClosedByEndpoints(coordinates)) {
            double area = GeometryCalculator.calculatePolygonArea(coordinates);
            if (unitScaleManager.toMeters(area) < minAreaThreshold) {
                return false;
            }
        }

        return true;
    }

    private boolean shouldIncludeCircle(double[] coordinates, Map<String, String> properties) {
        if (coordinates.length >= 3) {
            double radius = coordinates[2];
            if (unitScaleManager.toMeters(radius) < minLineLength / 2) {
                return false;
            }
        }

        return true;
    }

    private boolean shouldIncludeBlock(Map<String, String> properties) {
        if (properties.containsKey("2")) {
            String blockName = properties.get("2").toUpperCase();

            if (blockName.contains("TEXT") ||
                    blockName.contains("DIM") ||
                    blockName.contains("ARROW") ||
                    blockName.contains("SYMBOL") ||
                    blockName.contains("TITLE") ||
                    blockName.contains("NOTE")) {
                return false;
            }
        }

        return true;
    }

    public double[] cleanPolylineGeometry(double[] coordinates) {
        if (coordinates == null || coordinates.length < 4) {
            return coordinates;
        }

        List<Double> cleaned = new ArrayList<>();
        double minDistance = unitScaleManager.fromMeters(0.001);

        cleaned.add(coordinates[0]);
        cleaned.add(coordinates[1]);

        for (int i = 2; i < coordinates.length - 2; i += 2) {
            double x1 = cleaned.get(cleaned.size() - 2);
            double y1 = cleaned.get(cleaned.size() - 1);
            double x2 = coordinates[i];
            double y2 = coordinates[i + 1];

            double distance = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));

            if (distance > minDistance) {
                cleaned.add(x2);
                cleaned.add(y2);
            }
        }

        if (coordinates.length >= 4 && cleaned.size() >= 2) {
            double x1 = cleaned.get(cleaned.size() - 2);
            double y1 = cleaned.get(cleaned.size() - 1);
            double x2 = coordinates[coordinates.length - 2];
            double y2 = coordinates[coordinates.length - 1];

            double distance = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));

            if (distance > minDistance) {
                cleaned.add(x2);
                cleaned.add(y2);
            }
        }

        double[] result = new double[cleaned.size()];
        for (int i = 0; i < cleaned.size(); i++) {
            result[i] = cleaned.get(i);
        }

        return result;
    }
}
