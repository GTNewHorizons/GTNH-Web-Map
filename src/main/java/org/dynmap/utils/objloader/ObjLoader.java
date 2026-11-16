package org.dynmap.utils.objloader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dynmap.renderer.RenderPatchFactory.SideVisible;
import org.dynmap.utils.MeshTriangle;
import org.dynmap.utils.MeshVertex;
import org.dynmap.utils.TriangleMesh;

/**
 * Loads Wavefront OBJ triangle meshes.
 */
public final class ObjLoader {
    private static final String DEFAULT_MESH_KEY = "default/default";

    private ObjLoader() {}

    /**
     * Load an OBJ model into triangle meshes.
     *
     * @param reader OBJ source
     * @param textureIndexes texture indexes keyed by object/group
     * @return loaded meshes
     * @throws IOException if the source cannot be read
     */
    public static Map<String, TriangleMesh> load(Reader reader, Map<String, Integer> textureIndexes)
            throws IOException {
        if (reader == null) {
            throw new IllegalArgumentException("OBJ reader must be provided");
        }

        List<Position> positions = new ArrayList<Position>();
        List<TextureCoordinate> textureCoordinates = new ArrayList<TextureCoordinate>();
        TriangleMesh mesh = new TriangleMesh();
        BufferedReader bufferedReader = reader instanceof BufferedReader ? (BufferedReader) reader
                : new BufferedReader(reader);

        StringBuilder logicalLine = new StringBuilder();
        int lineNumber = 0;
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            lineNumber++;
            String content = stripComment(line).trim();
            boolean continues = content.endsWith("\\");
            if (continues) {
                content = content.substring(0, content.length() - 1).trim();
            }
            if (logicalLine.length() > 0 && content.length() > 0) {
                logicalLine.append(' ');
            }
            logicalLine.append(content);
            if (continues) {
                continue;
            }

            parseLine(logicalLine.toString(), lineNumber, positions, textureCoordinates, mesh);
            logicalLine.setLength(0);
        }
        if (logicalLine.length() > 0) {
            throw parseError(lineNumber, "Unterminated line continuation");
        }

        Map<String, TriangleMesh> result = new LinkedHashMap<String, TriangleMesh>();
        if (!mesh.getTriangles().isEmpty()) {
            result.put(DEFAULT_MESH_KEY, mesh);
        }
        return result;
    }

    private static void parseLine(String line, int lineNumber, List<Position> positions,
            List<TextureCoordinate> textureCoordinates, TriangleMesh mesh) {
        if (line.length() == 0) {
            return;
        }

        String[] tokens = line.split("\\s+");
        String directive = tokens[0];
        if ("v".equals(directive)) {
            addPosition(tokens, lineNumber, positions);
        } else if ("vt".equals(directive)) {
            addTextureCoordinate(tokens, lineNumber, textureCoordinates);
        } else if ("f".equals(directive)) {
            addFace(tokens, lineNumber, positions, textureCoordinates, mesh);
        }
    }

    private static void addPosition(String[] tokens, int lineNumber, List<Position> positions) {
        if (tokens.length < 4) {
            throw parseError(lineNumber, "Vertex position must provide x, y, and z coordinates");
        }
        positions.add(new Position(
                parseCoordinate(tokens[1], lineNumber, "vertex x coordinate"),
                parseCoordinate(tokens[2], lineNumber, "vertex y coordinate"),
                parseCoordinate(tokens[3], lineNumber, "vertex z coordinate")));
    }

    private static void addTextureCoordinate(String[] tokens, int lineNumber,
            List<TextureCoordinate> textureCoordinates) {
        if (tokens.length < 2) {
            throw parseError(lineNumber, "Texture coordinate must provide a U coordinate");
        }
        double u = parseCoordinate(tokens[1], lineNumber, "texture U coordinate");
        double v = tokens.length > 2 ? parseCoordinate(tokens[2], lineNumber, "texture V coordinate") : 0.0;
        textureCoordinates.add(new TextureCoordinate(u, v));
    }

    private static void addFace(String[] tokens, int lineNumber, List<Position> positions,
            List<TextureCoordinate> textureCoordinates, TriangleMesh mesh) {
        if (tokens.length < 4) {
            throw parseError(lineNumber, "Face must provide at least three vertices");
        }

        List<MeshVertex> vertices = new ArrayList<MeshVertex>(tokens.length - 1);
        for (int index = 1; index < tokens.length; index++) {
            vertices.add(parseFaceVertex(tokens[index], lineNumber, positions, textureCoordinates));
        }
        MeshVertex first = vertices.get(0);
        for (int index = 1; index < vertices.size() - 1; index++) {
            mesh.addTriangle(new MeshTriangle(first, vertices.get(index), vertices.get(index + 1), SideVisible.BOTH, 0));
        }
    }

    private static MeshVertex parseFaceVertex(String token, int lineNumber, List<Position> positions,
            List<TextureCoordinate> textureCoordinates) {
        String[] indices = token.split("/", -1);
        if (indices.length > 3 || indices.length == 0 || indices[0].length() == 0) {
            throw parseError(lineNumber, "Invalid face vertex reference: " + token);
        }

        Position position = positions.get(resolveIndex(indices[0], positions.size(), lineNumber, "vertex"));
        TextureCoordinate textureCoordinate = new TextureCoordinate(0.0, 0.0);
        if (indices.length > 1 && indices[1].length() > 0) {
            textureCoordinate = textureCoordinates.get(resolveIndex(indices[1], textureCoordinates.size(), lineNumber,
                    "texture coordinate"));
        }
        return new MeshVertex(position.x, position.y, position.z, textureCoordinate.u, textureCoordinate.v);
    }

    private static int resolveIndex(String value, int size, int lineNumber, String type) {
        int index;
        try {
            index = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw parseError(lineNumber, "Invalid " + type + " index: " + value);
        }
        if (index == 0) {
            throw parseError(lineNumber, type + " index cannot be zero");
        }
        int resolved = index > 0 ? index - 1 : size + index;
        if (resolved < 0 || resolved >= size) {
            throw parseError(lineNumber, type + " index is outside the available range: " + value);
        }
        return resolved;
    }

    private static double parseCoordinate(String value, int lineNumber, String description) {
        double coordinate;
        try {
            coordinate = Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw parseError(lineNumber, "Invalid " + description + ": " + value);
        }
        if (Double.isNaN(coordinate) || Double.isInfinite(coordinate)) {
            throw parseError(lineNumber, description + " must be finite");
        }
        return coordinate;
    }

    private static String stripComment(String line) {
        int commentStart = line.indexOf('#');
        return commentStart < 0 ? line : line.substring(0, commentStart);
    }

    private static IllegalArgumentException parseError(int lineNumber, String message) {
        return new IllegalArgumentException("Invalid OBJ at line " + lineNumber + ": " + message);
    }

    private static final class Position {
        private final double x;
        private final double y;
        private final double z;

        private Position(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class TextureCoordinate {
        private final double u;
        private final double v;

        private TextureCoordinate(double u, double v) {
            this.u = u;
            this.v = v;
        }
    }
}
