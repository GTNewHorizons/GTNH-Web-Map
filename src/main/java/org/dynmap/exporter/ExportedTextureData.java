package org.dynmap.exporter;

import org.dynmap.Color;
import org.dynmap.hdmap.TexturePack.MaterialType;

public final class ExportedTextureData {
    public byte[] imagePng;
    public byte[] alphaPng;
    public Color diffuseColor;
    public MaterialType material;
    public boolean hasAlpha;
    public boolean hasTranslucentAlpha;
}
