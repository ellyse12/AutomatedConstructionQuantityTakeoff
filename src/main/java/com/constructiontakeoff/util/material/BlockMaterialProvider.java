package com.constructiontakeoff.util.material;

import java.util.HashMap;
import java.util.Map;

public class BlockMaterialProvider implements MaterialProvider {
    private final Map<String, String> blockMaterialMap;

    public BlockMaterialProvider() {
        blockMaterialMap = new HashMap<>();

        blockMaterialMap.put("DOOR", "Doors");
        blockMaterialMap.put("WINDOW", "Windows");
        blockMaterialMap.put("PLUMB", "Plumbing Fixtures");
        blockMaterialMap.put("SINK", "Sinks");
        blockMaterialMap.put("BASIN", "Sinks");
        blockMaterialMap.put("TOILET", "Toilets");
        blockMaterialMap.put("WC", "Toilets");
        blockMaterialMap.put("SWITCH", "Switches");
        blockMaterialMap.put("OUTLET", "Outlets");
        blockMaterialMap.put("SOCKET", "Outlets");

        blockMaterialMap.put("FURNITURE", "Furniture");
        blockMaterialMap.put("CHAIR", "Furniture");
        blockMaterialMap.put("TABLE", "Furniture");
        blockMaterialMap.put("DESK", "Furniture");
        blockMaterialMap.put("BED", "Furniture");
        blockMaterialMap.put("SOFA", "Furniture");
        blockMaterialMap.put("CABINET", "Cabinets");
        blockMaterialMap.put("APPLIANCE", "Appliances");
        blockMaterialMap.put("FRIDGE", "Appliances");
        blockMaterialMap.put("STOVE", "Appliances");
        blockMaterialMap.put("OVEN", "Appliances");
        blockMaterialMap.put("DISHWASHER", "Appliances");
        blockMaterialMap.put("SHOWER", "Plumbing Fixtures");
        blockMaterialMap.put("BATH", "Plumbing Fixtures");
        blockMaterialMap.put("BATHTUB", "Plumbing Fixtures");
        blockMaterialMap.put("LIGHT", "Lighting");
        blockMaterialMap.put("FIXTURE", "Lighting");
    }

    public BlockMaterialProvider(Map<String, String> mappings) {
        this();
        if (mappings != null) {
            blockMaterialMap.putAll(mappings);
        }
    }

    public void updateMapping(String blockName, String material) {
        if (blockName != null && material != null) {
            blockMaterialMap.put(blockName, material);
        }
    }

    @Override
    public String getMaterial(String blockName) {
        if (blockName == null || blockName.isEmpty()) {
            return "Unknown Block";
        }

        String upperBlockName = blockName.toUpperCase();
        for (Map.Entry<String, String> entry : blockMaterialMap.entrySet()) {
            if (upperBlockName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return blockName;
    }

    public boolean isKnownBlockType(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        String upperName = name.toUpperCase();
        for (String key : blockMaterialMap.keySet()) {
            if (upperName.contains(key)) {
                return true;
            }
        }

        return false;
    }

}
