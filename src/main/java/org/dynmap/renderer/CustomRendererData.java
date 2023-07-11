package org.dynmap.renderer;

public class CustomRendererData {
    RenderPatch[] mesh;
    CustomColorMultiplier custColorMult;

    CustomTextureMapper custTextMapper;
    public CustomRendererData(RenderPatch[] mesh, CustomColorMultiplier custColorMult, CustomTextureMapper custTextMapper) {
        this.mesh = mesh;
        this.custColorMult = custColorMult;
        this.custTextMapper = custTextMapper;
    }


    public RenderPatch[] getCustomMesh(){
        return mesh;
    }
    public CustomTextureMapper getCustomTextureMapper(){
        return custTextMapper;
    }

    public CustomColorMultiplier getCustomColorMultiplier(int patchId) {
        return custColorMult;
    }
}
