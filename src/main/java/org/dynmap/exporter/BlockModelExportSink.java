package org.dynmap.exporter;

import java.io.IOException;

import org.dynmap.utils.PatchDefinition;

public interface BlockModelExportSink {
    void setChunk(int chunkX, int chunkZ) throws IOException;

    void setBlock(int blockId, int blockData) throws IOException;

    void addPatch(PatchDefinition patch, double x, double y, double z, ExportMaterial material, float[] vertexColors,
            float[] nightVertexLights) throws IOException;

    void addQuad(double[] xyz, ExportMaterial material, float[] vertexColors, float[] nightVertexLights) throws IOException;
}
