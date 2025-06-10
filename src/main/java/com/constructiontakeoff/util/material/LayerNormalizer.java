package com.constructiontakeoff.util.material;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;

public class LayerNormalizer {
    private static final double SIMILARITY_THRESHOLD = 0.85;

    private static final Map<String, String> PREFIX_MAP = new HashMap<>();

    private static final Pattern WALL_PATTERN = Pattern
            .compile("(?i).*(WALL|PARTITION|WAND|MUR|PARED|MURO|W-|W_|WA-|WA_).*");
    private static final Pattern COLUMN_PATTERN = Pattern
            .compile("(?i).*(COL|COLUMN|PILLAR|STÜTZE|COLONNE|COLUMNA|PILAR|C-|C_|CO-|CO_).*");
    private static final Pattern BEAM_PATTERN = Pattern
            .compile("(?i).*(BEAM|GIRDER|TRÄGER|POUTRE|VIGA|TRAVE|B-|B_|BM-|BM_).*");
    private static final Pattern SLAB_PATTERN = Pattern
            .compile("(?i).*(SLAB|FLOOR|DECKE|BODEN|DALLE|PLANCHER|LOSA|PISO|PLACA|FL-|FL_|SL-|SL_).*");
    private static final Pattern ROOF_PATTERN = Pattern
            .compile("(?i).*(ROOF|CEILING|DACH|TOIT|TECHO|CUBIERTA|SOFFITTO|RF-|RF_|CE-|CE_).*");
    private static final Pattern FOUNDATION_PATTERN = Pattern
            .compile("(?i).*(FOUND|FOUNDATION|FUNDAMENT|FONDATION|CIMIENTO|FND-|FND_|FDN).*");
    private static final Pattern STAIR_PATTERN = Pattern
            .compile("(?i).*(STAIR|STEP|TREPPE|ESCALIER|ESCALERA|ST-|ST_|STR-|STR_).*");
    private static final Pattern RAILING_PATTERN = Pattern
            .compile("(?i).*(RAIL|RAILING|GELÄNDER|GARDE-CORPS|BARANDILLA|RL-|RL_).*");
    private static final Pattern DOOR_PATTERN = Pattern.compile("(?i).*(DOOR|TÜR|PORTE|PUERTA|PORTA|D-|D_|DR-|DR_).*");
    private static final Pattern WINDOW_PATTERN = Pattern
            .compile("(?i).*(WINDOW|FENSTER|FENÊTRE|VENTANA|FINESTRA|W-|W_|WN-|WN_).*");
    private static final Pattern FURNITURE_PATTERN = Pattern
            .compile("(?i).*(FURN|FURNITURE|MÖBEL|MEUBLE|MUEBLE|MOBILI|FFE|FF&E|F-|F_).*");
    private static final Pattern PLUMBING_PATTERN = Pattern
            .compile("(?i).*(PLUMB|PLUMBING|SANITÄR|PLOMBERIE|FONTANERÍA|P-|P_|PL-|PL_).*");
    private static final Pattern ELECTRICAL_PATTERN = Pattern
            .compile("(?i).*(ELEC|ELECTRICAL|ELEKTRO|ÉLECTRIQUE|ELÉCTRICO|E-|E_|EL-|EL_).*");
    private static final Pattern HVAC_PATTERN = Pattern
            .compile("(?i).*(HVAC|MECHANICAL|KLIMA|CVC|CLIMATIZACIÓN|H-|H_|MH-|MH_|MC-|MC_).*");

    private static final Map<String, Pattern> CATEGORY_PATTERNS = new HashMap<>();

    private static final Map<String, String> NUMERIC_LAYER_MAP = new HashMap<>();

    private final Map<String, String> normalizedNameCache = new HashMap<>();

    private final JaroWinklerSimilarity similarityCalculator = new JaroWinklerSimilarity();

