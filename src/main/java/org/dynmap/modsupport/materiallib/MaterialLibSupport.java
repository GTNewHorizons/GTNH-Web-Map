package org.dynmap.modsupport.materiallib;

import com.ruling_0.materiallib.api.Material;
import com.ruling_0.materiallib.api.MaterialLibAPI;
import com.ruling_0.materiallib.api.StandardProperties;
import com.ruling_0.materiallib.api.TextureSet;
import gregtech.api.material.MaterialUtils;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.SimpleColorMultiplier;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// The art tables declared by `materiallib-texture.txt`, keyed by shape and texture set and resolved against
/// the live MaterialLib registry. A MaterialLib block carries a material index as its metadata, so its tile
/// and tint are only known once that index maps back to a material.
///
/// Tile choice follows MaterialLib's icon binding: a shape's per-material art, then the material's texture set,
/// then its fallback sets, then the shape's fallback tile. Tint comes from [StandardProperties#BLOCK_TINT]
/// where set and [StandardProperties#TINT] otherwise.
///
/// Only the renderdata file may reach this class, which touches MaterialLib and GregTech types directly. That
/// file stops at its `modname:materiallib` gate when MaterialLib is absent.
public class MaterialLibSupport {

    public static final MaterialLibSupport INSTANCE = new MaterialLibSupport();

    private final Map<String, ShapeArt> shapes = new HashMap<>();
    private final Map<String, Integer> setTiles = new HashMap<>();
    private final Map<String, Integer> materialTiles = new HashMap<>();

    private MaterialLibSupport() {}

    public void processShape(HashMap<String, String> data, HashMap<String, Integer> filetoidx) {
        String name = data.get("shape");
        String fallback = data.get("fallback");

        if (name == null || fallback == null)
            return;

        boolean perMaterialArt = "true".equals(data.get("permaterialart"));
        shapes.put(name, new ShapeArt(name, TexturePack.parseTextureIndex(filetoidx, fallback), perMaterialArt));
    }

    public void processSetArt(HashMap<String, String> data, HashMap<String, Integer> filetoidx) {
        String shape = data.get("shape");
        String set = data.get("set");
        String tex = data.get("tex");

        if (shape == null || set == null || tex == null)
            return;

        setTiles.put(setKey(shape, set), TexturePack.parseTextureIndex(filetoidx, tex));
    }

    public void processBlockArt(HashMap<String, String> data, HashMap<String, Integer> filetoidx) {
        String name = data.get("name");
        String tex = data.get("tex");

        if (name == null || tex == null)
            return;

        materialTiles.put(name.toLowerCase(Locale.ROOT), TexturePack.parseTextureIndex(filetoidx, tex));
    }

    /// The tile and tint the material at `materialIndex` draws on the named shape, or null when the renderdata
    /// declared no such shape.
    public MaterialArt resolve(String shapeName, int materialIndex) {
        ShapeArt shape = shapes.get(shapeName);

        if (shape == null)
            return null;

        Material material = MaterialLibAPI.getMaterialByIndex(materialIndex);

        if (material == null)
            return shape.unresolved;

        return new MaterialArt(tileFor(shape, material), multiplierFor(material));
    }

    private int tileFor(ShapeArt shape, Material material) {
        if (shape.perMaterialArt) {
            String legacyName = MaterialUtils.internalName(material);
            Integer tile = legacyName == null ? null : materialTiles.get(legacyName.toLowerCase(Locale.ROOT));

            if (tile != null)
                return tile;
        }

        Integer tile = setTile(shape, material.getProperty(StandardProperties.TEXTURE_SET));

        if (tile != null)
            return tile;

        List<TextureSet> fallbacks = material.getProperty(StandardProperties.FALLBACK_TEXTURE_SETS);

        if (fallbacks != null) {
            for (TextureSet set : fallbacks) {
                tile = setTile(shape, set);

                if (tile != null)
                    return tile;
            }
        }

        return shape.fallbackTile;
    }

    private Integer setTile(ShapeArt shape, TextureSet set) {
        return set == null ? null : setTiles.get(setKey(shape.name, set.getName()));
    }

    private static String setKey(String shapeName, String setName) {
        return shapeName + "|" + setName;
    }

    private static SimpleColorMultiplier multiplierFor(Material material) {
        Integer tint = material.getProperty(StandardProperties.BLOCK_TINT);

        if (tint == null)
            tint = material.getProperty(StandardProperties.TINT);

        int rgb = tint == null ? 0xFFFFFF : (tint & 0xFFFFFF);

        return rgb == 0xFFFFFF ? null : new SimpleColorMultiplier(rgb);
    }

    /// The tile a material draws on one shape, and the multiplier tinting it. The multiplier is null for an
    /// untinted material.
    public static class MaterialArt {
        public final int tile;
        public final SimpleColorMultiplier multiplier;

        MaterialArt(int tile, SimpleColorMultiplier multiplier) {
            this.tile = tile;
            this.multiplier = multiplier;
        }
    }

    private static class ShapeArt {
        final String name;
        final int fallbackTile;
        final boolean perMaterialArt;
        final MaterialArt unresolved;

        ShapeArt(String name, int fallbackTile, boolean perMaterialArt) {
            this.name = name;
            this.fallbackTile = fallbackTile;
            this.perMaterialArt = perMaterialArt;
            this.unresolved = new MaterialArt(fallbackTile, null);
        }
    }
}
