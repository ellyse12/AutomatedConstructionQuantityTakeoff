package com.constructiontakeoff.util.geometry;

public class GeometryCalculator {
    private static final double ENDPOINT_TOLERANCE = 0.0001;

    public static double calculateLength(double[] coordinates) {
        if (coordinates.length < 4) {
            return 0;
        }

        double totalLength = 0;

        if (coordinates.length == 4) {
            double x1 = coordinates[0];
            double y1 = coordinates[1];
            double x2 = coordinates[2];
            double y2 = coordinates[3];

            return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
        }

        for (int i = 0; i < coordinates.length - 2; i += 2) {
            if (i + 3 >= coordinates.length)
                break;

            double x1 = coordinates[i];
            double y1 = coordinates[i + 1];
            double x2 = coordinates[i + 2];
            double y2 = coordinates[i + 3];

            double segmentLength = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
            totalLength += segmentLength;
        }

        boolean isClosed = isClosedByEndpoints(coordinates);
        if (!isClosed && coordinates.length >= 4) {
            double x1 = coordinates[coordinates.length - 2];
            double y1 = coordinates[coordinates.length - 1];
            double x2 = coordinates[0];
            double y2 = coordinates[1];

            double closingSegmentLength = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));

            if (closingSegmentLength > ENDPOINT_TOLERANCE) {
                totalLength += closingSegmentLength;
            }
        }

        return totalLength;
    }

    public static double calculatePolygonArea(double[] coordinates) {
        if (coordinates.length < 6) {
            return 0;
        }

        double sum = 0;
        for (int i = 0; i < coordinates.length - 2; i += 2) {
            double x1 = coordinates[i];
            double y1 = coordinates[i + 1];
            double x2 = coordinates[i + 2];
            double y2 = coordinates[i + 3];

            sum += (x1 * y2) - (x2 * y1);
        }

        double xn = coordinates[coordinates.length - 2];
        double yn = coordinates[coordinates.length - 1];
        double x1 = coordinates[0];
        double y1 = coordinates[1];
        sum += (xn * y1) - (x1 * yn);

        return Math.abs(sum / 2.0);
    }

    public static double calculateCircleArea(double radius) {
        return Math.PI * radius * radius;
    }

    public static boolean isClosedByEndpoints(double[] coordinates) {
        if (coordinates.length < 6)
            return false;

        double x1 = coordinates[0];
        double y1 = coordinates[1];
        double xn = coordinates[coordinates.length - 2];
        double yn = coordinates[coordinates.length - 1];

        return Math.abs(x1 - xn) < ENDPOINT_TOLERANCE &&
                Math.abs(y1 - yn) < ENDPOINT_TOLERANCE;
    }
}