    static {
        PREFIX_MAP.put("A-", "ARCH-");
        PREFIX_MAP.put("A_", "ARCH-");
        PREFIX_MAP.put("AR-", "ARCH-");
        PREFIX_MAP.put("AR_", "ARCH-");
        PREFIX_MAP.put("ARCH-", "ARCH-");
        PREFIX_MAP.put("ARCH_", "ARCH-");
        PREFIX_MAP.put("S-", "STRUCT-");
        PREFIX_MAP.put("S_", "STRUCT-");
        PREFIX_MAP.put("ST-", "STRUCT-");
        PREFIX_MAP.put("ST_", "STRUCT-");
        PREFIX_MAP.put("STR-", "STRUCT-");
        PREFIX_MAP.put("STR_", "STRUCT-");
        PREFIX_MAP.put("STRUCT-", "STRUCT-");
        PREFIX_MAP.put("STRUCT_", "STRUCT-");
        PREFIX_MAP.put("M-", "MECH-");
        PREFIX_MAP.put("M_", "MECH-");
        PREFIX_MAP.put("ME-", "MECH-");
        PREFIX_MAP.put("ME_", "MECH-");
        PREFIX_MAP.put("MECH-", "MECH-");
        PREFIX_MAP.put("MECH_", "MECH-");
        PREFIX_MAP.put("H-", "HVAC-");
        PREFIX_MAP.put("H_", "HVAC-");
        PREFIX_MAP.put("HV-", "HVAC-");
        PREFIX_MAP.put("HV_", "HVAC-");
        PREFIX_MAP.put("HVAC-", "HVAC-");
        PREFIX_MAP.put("HVAC_", "HVAC-");
        PREFIX_MAP.put("E-", "ELEC-");
        PREFIX_MAP.put("E_", "ELEC-");
        PREFIX_MAP.put("EL-", "ELEC-");
        PREFIX_MAP.put("EL_", "ELEC-");
        PREFIX_MAP.put("ELEC-", "ELEC-");
        PREFIX_MAP.put("ELEC_", "ELEC-");
        PREFIX_MAP.put("P-", "PLUMB-");
        PREFIX_MAP.put("P_", "PLUMB-");
        PREFIX_MAP.put("PL-", "PLUMB-");
        PREFIX_MAP.put("PL_", "PLUMB-");
        PREFIX_MAP.put("PLUMB-", "PLUMB-");
        PREFIX_MAP.put("PLUMB_", "PLUMB-");
        PREFIX_MAP.put("F-", "FIRE-");
        PREFIX_MAP.put("F_", "FIRE-");
        PREFIX_MAP.put("FP-", "FIRE-");
        PREFIX_MAP.put("FP_", "FIRE-");
        PREFIX_MAP.put("FIRE-", "FIRE-");
        PREFIX_MAP.put("FIRE_", "FIRE-");
        PREFIX_MAP.put("C-", "CIVIL-");
        PREFIX_MAP.put("C_", "CIVIL-");
        PREFIX_MAP.put("CV-", "CIVIL-");
        PREFIX_MAP.put("CV_", "CIVIL-");
        PREFIX_MAP.put("CIVIL-", "CIVIL-");
        PREFIX_MAP.put("CIVIL_", "CIVIL-");
        PREFIX_MAP.put("L-", "LANDSCAPE-");
        PREFIX_MAP.put("L_", "LANDSCAPE-");
        PREFIX_MAP.put("LA-", "LANDSCAPE-");
        PREFIX_MAP.put("LA_", "LANDSCAPE-");
        PREFIX_MAP.put("LAND-", "LANDSCAPE-");
        PREFIX_MAP.put("LAND_", "LANDSCAPE-");
        PREFIX_MAP.put("LANDSCAPE-", "LANDSCAPE-");
        PREFIX_MAP.put("LANDSCAPE_", "LANDSCAPE-");
        PREFIX_MAP.put("I-", "INTERIOR-");
        PREFIX_MAP.put("I_", "INTERIOR-");
        PREFIX_MAP.put("ID-", "INTERIOR-");
        PREFIX_MAP.put("ID_", "INTERIOR-");
        PREFIX_MAP.put("INT-", "INTERIOR-");
        PREFIX_MAP.put("INT_", "INTERIOR-");
        PREFIX_MAP.put("INTERIOR-", "INTERIOR-");
        PREFIX_MAP.put("INTERIOR_", "INTERIOR-");

        CATEGORY_PATTERNS.put("WALL", WALL_PATTERN);
        CATEGORY_PATTERNS.put("COLUMN", COLUMN_PATTERN);
        CATEGORY_PATTERNS.put("BEAM", BEAM_PATTERN);
        CATEGORY_PATTERNS.put("SLAB", SLAB_PATTERN);
        CATEGORY_PATTERNS.put("ROOF", ROOF_PATTERN);
        CATEGORY_PATTERNS.put("FOUNDATION", FOUNDATION_PATTERN);
        CATEGORY_PATTERNS.put("STAIR", STAIR_PATTERN);
        CATEGORY_PATTERNS.put("RAILING", RAILING_PATTERN);
        CATEGORY_PATTERNS.put("DOOR", DOOR_PATTERN);
        CATEGORY_PATTERNS.put("WINDOW", WINDOW_PATTERN);
        CATEGORY_PATTERNS.put("FURNITURE", FURNITURE_PATTERN);
        CATEGORY_PATTERNS.put("PLUMBING", PLUMBING_PATTERN);
        CATEGORY_PATTERNS.put("ELECTRICAL", ELECTRICAL_PATTERN);
        CATEGORY_PATTERNS.put("HVAC", HVAC_PATTERN);

        NUMERIC_LAYER_MAP.put("A-WALL", "A-2000");
        NUMERIC_LAYER_MAP.put("A-WALL-FULL", "A-2010");
        NUMERIC_LAYER_MAP.put("A-WALL-PRHT", "A-2020");
        NUMERIC_LAYER_MAP.put("A-WALL-MOVE", "A-2030");
        NUMERIC_LAYER_MAP.put("A-DOOR", "A-2200");
        NUMERIC_LAYER_MAP.put("A-GLAZ", "A-2300");
        NUMERIC_LAYER_MAP.put("A-FLOR", "A-2400");
        NUMERIC_LAYER_MAP.put("A-CEIL", "A-2500");
        NUMERIC_LAYER_MAP.put("A-ROOF", "A-2600");
        NUMERIC_LAYER_MAP.put("A-STRS", "A-2700");
        NUMERIC_LAYER_MAP.put("S-COLS", "S-3000");
        NUMERIC_LAYER_MAP.put("S-BEAM", "S-3100");
        NUMERIC_LAYER_MAP.put("S-FNDN", "S-3200");
        NUMERIC_LAYER_MAP.put("S-SLAB", "S-3300");
        NUMERIC_LAYER_MAP.put("S-WALL", "S-3400");
    }

