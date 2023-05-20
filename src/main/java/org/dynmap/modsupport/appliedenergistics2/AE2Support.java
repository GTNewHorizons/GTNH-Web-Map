package org.dynmap.modsupport.appliedenergistics2;

import org.dynmap.hdmap.TexturePack;

import java.util.HashMap;

public class AE2Support {
    public static CableType[] cableTypes = new CableType[32768];
    public static void addCableType(HashMap<String, Integer> filetoidx, int damage, String texture, String texture2, String texture3, int size) {
        cableTypes[damage] = new CableType(size, TexturePack.parseTextureIndex(filetoidx, texture), TexturePack.parseTextureIndex(filetoidx, texture2), TexturePack.parseTextureIndex(filetoidx, texture3));
    }
    public static class CableType{
        public int size;
        public final int textureId;
        public final int textureId2;
        public final int textureId3;

        public CableType(int size, int textureId, int textureId2, int textureId3) {

            this.size = size;
            this.textureId = textureId;
            this.textureId2 = textureId2;
            this.textureId3 = textureId3;
        }
    }
}
