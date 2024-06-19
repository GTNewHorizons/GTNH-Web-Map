package org.dynmap.modsupport;

import org.dynmap.renderer.MapDataContext;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;

public class BlockMatcher {
    BitSet simpleBlocks = new BitSet();
    HashMap<Integer, ArrayList<AdvancedCondition>> blocksWithAdvancedConditions = new HashMap<>();

    public void addBlock(int blockId){
        int blockIdStart = getBlockIdStart(blockId);
        simpleBlocks.set(blockIdStart, blockIdStart+16, true);
    }

    public void addBlockSingleMeta(int blockId, int meta){
        int blockIdStart = getBlockIdStart(blockId);
        simpleBlocks.set(blockIdStart + meta);
    }

    public void addBlockMetaMask(int blockId, int metaMask){
        int blockIdStart = getBlockIdStart(blockId);
        for(int i = 0; i < 16; i++)
            if((metaMask & metaToMetaMask(i)) != 0)
                simpleBlocks.set(blockIdStart + i);
    }

    public void addBlockWithCondition(int blockId, int meta, AdvancedCondition advancedCondition) {
        int blockIdStart = getBlockIdStart(blockId);
        int combined = blockIdStart | meta;
        ArrayList<AdvancedCondition> list = blocksWithAdvancedConditions.get(combined);
        if(list == null) {
            list = new ArrayList<>();
            blocksWithAdvancedConditions.put(combined, list);
        }
        list.add(advancedCondition);
    }

    public boolean matches(MapDataContext mdc){
        int blockId = mdc.getBlockTypeID();
        int blockIdStart = getBlockIdStart(blockId);
        int blockMeta = mdc.getBlockData() & 0x0F;
        int combined = blockIdStart | metaToMetaMask(blockMeta);

        if(simpleBlocks.get(combined))
            return true;

        ArrayList<AdvancedCondition> advanced = blocksWithAdvancedConditions.get(combined);
        if(advanced != null){
            for(AdvancedCondition ac : advanced)
                if(ac.matches(mdc))
                    return true;
        }

        return false;
    }

    private static int metaToMetaMask(int blockMeta) {
        return 1 << blockMeta;
    }

    private static int getBlockIdStart(int blockId) {
        return blockId << 16;
    }

    public static class AdvancedConditionParser {
        public AdvancedCondition parseExpression(String expr) {
            return new AdvancedCondition();
        }
    }

    public static class AdvancedCondition {
        public boolean matches(MapDataContext mdc) {
            return true;
        }
    }
}
