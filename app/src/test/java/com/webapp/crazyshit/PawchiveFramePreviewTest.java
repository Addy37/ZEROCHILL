package com.webapp.crazyshit;

import android.app.Application;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSpec;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class PawchiveFramePreviewTest {
    @Test public void headAndLateMp4IndexShareOneByteBudgetAcrossExtractorReopens() throws Exception {
        long mediaSize = 203_846_822L;
        long index = 203_793_152L;
        int block = BunkrMediaPreview.PAWCHIVE_BLOCK_BYTES;
        List<Long> requests = new ArrayList<>();
        Uri uri = Uri.parse("https://n3.pawchive.pw/data/ab/cd/clip.mp4");
        try (BunkrPreviewDataSource bytes = new BunkrPreviewDataSource((position, size) -> {
            requests.add(position);
            return new BunkrPreviewDataSource.Block(new byte[(int) Math.min(size, mediaSize - position)], mediaSize);
        }, block)) {
            byte[] out = new byte[32];
            PawchiveFramePreview.BoundedSource head = new PawchiveFramePreview.BoundedSource(uri, bytes);
            assertEquals(mediaSize, head.open(new DataSpec(uri)));
            assertEquals(32, head.read(out, 0, out.length));
            head.close();

            PawchiveFramePreview.BoundedSource end = new PawchiveFramePreview.BoundedSource(uri, bytes);
            assertEquals(16, end.open(new DataSpec.Builder().setUri(uri).setPosition(index).setLength(16).build()));
            assertEquals(16, end.read(out, 0, out.length));
            assertEquals(C.RESULT_END_OF_INPUT, end.read(out, 0, out.length));
            end.close();

            assertEquals(mediaSize, head.open(new DataSpec(uri)));
            assertEquals(32, head.read(out, 0, out.length));
            head.close();
            assertEquals(Arrays.asList(0L, index - index % block), requests);
        }
    }
}
