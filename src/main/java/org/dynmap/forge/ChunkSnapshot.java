package org.dynmap.forge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.dynmap.Log;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/**
 * Represents a static, thread-safe snapshot of chunk of blocks
 * Purpose is to allow clean, efficient copy of a chunk data to be made, and then handed off for processing in another thread (e.g. map rendering)
 */
public class ChunkSnapshot
{
    private final int x, z;
    private final int[][] blockids; /* Block IDs, by section */
    private final short[][] blockdata16;
    private final byte[][] skylight;
    private final byte[][] emitlight;
    private final boolean[] empty;
    private final int[] hmap; // Height map
    private final short[] biome;
    private final long captureFulltime;
    private final int sectionCnt;
    private final long inhabitedTicks;

    private static final int BLOCKS_PER_SECTION = 16 * 16 * 16;
    private static final int COLUMNS_PER_CHUNK = 16 * 16;
    private static final int[] emptyIDs = new int[BLOCKS_PER_SECTION];
    private static final short[] emptyData = new short[BLOCKS_PER_SECTION];
    private static final byte[] fullHalfByteData = new byte[BLOCKS_PER_SECTION / 2];
    private static final byte[] emptyHalfByteData = new byte[BLOCKS_PER_SECTION / 2];
    private static Method getvalarray = null;

    static
    {
        for (int i = 0; i < fullHalfByteData.length; i++)
        {
            fullHalfByteData[i] = (byte)0xFF;
        }
        try {
            Method[] m = NibbleArray.class.getDeclaredMethods();
            for (Method mm : m) {
                if (mm.getName().equals("getValueArray")) {
                    getvalarray = mm;
                    break;
                }
            }
        } catch (Exception x) {
        }
    }

    /**
     * Construct empty chunk snapshot
     *
     * @param x
     * @param z
     */
    public ChunkSnapshot(int worldheight, int x, int z, long captime, long inhabitedTime)
    {
        this.x = x;
        this.z = z;
        this.captureFulltime = captime;
        this.biome = new short[COLUMNS_PER_CHUNK];
        this.sectionCnt = worldheight / 16;
        /* Allocate arrays indexed by section */
        this.blockids = new int[this.sectionCnt][];
        this.blockdata16 = new short[this.sectionCnt][];
        this.skylight = new byte[this.sectionCnt][];
        this.emitlight = new byte[this.sectionCnt][];
        this.empty = new boolean[this.sectionCnt];

        /* Fill with empty data */
        for (int i = 0; i < this.sectionCnt; i++)
        {
            this.empty[i] = true;
            this.blockids[i] = emptyIDs;
            this.blockdata16[i] = emptyData;
            this.emitlight[i] = emptyHalfByteData;
            this.skylight[i] = fullHalfByteData;
        }

        /* Create empty height map */
        this.hmap = new int[16 * 16];
        
        this.inhabitedTicks = inhabitedTime;
    }

