package com.constructiontakeoff.util.geometry;

import com.constructiontakeoff.util.dxf.DxfParsingException;
import com.constructiontakeoff.model.LayerInfo;

import java.util.Map;

public interface EntityProcessor {
    
    void process(double[] coordinates, 
                String layer, 
                String blockName,
                Map<String, String> properties, 
                Map<String, LayerInfo> layerInfoMap) throws DxfParsingException;
    
    EntityType getEntityType();
}
