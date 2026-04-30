package org.dynmap.exporter;

import java.io.IOException;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.hdmap.HDShader;
import org.dynmap.utils.MapChunkCache;

public class BlockModelExporter {
    private final DynmapWorld world;
    private final DynmapCore core;
    private final HDShader shader;
    private boolean cullExportRegionEdges;
    private GLBExport.LightingMode lightingMode = GLBExport.LightingMode.DAY;
    private BlockModelExportMode exportMode = BlockModelExportMode.FULL;
    private int simplifiedMinSkyLight = 7;

    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;

    public BlockModelExporter(DynmapWorld world, DynmapCore core, HDShader shader) {
        this.world = world;
        this.core = core;
        this.shader = shader;
    }

    public void setRenderBounds(int minx, int miny, int minz, int maxx, int maxy, int maxz) {
        if (minx < maxx) {
            minX = minx;
            maxX = maxx;
        } else {
            minX = maxx;
            maxX = minx;
        }
        if (miny < maxy) {
            minY = miny;
            maxY = maxy;
        } else {
            minY = maxy;
            maxY = miny;
        }
        if (minz < maxz) {
            minZ = minz;
            maxZ = maxz;
        } else {
            minZ = maxz;
            maxZ = minz;
        }
        if (minY < 0) {
            minY = 0;
        }
        if (maxY >= world.worldheight) {
            maxY = world.worldheight - 1;
        }
    }

    public void setCullExportRegionEdges(boolean cullExportRegionEdges) {
        this.cullExportRegionEdges = cullExportRegionEdges;
    }

    public void setLightingMode(GLBExport.LightingMode lightingMode) {
        this.lightingMode = (lightingMode == null) ? GLBExport.LightingMode.DAY : lightingMode;
    }

    public void setExportMode(BlockModelExportMode exportMode) {
        this.exportMode = (exportMode == null) ? BlockModelExportMode.FULL : exportMode;
    }

    public void setSimplifiedMinSkyLight(int simplifiedMinSkyLight) {
        this.simplifiedMinSkyLight = Math.max(0, Math.min(15, simplifiedMinSkyLight));
    }

    public void export(BlockModelExportSink sink) throws IOException {
        createModeExporter().export(sink);
    }

    public void export(MapChunkCache cache, BlockModelExportSink sink) throws IOException {
        createModeExporter().export(cache, sink);
    }

    private AbstractBlockModelExporter createModeExporter() {
        AbstractBlockModelExporter exporter;
        switch (exportMode) {
            case SIMPLIFIED:
                exporter = new BlockModelSimplifiedExporter(world, core, shader);
                break;
            case ZOOMOUT:
                exporter = new BlockModelLodExporter(world, core, shader);
                break;
            case FULL:
            default:
                exporter = new BlockModelFullExporter(world, core, shader);
                break;
        }
        copyConfigurationTo(exporter);
        return exporter;
    }

    private void copyConfigurationTo(AbstractBlockModelExporter exporter) {
        exporter.setRenderBounds(minX, minY, minZ, maxX, maxY, maxZ);
        exporter.setCullExportRegionEdges(cullExportRegionEdges);
        exporter.setLightingMode(lightingMode);
        exporter.setSimplifiedMinSkyLight(simplifiedMinSkyLight);
    }
}