    public ChunkSnapshot(NBTTagCompound nbt, int worldheight) {

        this.x = nbt.getInteger("xPos");
        this.z = nbt.getInteger("zPos");
        this.captureFulltime = 0;
        this.hmap = nbt.getIntArray("HeightMap");
        this.sectionCnt = worldheight / 16;
        if (nbt.hasKey("InhabitedTime")) {
            this.inhabitedTicks = nbt.getLong("InhabitedTime");
        } else {
            this.inhabitedTicks = 0;
        }
        /* Allocate arrays indexed by section */
        this.blockids = new int[this.sectionCnt][];
        this.blockdata16 = new short[this.sectionCnt][];
        this.skylight = new byte[this.sectionCnt][];
        this.emitlight = new byte[this.sectionCnt][];
        this.empty = new boolean[this.sectionCnt];
        /* Fill with empty data */
        for (int i = 0; i < this.sectionCnt; i++) {
            this.empty[i] = true;
            this.blockids[i] = emptyIDs;
            this.blockdata16[i] = emptyData;
            this.emitlight[i] = emptyHalfByteData;
            this.skylight[i] = fullHalfByteData;
        }
        /* Get sections */
        NBTTagList sect = nbt.getTagList("Sections", 10);
        for (int i = 0; i < sect.tagCount(); i++) {
            NBTTagCompound sec = sect.getCompoundTagAt(i);
            byte secnum = sec.getByte("Y");
            if (secnum >= this.sectionCnt) {
                Log.info("Section " + (int) secnum + " above world height " + worldheight);
                continue;
            }
            int[] blkids = new int[BLOCKS_PER_SECTION];
            this.blockids[secnum] = blkids;
            int len = BLOCKS_PER_SECTION;

            byte[] blocks16 = sec.getByteArray("Blocks16");

            if (blocks16 != null && blocks16.length > 0) {
                for (int b = 0; b < blkids.length; b++) {
                    blkids[b] = (((0xFF & blocks16[2 * b]) << 8) | (0xFF & blocks16[2 * b + 1]));
                }
            } else {
                byte[] lsb_bytes = sec.getByteArray("Blocks");
                if (len > lsb_bytes.length) len = lsb_bytes.length;
                for (int j = 0; j < len; j++) {
                    blkids[j] = (short) (0xFF & lsb_bytes[j]);
                }
                if (sec.hasKey("Add")) {    /* If additional data, add it */
                    byte[] msb = sec.getByteArray("Add");
                    len = BLOCKS_PER_SECTION / 2;
                    if (len > msb.length) len = msb.length;
                    for (int j = 0; j < len; j++) {
                        short b = (short) (msb[j] & 0xFF);
                        if (b == 0) {
                            continue;
                        }
                        blkids[j << 1] |= (b & 0x0F) << 8;
                        blkids[(j << 1) + 1] |= (b & 0xF0) << 4;
                    }
                }
                if (sec.hasKey("BlocksB2Hi")) {
                    byte[] msb = sec.getByteArray("BlocksB2Hi");
                    len = BLOCKS_PER_SECTION / 2;
                    if (len > msb.length) len = msb.length;
                    for (int j = 0; j < len; j++) {
                        short b = (short) (msb[j] & 0xFF);
                        if (b == 0) {
                            continue;
                        }
                        blkids[j << 1] |= (b & 0x0F) << 12;
                        blkids[(j << 1) + 1] |= (b & 0xF0) << 8;
                    }
                }
                if (sec.hasKey("BlocksB3")) {
                    byte[] msb = sec.getByteArray("BlocksB3");
                    len = BLOCKS_PER_SECTION;
                    if (len > msb.length) len = msb.length;
                    for (int j = 0; j < len; j++)
                        blkids[j] |= (msb[j] & 0xFF) << 16;
                }
            }

            byte[] data16 = sec.getByteArray("Data16");
            if (data16 != null && data16.length > 0) {
                short[] blkdata = new short[BLOCKS_PER_SECTION];
                for (int b = 0; b < blkdata.length; b++) {
                    blkdata[b] = (short) (((0xFF & data16[2 * b]) << 8) | (0xFF & data16[2 * b + 1]));
                }
                blockdata16[secnum] = blkdata;
            } else if (sec.hasKey("Data")) {
                byte[] data = sec.getByteArray("Data");
                short[] blkdata = new short[BLOCKS_PER_SECTION];
                len = BLOCKS_PER_SECTION / 2;
                if (len > data.length) len = data.length;
                if (data != null) {
                    for (int b = 0; b < len; b++) {
                        blkdata[b << 1] = (short) (data[b] & 0xF);
                        blkdata[(b << 1) + 1] = (short) (((data[b] & 0xF0) >> 4) & 0xF);
                    }
                    blockdata16[secnum] = blkdata;
                }

                if (sec.hasKey("Data1High")) {
                    byte[] msb = sec.getByteArray("Data1High");
                    len = BLOCKS_PER_SECTION / 2;
                    if (len > msb.length) len = msb.length;
                    for (int j = 0; j < len; j++) {
                        short b = (short) (msb[j] & 0xFF);
                        if (b == 0) {
                            continue;
                        }
                        blkdata[j << 1] |= (short) ((b & 0x0F) << 4);
                        blkdata[(j << 1) + 1] |= (short) (b & 0xF0);
                    }
                }
                if (sec.hasKey("Data2")) {
                    byte[] msb = sec.getByteArray("Data2");
                    len = BLOCKS_PER_SECTION;
                    if (len > msb.length) len = msb.length;
                    for (int j = 0; j < len; j++) {
                        short b = (short) (msb[j] & 0xFF);
                        blkdata[j] = (short) (blkdata[j] | (b << 8));
                    }
                }

            }

            this.emitlight[secnum] = sec.getByteArray("BlockLight");
            if (sec.hasKey("SkyLight")) {
                this.skylight[secnum] = sec.getByteArray("SkyLight");
            }
            this.empty[secnum] = false;
        }
        /* Get biome data */
        if (nbt.hasKey("Biomes")) {
            byte[] biomes8 = nbt.getByteArray("Biomes");
            short[] biomes16 = new short[biomes8.length];
            if (biomes8 != null && biomes8.length > 0) {
                for (int i = 0; i < biomes8.length; i++) {
                    biomes16[i] = (short) (biomes8[i] & 0xFF);
                }
            }
            this.biome = biomes16;
        } else if (nbt.hasKey("Biomes16v2")) {
            byte[] biomes = nbt.getByteArray("Biomes16v2");
            short[] biomes16 = new short[COLUMNS_PER_CHUNK];
            for (int i = 0; i < biomes16.length; i++) {
                biomes16[i] = (short) ((biomes[i << 1] & 0xFF) | (biomes[(i << 1) + 1] & 0xFF) << 8);
            }
            this.biome = biomes16;
        } else {
            this.biome = new short[COLUMNS_PER_CHUNK];
        }
    }
    
