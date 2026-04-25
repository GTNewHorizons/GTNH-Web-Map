package org.dynmap.exporter;

import org.dynmap.renderer.RenderPatchFactory.SideVisible;
import org.dynmap.utils.PatchDefinition;

public final class ExportPatchGeometry {
    public static final class Geometry {
        public final double[] xyz;
        public final double[] uv;
        public final int vertexCount;
        public final SideVisible sideVisible;

        private Geometry(double[] xyz, double[] uv, int vertexCount, SideVisible sideVisible) {
            this.xyz = xyz;
            this.uv = uv;
            this.vertexCount = vertexCount;
            this.sideVisible = sideVisible;
        }
    }

    private ExportPatchGeometry() {
    }

    public static Geometry build(PatchDefinition patch, double x, double y, double z, int rotation) {
        boolean triangle = patch.uplusvmax <= 1.0000001;
        int vertexCount = triangle ? 3 : 4;
        double[] xyz = new double[vertexCount * 3];
        double[] uv = new double[vertexCount * 2];

        double ux = patch.xu - patch.x0;
        double uy = patch.yu - patch.y0;
        double uz = patch.zu - patch.z0;
        double vx = patch.xv - patch.x0;
        double vy = patch.yv - patch.y0;
        double vz = patch.zv - patch.z0;

        x = x + patch.x0;
        y = y + patch.y0;
        z = z + patch.z0;

        double[] patchU = triangle ? new double[] { patch.umin, patch.umax, patch.umin }
                : new double[] { patch.umin, patch.umax, patch.umax, patch.umin };
        double[] patchV = triangle ? new double[] { patch.vmin, patch.vmin, patch.vmax }
                : new double[] { patch.vmin, patch.vmin, patch.vmax, patch.vmax };

        for (int i = 0; i < vertexCount; i++) {
            double u = patchU[i];
            double v = patchV[i];
            fillVertex(xyz, i * 3, x + ux * u + vx * v, y + uy * u + vy * v, z + uz * u + vz * v);
            setUV(uv, i, rotateUV(getTextureU(patch, u, v), getTextureV(patch, u, v), rotation));
        }

        return new Geometry(xyz, uv, vertexCount, patch.sidevis);
    }

    private static void fillVertex(double[] xyz, int offset, double x, double y, double z) {
        xyz[offset] = x;
        xyz[offset + 1] = y;
        xyz[offset + 2] = z;
    }

    private static double getTextureU(PatchDefinition patch, double u, double v) {
        if (patch.explicitTexCoords != null) {
            return normalizeUV(
                    u * patch.explicitTexCoords[2] + v * patch.explicitTexCoords[4] + (1 - u - v) * patch.explicitTexCoords[0]);
        }
        return u;
    }

    private static double getTextureV(PatchDefinition patch, double u, double v) {
        if (patch.explicitTexCoords != null) {
            return normalizeUV(
                    u * patch.explicitTexCoords[3] + v * patch.explicitTexCoords[5] + (1 - u - v) * patch.explicitTexCoords[1]);
        }
        return v;
    }

    private static double normalizeUV(double value) {
        while (value > 1.0) {
            value -= 1.0;
        }
        while (value < 0.0) {
            value += 1.0;
        }
        return value;
    }

    private static double[] rotateUV(double u, double v, int rotation) {
        if (rotation == 1) {
            return new double[] { 1.0 - v, u };
        } else if (rotation == 2) {
            return new double[] { 1.0 - u, 1.0 - v };
        } else if (rotation == 3) {
            return new double[] { v, 1.0 - u };
        } else if (rotation == 4) {
            return new double[] { 1.0 - u, v };
        }
        return new double[] { u, v };
    }

    private static void setUV(double[] uv, int vertexIndex, double[] rotated) {
        uv[vertexIndex * 2] = rotated[0];
        uv[vertexIndex * 2 + 1] = rotated[1];
    }
}
