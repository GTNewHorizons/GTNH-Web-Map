package org.dynmap.modsupport;

import org.dynmap.renderer.MapDataContext;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;

public class BlockMatcher {
    BitSet simpleBlocks = new BitSet();
    HashMap<Integer, ArrayList<AdvancedCondition>> blocksWithAdvancedConditions = new HashMap<>();

    public void addBlock(int blockId){
        int blockIdStart = blockId << 4;
        simpleBlocks.set(blockIdStart, blockIdStart+16, true);
    }
    public void addBlockSingleMeta(int blockId, int meta){
        int blockIdStart = blockId << 4;
        simpleBlocks.set(blockIdStart + meta);
    }
    public void addBlockMetaMask(int blockId, int metaMask){
        int blockIdStart = blockId << 4;
        for(int i = 0; i < 16; i++)
            if((metaMask & i) != 0)
                simpleBlocks.set(blockIdStart + i);
    }

    public void addBlockWithCondition(int blockId, int meta, AdvancedCondition advancedCondition) {
        int blockIdStart = blockId << 4;
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
        int blockMeta = mdc.getBlockData() & 0x0F;
        int combined = (blockId << 4) | blockMeta;

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
