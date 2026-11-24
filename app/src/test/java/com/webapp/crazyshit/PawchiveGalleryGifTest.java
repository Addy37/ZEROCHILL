package com.webapp.crazyshit;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class PawchiveGalleryGifTest {
    private byte[] gif(int width, int height, int length) {
        byte[] bytes = new byte[length];
        System.arraycopy("GIF89a".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 6);
        bytes[6] = (byte) width; bytes[7] = (byte) (width >> 8);
        bytes[8] = (byte) height; bytes[9] = (byte) (height >> 8);
        return bytes;
    }
    @Test public void acceptsSmallGifAndPreservesOriginalBytes() throws Exception {
        byte[] bytes = gif(320, 240, 100);
        assertArrayEquals(bytes, PawchiveOriginalImageLoader.readGalleryGif(
                new ByteArrayInputStream(bytes), bytes.length, () -> {}));
    }
    @Test(expected = IOException.class) public void rejectsDeclaredOversizedGif() throws Exception {
        PawchiveOriginalImageLoader.readGalleryGif(new ByteArrayInputStream(gif(320,240,100)),
                PawchiveOriginalImageLoader.MAX_GALLERY_GIF_BYTES + 1L, () -> {});
    }
    @Test(expected = IOException.class) public void rejectsUnknownLengthOverLimit() throws Exception {
        byte[] bytes = gif(320,240,PawchiveOriginalImageLoader.MAX_GALLERY_GIF_BYTES+1);
        PawchiveOriginalImageLoader.readGalleryGif(new ByteArrayInputStream(bytes), -1, () -> {});
    }
    @Test(expected = IOException.class) public void rejectsOversizedDimensions() throws Exception {
        PawchiveOriginalImageLoader.readGalleryGif(new ByteArrayInputStream(gif(2048,2048,100)),
                100, () -> {});
    }
    @Test(expected = IOException.class) public void rejectsStaticThumbnail() throws Exception {
        PawchiveOriginalImageLoader.readGalleryGif(new ByteArrayInputStream(new byte[20]), 20, () -> {});
    }
    @Test(expected = IOException.class) public void cancellationStopsRead() throws Exception {
        PawchiveOriginalImageLoader.readGalleryGif(new ByteArrayInputStream(gif(320,240,100)),
                100, () -> { throw new IOException("canceled"); });
    }
}
