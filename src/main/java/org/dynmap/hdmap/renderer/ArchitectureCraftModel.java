/* The code in this file is more or less a direct copy of code from ArchitectureCraft
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 gcewing
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
*/

package org.dynmap.hdmap.renderer;

import com.google.gson.Gson;
import net.minecraft.util.ResourceLocation;
import org.dynmap.Log;

import java.io.InputStream;
import java.io.InputStreamReader;

public class ArchitectureCraftModel {
    public double[] bounds;
    public ArchitectureCraftModel.Face[] faces;
    public double[][] boxes;

    public static class Face {
        public int texture;
        public double[][] vertices;
        public int[][] triangles;

        Vector3 normal;
    }
    public class Vector3 {
        double x;
        double y;
        double z;

        public Vector3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        public String toString() {
            return String.format("(%.3f,%.3f,%.3f)", this.x, this.y, this.z);
        }
    }
    static Gson gson = new Gson();

    public static ArchitectureCraftModel fromResource(ResourceLocation location) {
        // Can't use resource manager because this needs to work on the server
        String path = String.format("/assets/%s/%s", location.getResourceDomain(), location.getResourcePath());
        InputStream in = ArchitectureCraftModel.class.getResourceAsStream(path);
        if (in == null) {
            String objsonPath = path.replace(".smeg", ".objson");
            in = ArchitectureCraftModel.class.getResourceAsStream(objsonPath);

            if (in == null) {
                Log.warning(String.format("Cannot find ArchitectureCraft model %s", path));
                return null;
            }
        }
        ArchitectureCraftModel model = gson.fromJson(new InputStreamReader(in), ArchitectureCraftModel.class);

        return model;
    }
    public static ResourceLocation resourceLocation(String path) {

        if (path.contains(":")) return new ResourceLocation(path);
        else return new ResourceLocation("architecturecraft", path);
    }
    public static ResourceLocation modelLocation(String path) {
        return resourceLocation("models/" + path);
    }
    public static ArchitectureCraftModel getModel(String name) {
        ResourceLocation loc = modelLocation(name);
        return fromResource(loc);
    }
}
