package com.webapp.crazyshit;

import static org.junit.Assert.*;
import static com.webapp.crazyshit.BunkrMp4MetadataRepairTest.*;
import android.app.Application;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.ByteArrayDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class BunkrMp4DataSourceTest {
    @Test public void repairsTailMoovAcrossTinyReadsAndSeeksWithoutChangingMediaBytes() throws Exception {
        byte[] payload = new byte[120]; Arrays.fill(payload,(byte)0x7f);
        byte[] ftyp=box("ftyp",new byte[24]), mdat=box("mdat",payload), moov=fixture();
        byte[] original=concat(ftyp,mdat,moov);
        DataSource source=BunkrMp4DataSource.wrap(()->new ByteArrayDataSource(original)).createDataSource();
        assertEquals(original.length,source.open(spec(0,C.LENGTH_UNSET)));
        byte[] prefix=read(source,ftyp.length+8,1); source.close();
        assertArrayEquals(Arrays.copyOf(original,prefix.length),prefix);
        assertEquals(moov.length,source.open(spec(ftyp.length+mdat.length,C.LENGTH_UNSET)));
        byte[] repaired=read(source,moov.length,3);source.close();
        assertFalse(Arrays.equals(moov,repaired));assertEquals(moov.length,repaired.length);
        assertFalse(BunkrMp4MetadataRepair.repair(repaired));
        source.open(spec(ftyp.length+8,20));assertArrayEquals(Arrays.copyOf(payload,20),read(source,20,2));source.close();
        assertArrayEquals(concat(ftyp,mdat,moov),original);
    }

    @Test public void ordinaryStreamsAndBoundedRangeRequestsPassThroughExactly() throws Exception {
        byte[] original=concat(box("ftyp",new byte[24]),box("mdat",new byte[16]));
        for (int limit : new int[]{3,8,16,original.length}) {
            DataSource source=BunkrMp4DataSource.wrap(()->new ByteArrayDataSource(original)).createDataSource();
            assertEquals(limit,source.open(spec(0,limit)));
            assertArrayEquals(Arrays.copyOf(original,limit),read(source,limit,5));
            assertEquals(C.RESULT_END_OF_INPUT,source.read(new byte[1],0,1));source.close();
        }
    }

    @Test public void sharedFactoryRecognizesKnownMetadataOffsetAfterSourceRecreation() throws Exception {
        byte[] ftyp=box("ftyp",new byte[24]), mdat=box("mdat",new byte[4]), moov=fixture();
        byte[] bytes=concat(ftyp,mdat,moov);
        DataSource.Factory factory=BunkrMp4DataSource.wrap(()->new ByteArrayDataSource(bytes));
        DataSource first=factory.createDataSource();first.open(spec(0,C.LENGTH_UNSET));read(first,ftyp.length+8,7);first.close();
        DataSource second=factory.createDataSource();second.open(spec(ftyp.length+mdat.length,C.LENGTH_UNSET));
        byte[] fixed=read(second,moov.length,13);second.close();
        assertFalse(Arrays.equals(moov,fixed));
    }

    static DataSpec spec(long position,long length) {
        return new DataSpec.Builder().setUri(Uri.parse("https://cdn.example/file.mp4"))
                .setPosition(position).setLength(length).build();
    }
    static byte[] read(DataSource source,int wanted,int chunk) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[chunk];
        while(out.size()<wanted) {int n=source.read(b,0,Math.min(chunk,wanted-out.size()));if(n<0)break;assertTrue(n>0);out.write(b,0,n);}
        return out.toByteArray();
    }
}
