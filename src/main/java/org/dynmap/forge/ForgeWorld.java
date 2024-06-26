package org.dynmap.forge;
/**
 * Forge specific implementation of DynmapWorld
 */
import java.util.List;

import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldProviderHell;

import org.dynmap.DynmapChunk;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapWorld;
import org.dynmap.utils.MapChunkCache;

public class ForgeWorld extends DynmapWorld
{
    private World world;
    private final boolean skylight;
    private final boolean isnether;
    private final boolean istheend;
    private final String env;
    private DynmapLocation spawnloc = new DynmapLocation();
    private static boolean doMCPCMapping = false;
    private static boolean doSaveFolderMapping = false;
    private static int maxWorldHeight = 256;    // Maximum allows world height
    
    public static void setMCPCMapping() {
        doMCPCMapping = true;
    }
    public static void setSaveFolderMapping() {
        doSaveFolderMapping = true;
    }
    public static int getMaxWorldHeight() {
        return maxWorldHeight;
    }
    public static void setMaxWorldHeight(int h) {
        maxWorldHeight = h;
    }

    public static String getWorldName(World w) {
        String n;
        if (doMCPCMapping) {    // MCPC+ mapping
            n = w.getWorldInfo().getWorldName();
        }
        else if (doSaveFolderMapping) { // New vanilla Forge mapping (consistent with MCPC+)
            if (w.provider.dimensionId == 0) {
                n = w.getWorldInfo().getWorldName();
            }
            else {
                n = "DIM" + w.provider.dimensionId;
            }
        }
        else {  // Legacy mapping
            n = w.getWorldInfo().getWorldName();
            WorldProvider wp = w.provider;
            switch(wp.dimensionId) {
                case 0:
                    break;
                case -1:
                    n += "_nether";
                    break;
                case 1:
                    n += "_the_end";
                    break;
                default:
                    n += "_" + wp.dimensionId;
                    break;
            }
        }
        return n;
    }

    public ForgeWorld(World w)
    {
        this(getWorldName(w), w.getHeight(), 64, w.provider instanceof WorldProviderHell,
        		w.provider instanceof WorldProviderEnd, w.provider.getDimensionName());
        setWorldLoaded(w);
    }
    public ForgeWorld(String name, int height, int sealevel, boolean nether, boolean the_end, String deftitle)
    {
        super(name, (height > maxWorldHeight)?maxWorldHeight:height, sealevel);
        world = null;
        setTitle(deftitle);
        isnether = nether;
        istheend = the_end;
        skylight = !(isnether || istheend);

        if (isnether)
        {
            env = "nether";
        }
        else if (istheend)
        {
            env = "the_end";
        }
        else
        {
            env = "normal";
        }
        
    }
    /* Test if world is nether */
    @Override
    public boolean isNether()
    {
        return isnether;
    }
    public boolean isTheEnd()
    {
        return istheend;
    }
    /* Get world spawn location */
    @Override
    public DynmapLocation getSpawnLocation()
    {
    	if(world != null) {
    		ChunkCoordinates sloc = world.getSpawnPoint();
    		spawnloc.x = sloc.posX;
    		spawnloc.y = sloc.posY;
    		spawnloc.z = sloc.posZ;
    		spawnloc.world = this.getName();
    	}
        return spawnloc;
    }
    /* Get world time */
    @Override
    public long getTime()
    {
    	if(world != null)
    		return world.getWorldTime();
    	else
    		return -1;
    }
    /* World is storming */
    @Override
    public boolean hasStorm()
    {
    	if(world != null)
    		return world.isRaining();
    	else
    		return false;
    }
    /* World is thundering */
    @Override
    public boolean isThundering()
    {
    	if(world != null)
    		return world.isThundering();
    	else
    		return false;
    }
    /* World is loaded */
    @Override
    public boolean isLoaded()
    {
        return (world != null);
    }
    /* Set world to unloaded */
    @Override
    public void setWorldUnloaded() 
    {
    	getSpawnLocation();
    	world = null;
    }
    /* Set world to loaded */
    public void setWorldLoaded(World w) {
    	world = w;
    	// Update lighting table
    	float[] lt = w.provider.lightBrightnessTable;
    	for (int i = 0; i < 16; i++) {
    	    this.setBrightnessTableEntry(i, lt[i]);
    	}
    }
    /* Get light level of block */
    @Override
    public int getLightLevel(int x, int y, int z)
    {
    	if(world != null)
    		return world.getBlockLightValue(x,  y,  z);
    	else
    		return -1;
    }
    /* Get highest Y coord of given location */
    @Override
    public int getHighestBlockYAt(int x, int z)
    {
    	if(world != null)
    		return world.getHeightValue(x,  z);
    	else
    		return -1;
    }
    /* Test if sky light level is requestable */
    @Override
    public boolean canGetSkyLightLevel()
    {
        return skylight;
    }
    /* Return sky light level */
    @Override
    public int getSkyLightLevel(int x, int y, int z)
    {
    	if(world != null)
    		return world.getSavedLightValue(EnumSkyBlock.Sky, x, y, z);
    	else
    		return -1;
    }
    /**
     * Get world environment ID (lower case - normal, the_end, nether)
     */
    @Override
    public String getEnvironment()
    {
        return env;
    }
    /**
     * Get map chunk cache for world
     */
    @Override
    public MapChunkCache getChunkCache(List<DynmapChunk> chunks)
    {
    	if(world != null) {
    		ForgeMapChunkCache c = new ForgeMapChunkCache();
    		c.setChunks(this, chunks);
    		return c;
    	}
    	return null;
    }

    public World getWorld()
    {
        return world;
    }
}