    public String normalize(String originalLayerName) {
        if (originalLayerName == null || originalLayerName.isEmpty()) {
            return "UNKNOWN";
        }

        if (normalizedNameCache.containsKey(originalLayerName)) {
            return normalizedNameCache.get(originalLayerName);
        }

        String normalized = originalLayerName.toUpperCase().trim();

        for (Map.Entry<String, String> entry : NUMERIC_LAYER_MAP.entrySet()) {
            if (normalized.equals(entry.getValue())) {
                normalized = entry.getKey();
                normalizedNameCache.put(originalLayerName, normalized);
                return normalized;
            }
        }

        for (Map.Entry<String, String> entry : PREFIX_MAP.entrySet()) {
            if (normalized.startsWith(entry.getKey())) {
                normalized = normalized.replaceFirst(Pattern.quote(entry.getKey()), entry.getValue());
                break;
            }
        }

        if (normalized.contains(" - ")) {
            String[] parts = normalized.split(" - ", 2);
            if (parts.length == 2) {
                String category = mapCommonTermToCategory(parts[0]);
                if (category != null) {
                    normalized = "ARCH-" + category + "-" + parts[1].replace(' ', '_');
                }
            }
        }

        if (!containsCategory(normalized)) {
            String category = identifyCategory(normalized);
            if (category != null) {
                normalized = normalized + "-" + category;
            }
        }

        normalizedNameCache.put(originalLayerName, normalized);
        return normalized;
    }

    private String mapCommonTermToCategory(String term) {
        term = term.toUpperCase().trim();

        if (term.equals("WALLS") || term.equals("WALL"))
            return "WALL";
        if (term.equals("COLUMNS") || term.equals("COLUMN"))
            return "COLUMN";
        if (term.equals("BEAMS") || term.equals("BEAM"))
            return "BEAM";
        if (term.equals("FLOORS") || term.equals("FLOOR") || term.equals("SLABS") || term.equals("SLAB"))
            return "SLAB";
        if (term.equals("CEILINGS") || term.equals("CEILING"))
            return "CEILING";
        if (term.equals("ROOFS") || term.equals("ROOF"))
            return "ROOF";
        if (term.equals("DOORS") || term.equals("DOOR"))
            return "DOOR";
        if (term.equals("WINDOWS") || term.equals("WINDOW"))
            return "WINDOW";
        if (term.equals("STAIRS") || term.equals("STAIR"))
            return "STAIR";
        if (term.equals("RAILINGS") || term.equals("RAILING"))
            return "RAILING";
        if (term.equals("FURNITURE"))
            return "FURNITURE";
        if (term.equals("PLUMBING") || term.equals("FIXTURES"))
            return "PLUMBING";
        if (term.equals("ELECTRICAL"))
            return "ELECTRICAL";
        if (term.equals("HVAC") || term.equals("MECHANICAL"))
            return "HVAC";

        return null;
    }

    private boolean containsCategory(String layerName) {
        for (String category : CATEGORY_PATTERNS.keySet()) {
            if (layerName.contains(category)) {
                return true;
            }
        }
        return false;
    }

    private String identifyCategory(String layerName) {
        for (Map.Entry<String, Pattern> entry : CATEGORY_PATTERNS.entrySet()) {
            Matcher matcher = entry.getValue().matcher(layerName);
            if (matcher.matches()) {
                return entry.getKey();
            }
        }
        return null;
    }

    public boolean areSimilarLayers(String layer1, String layer2) {
        if (layer1 == null || layer2 == null) {
            return false;
        }

        String norm1 = normalize(layer1);
        String norm2 = normalize(layer2);

        if (norm1.equals(norm2)) {
            return true;
        }

        double similarity = similarityCalculator.apply(norm1, norm2);
        return similarity >= SIMILARITY_THRESHOLD;
    }

    public String getLayerCategory(String layerName) {
        String normalized = normalize(layerName);

        for (String category : CATEGORY_PATTERNS.keySet()) {
            if (normalized.contains(category)) {
                return category;
            }
        }

        return identifyCategory(normalized);
    }
}
