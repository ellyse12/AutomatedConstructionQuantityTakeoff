package com.constructiontakeoff.util.material;

import java.util.HashMap;
import java.util.Map;


public class LayerMaterialProvider implements MaterialProvider {
    private final Map<String, String> layerMaterialMap;
    
    public LayerMaterialProvider() {
        layerMaterialMap = new HashMap<>();
        layerMaterialMap.put("A-WALL", "Walls");
        layerMaterialMap.put("A-COLS", "Columns");
        layerMaterialMap.put("A-BEAM", "Beams");
        layerMaterialMap.put("A-FLOR", "Floor");
        layerMaterialMap.put("A-ROOF", "Roof");
        layerMaterialMap.put("C-ROAD", "Roads");
        layerMaterialMap.put("P-PIPE", "Pipes");
        layerMaterialMap.put("E-CABL", "Cables");
        
        layerMaterialMap.put("A_WALL", "Walls");
        layerMaterialMap.put("A_COLS", "Columns");
        layerMaterialMap.put("A_BEAM", "Beams");
        layerMaterialMap.put("A_FLOR", "Floor");
        layerMaterialMap.put("A_ROOF", "Roof");
        layerMaterialMap.put("C_ROAD", "Roads");
        layerMaterialMap.put("P_PIPE", "Pipes");
        layerMaterialMap.put("E_CABL", "Cables");
        
        layerMaterialMap.put("WALL", "Walls");
        layerMaterialMap.put("WALLS", "Walls");
        layerMaterialMap.put("COLUMN", "Columns");
        layerMaterialMap.put("COLUMNS", "Columns");
        layerMaterialMap.put("BEAM", "Beams");
        layerMaterialMap.put("BEAMS", "Beams");
        layerMaterialMap.put("FLOOR", "Floor");
        layerMaterialMap.put("FLOORS", "Floor");
        layerMaterialMap.put("ROOF", "Roof");
        layerMaterialMap.put("ROOFS", "Roof");
        layerMaterialMap.put("DOOR", "Doors");
        layerMaterialMap.put("DOORS", "Doors");
        layerMaterialMap.put("WINDOW", "Windows");
        layerMaterialMap.put("WINDOWS", "Windows");
    }
    
    public LayerMaterialProvider(Map<String, String> mappings) {
        this();
        if (mappings != null) {
            layerMaterialMap.putAll(mappings);
        }
    }
    
    public void updateMapping(String layer, String material) {
        if (layer != null && material != null) {
            layerMaterialMap.put(layer, material);
        }
    }
    
    @Override
    public String getMaterial(String layer) {
        if (layer == null || layer.isEmpty()) {
            return "Unknown";
        }
        return layer;
    }
    
}
