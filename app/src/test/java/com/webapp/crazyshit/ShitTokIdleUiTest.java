package com.webapp.crazyshit;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import android.app.Application;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(application=Application.class,sdk=35,qualifiers="w400dp-h800dp-xhdpi")
public class ShitTokIdleUiTest {
    @Test public void adapterRefillsWaitThroughDragAndSettleThenApplyInOrder() {
        try(ShitTokAudioOwnershipTest.Fixture f=new ShitTokAudioOwnershipTest.Fixture()) {
            ViewPager2 pager=ReflectionHelpers.getField(f.feed,"pager");
            List<String> applied=new ArrayList<>();
            pager.getAdapter().registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override public void onItemRangeInserted(int first,int count) {
                    assertFalse(ReflectionHelpers.getField(f.feed,"userPaging"));applied.add("insert:"+first);
                }
            });
            ReflectionHelpers.callInstanceMethod(f.feed,"handlePagerScrollState",from(int.class,ViewPager2.SCROLL_STATE_DRAGGING));
            ReflectionHelpers.callInstanceMethod(f.feed,"deferUiApply",from(Runnable.class,(Runnable)()->{
                List<NativeContentItem> items=ReflectionHelpers.getField(f.feed,"items");
                items.add(new NativeContentItem(NativeContentItem.KIND_MEDIA,"D","https://example.invalid/d","","","",""));
                pager.getAdapter().notifyItemInserted(3);
            }));
            ReflectionHelpers.callInstanceMethod(f.feed,"handlePagerScrollState",from(int.class,ViewPager2.SCROLL_STATE_SETTLING));
            ReflectionHelpers.callInstanceMethod(f.feed,"deferUiApply",from(Runnable.class,(Runnable)()->{
                List<NativeContentItem> items=ReflectionHelpers.getField(f.feed,"items");
                items.add(new NativeContentItem(NativeContentItem.KIND_MEDIA,"E","https://example.invalid/e","","","",""));
                pager.getAdapter().notifyItemInserted(4);
            }));
            assertTrue(applied.isEmpty());
            ReflectionHelpers.callInstanceMethod(f.feed,"handlePagerScrollState",from(int.class,ViewPager2.SCROLL_STATE_IDLE));
            assertEquals(Arrays.asList("insert:3","insert:4"),applied);
        }
    }
    @Test public void holderBindAndRecycleBorrowWithoutCreatingOrReleasingEngines() {
        try(ShitTokAudioOwnershipTest.Fixture f=new ShitTokAudioOwnershipTest.Fixture()) {
            ShitTokPlaybackOwner owner=ReflectionHelpers.getField(f.feed,"playbackOwner");
            ViewPager2 pager=ReflectionHelpers.getField(f.feed,"pager");
            RecyclerView recycler=(RecyclerView)pager.getChildAt(0);
            RecyclerView.Adapter adapter=pager.getAdapter();
            ReflectionHelpers.callInstanceMethod(f.feed,"handlePagerScrollState",from(int.class,ViewPager2.SCROLL_STATE_DRAGGING));
            String before=owner.summary();
            RecyclerView.ViewHolder holder=adapter.createViewHolder(recycler,0);
            adapter.bindViewHolder(holder,1);adapter.onViewRecycled(holder);
            assertEquals(before,owner.summary());
            assertSame(f.offscreenPlayer.player,owner.player("https://example.invalid/clip-1"));
        }
    }
}
