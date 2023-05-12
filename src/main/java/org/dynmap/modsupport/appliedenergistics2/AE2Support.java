package org.dynmap.modsupport.appliedenergistics2;

import org.dynmap.hdmap.TexturePack;

import java.util.HashMap;

public class AE2Support {
    public static CableType[] cableTypes = new CableType[32768];
    public static void addCableType(HashMap<String, Integer> filetoidx, int damage, String texture, int size) {
        cableTypes[damage] = new CableType(size, TexturePack.parseTextureIndex(filetoidx, texture));
    }
    public static class CableType{
        public int size;
        public final int textureId;

        public CableType(int size, int textureId) {

            this.size = size;
            this.textureId = textureId;
        }
    }
}