    private static byte[] getValueArray(NibbleArray na) {
        if(getvalarray != null) {
            try {
                return (byte[])getvalarray.invoke(na);
            } catch (IllegalArgumentException e) {
            } catch (IllegalAccessException e) {
            } catch (InvocationTargetException e) {
            }
        }
        return na.data;
    }

    public int getX()
    {
        return x;
    }

    public int getZ()
    {
        return z;
    }

    public int getBlockTypeId(int x, int y, int z)
    {
        if(y > 255)
            return 0;

        return blockids[y >> 4][((y & 0xF) << 8) | (z << 4) | x];
    }

    public int getBlockData(int x, int y, int z)
    {
        if (y > 255)
            return 0;

        int data = (blockdata16[y >> 4][((y & 0xF) << 8) | (z << 4) | x]) & 0xF;
        return data;
    }

    public int getBlockSkyLight(int x, int y, int z)
    {
        if(y > 255)
            return 15;

        int off = ((y & 0xF) << 7) | (z << 3) | (x >> 1);
        return (skylight[y >> 4][off] >> ((x & 1) << 2)) & 0xF;
    }

    public int getBlockEmittedLight(int x, int y, int z)
    {
        if(y > 255)
            return 0;

        int off = ((y & 0xF) << 7) | (z << 3) | (x >> 1);
        return (emitlight[y >> 4][off] >> ((x & 1) << 2)) & 0xF;
    }

    public int getHighestBlockYAt(int x, int z)
    {
        return hmap[z << 4 | x];
    }

    public int getBiome(int x, int z)
    {
        return 255 & biome[z << 4 | x];
    }

    public final long getCaptureFullTime()
    {
        return captureFulltime;
    }

    public boolean isSectionEmpty(int sy)
    {
        return empty[sy];
    }
    
    public long getInhabitedTicks() {
        return inhabitedTicks;
    }

    public int getBlockDataFull(int bx, int y, int bz) {
        if(y > 255)
            return 0;

        int tmp = blockdata16[y >> 4][((y & 0xF) << 8) | (bz << 4) | bx];

        if(tmp < 0)
            tmp += 0x10000;

        return tmp;

    }
}
