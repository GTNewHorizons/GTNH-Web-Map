package org.dynmap.servlet;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.PlayerFaces;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.storage.MapStorageTile.TileRead;
import org.dynmap.utils.BufferInputStream;
import org.dynmap.utils.BufferOutputStream;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;

import org.dynmap.MapType.ImageEncoding;

public class MapStorageResourceHandler extends AbstractHandler {

    private DynmapCore core;
    private byte[] blankpng;
    private long blankpnghash = 0x12345678;
    
    public MapStorageResourceHandler() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BufferedImage blank = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        try {
            ImageIO.write(blank, "png", baos);
            blankpng = baos.toByteArray();
        } catch (IOException e) {
        }
        
    }
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String path = baseRequest.getPathInfo();
        int soff = 0, eoff;
        // We're handling this request
        baseRequest.setHandled(true);
        if(core.getLoginRequired()
            && request.getSession(true).getAttribute(LoginServlet.USERID_ATTRIB) == null){
            response.sendError(HttpStatus.UNAUTHORIZED_401);
            return;
        }
        if (path.charAt(0) == '/') soff = 1;
        eoff = path.indexOf('/', soff);
        if (soff < 0) {
            response.sendError(HttpStatus.NOT_FOUND_404);
            return;
        }
        String world = path.substring(soff, eoff);
        String uri = path.substring(eoff+1);
        ImageEncoding requestedEncoding = getRequestedEncoding(uri);
        // If faces directory, handle faces
        if (world.equals("faces")) {
            handleFace(response, uri);
            return;
        }
        // If markers directory, handle markers
        if (world.equals("_markers_")) {
            handleMarkers(response, uri);
            return;
        }

        DynmapWorld w = null;
        if (core.mapManager != null) {
            w = core.mapManager.getWorld(world);
        }
        // If world not found quit
        if (w == null) {
            if (!writeBlankImage(response, requestedEncoding)) {
                response.sendError(HttpStatus.NOT_FOUND_404);
            }
            return;
        }
        MapStorage store = w.getMapStorage();    // Get storage handler
        // Get tile reference, based on URI and world
        MapStorageTile tile = store.getTile(w, uri);
        if (tile == null) {
            if (!writeBlankImage(response, requestedEncoding)) {
                response.sendError(HttpStatus.NOT_FOUND_404);
            }
            return;
        }
        // Read tile
        TileRead tr = null;
        if (tile.getReadLock(5000)) {
            tr = tile.read();
            tile.releaseReadLock();
        }
        response.setHeader("Cache-Control", "max-age=0,must-revalidate");
        String etag;
        if (tr == null) {
        	etag = "\"" + blankpnghash + "\"";
        }
        else {
        	etag = "\"" + tr.hashCode + "\"";
        }
        response.setHeader("ETag", etag);
        String ifnullmatch = request.getHeader("If-None-Match");
        if ((ifnullmatch != null) && ifnullmatch.equals(etag)) {
            response.sendError(HttpStatus.NOT_MODIFIED_304);
        	return;
        }
        if (tr == null) {
            if (!writeBlankImage(response, requestedEncoding)) {
                response.sendError(HttpStatus.NOT_FOUND_404);
            }
            return;
        }
        // Got tile, package up for response
        response.setDateHeader("Last-Modified", tr.lastModified);
        response.setContentType(tr.format.getContentType());
        byte[] responseBody = tr.image.buffer();
        int responseLength = tr.image.length();
        if (isGzipCompressed(tr)) {
            response.setHeader("Vary", "Accept-Encoding");
        }
        if (isGzipCompressed(tr) && !clientAcceptsGzip(request)) {
            BufferInputStream uncompressed = gunzip(tr.image);
            responseBody = uncompressed.buffer();
            responseLength = uncompressed.length();
        } else if (isGzipCompressed(tr)) {
            response.setHeader("Content-Encoding", "gzip");
        }
        response.setIntHeader("Content-Length", responseLength);
        ServletOutputStream out = response.getOutputStream();
        out.write(responseBody, 0, responseLength);
        out.flush();

    }

    private boolean isGzipCompressed(TileRead tr) {
        return (tr != null) && (tr.image != null) && (tr.image.length() >= 2) && ((tr.image.buffer()[0] & 0xFF) == 0x1F)
                && ((tr.image.buffer()[1] & 0xFF) == 0x8B);
    }

    private boolean clientAcceptsGzip(HttpServletRequest request) {
        String acceptEncoding = request.getHeader("Accept-Encoding");
        return (acceptEncoding != null) && acceptEncoding.toLowerCase().contains("gzip");
    }

    private BufferInputStream gunzip(BufferInputStream compressed) throws IOException {
        GZIPInputStream gzip = new GZIPInputStream(compressed);
        BufferOutputStream out = new BufferOutputStream();
        byte[] buffer = new byte[8192];
        try {
            int len;
            while ((len = gzip.read(buffer)) >= 0) {
                if (len > 0) {
                    out.write(buffer, 0, len);
                }
            }
        } finally {
            gzip.close();
        }
        return new BufferInputStream(out.buf, out.len);
    }

    private ImageEncoding getRequestedEncoding(String uri) {
        int extoff = uri.lastIndexOf('.');
        if ((extoff < 0) || (extoff == (uri.length() - 1))) {
            return null;
        }
        return ImageEncoding.fromExt(uri.substring(extoff + 1));
    }

    private boolean writeBlankImage(HttpServletResponse response, ImageEncoding requestedEncoding) throws IOException {
        if ((requestedEncoding != null) && (requestedEncoding != ImageEncoding.PNG) && (requestedEncoding != ImageEncoding.JPG) && (requestedEncoding != ImageEncoding.WEBP)) {
            return false;
        }
        response.setContentType("image/png");
        response.setIntHeader("Content-Length", blankpng.length);
        OutputStream os = response.getOutputStream();
        os.write(blankpng);
        return true;
    }

    private void handleFace(HttpServletResponse response, String uri) throws IOException, ServletException {
        String[] suri = uri.split("[/\\.]");
        if (suri.length < 3) {  // 3 parts : face ID, player name, png
            response.sendError(HttpStatus.NOT_FOUND_404);
            return;
        }
        // Find type
        PlayerFaces.FaceType ft = PlayerFaces.FaceType.byID(suri[0]);
        if (ft == null) {
            response.sendError(HttpStatus.NOT_FOUND_404);
            return;
        }
        BufferInputStream bis = null;
        if (core.playerfacemgr != null) {
            bis = core.playerfacemgr.storage.getPlayerFaceImage(suri[1], ft);
        }
        if (bis == null) {
            response.sendError(HttpStatus.NOT_FOUND_404);
            return;
        }
        // Got image, package up for response
        response.setIntHeader("Content-Length", bis.length());
        response.setContentType("image/png");
        ServletOutputStream out = response.getOutputStream();
        out.write(bis.buffer(), 0, bis.length());
        out.flush();
    }

    private void handleMarkers(HttpServletResponse response, String uri) throws IOException, ServletException {
        String[] suri = uri.split("/");
        // If json file in last part
        if ((suri.length == 1) && suri[0].startsWith("marker_") && suri[0].endsWith(".json")) {
            String content = core.getDefaultMapStorage().getMarkerFile(suri[0].substring(7, suri[0].length() - 5));
            response.setContentType("application/json");
            PrintWriter pw = response.getWriter();
            pw.print(content);
            pw.flush();
            return;
        }
        // If png, make marker ID
        if (suri[suri.length-1].endsWith(".png")) {
            BufferInputStream bis = core.getDefaultMapStorage().getMarkerImage(uri.substring(0, uri.length()-4));
            // Got image, package up for response
            response.setIntHeader("Content-Length", bis.length());
            response.setContentType("image/png");
            ServletOutputStream out = response.getOutputStream();
            out.write(bis.buffer(), 0, bis.length());
            out.flush();
            return;
        }
        response.sendError(HttpStatus.NOT_FOUND_404);
    }

    public void setCore(DynmapCore core) {
        this.core = core;
    }
}
