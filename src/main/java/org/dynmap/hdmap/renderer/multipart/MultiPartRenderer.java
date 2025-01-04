package org.dynmap.hdmap.renderer.multipart;

import org.dynmap.renderer.CustomRendererData;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.utils.PatchDefinitionFactory;

import java.util.HashMap;

public interface MultiPartRenderer {

    CustomRendererData getRenderData(MapDataContext ctx, HashMap<String, Object> partData);

    void initialize(RenderPatchFactory rpf, HashMap<String, Integer> filetoidx, HashMap<String, String> data);
}
