package com.constructiontakeoff.util.dxf;

public class DxfParsingException extends Exception {
    
    public DxfParsingException(String message) {
        super(message);
    }
    
    public DxfParsingException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public DxfParsingException(Throwable cause) {
        super(cause);
    }
    
    public static DxfParsingException invalidCoordinates(String entityType) {
        return new DxfParsingException("Insufficient coordinates for " + entityType);
    }
    
    public static DxfParsingException invalidEntityData(String entityType, String details) {
        return new DxfParsingException("Invalid data for " + entityType + ": " + details);
    }
    
    public static DxfParsingException fileReadError(Throwable cause) {
        return new DxfParsingException("Error reading DXF file", cause);
    }
}
