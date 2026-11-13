package com.webapp.crazyshit;

import android.app.Application;
import android.widget.TextView;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class VideoDetailLikeRaceTest {
    private VideoDetailActivity shell(ArrayList<ZeroChillSocialRepository.Callback<ZeroChillSocialRepository.VideoLikeState>> requests) {
        VideoDetailActivity activity = Robolectric.buildActivity(VideoDetailActivity.class).get();
        ReflectionHelpers.setField(activity, "pageUrl", "https://example.com/video/one");
        ReflectionHelpers.setField(activity, "videoLikeOwner", "");
        ReflectionHelpers.setField(activity, "videoLikeButton", new TextView(activity));
        ReflectionHelpers.setField(activity, "videoLikeLoader", (VideoDetailActivity.VideoLikeLoader)
                (context, page, callback) -> requests.add(callback));
        return activity;
    }
    private void refresh(VideoDetailActivity activity) {
        ReflectionHelpers.callInstanceMethod(activity, "refreshVideoLikeState",
                ReflectionHelpers.ClassParameter.from(boolean.class, true));
    }
    private void begin(VideoDetailActivity activity) {
        ReflectionHelpers.callInstanceMethod(activity, "beginVideoLikeWrite",
                ReflectionHelpers.ClassParameter.from(String.class, "https://example.com/video/one"));
    }
    @Test public void preWriteReadCannotUndoOptimisticHeartOrReenablePendingWrite() {
        ArrayList<ZeroChillSocialRepository.Callback<ZeroChillSocialRepository.VideoLikeState>> requests = new ArrayList<>();
        VideoDetailActivity activity = shell(requests);
        refresh(activity); assertEquals(1, requests.size());
        begin(activity);
        ReflectionHelpers.setField(activity, "videoLiked", true);
        ReflectionHelpers.setField(activity, "videoLikeCount", 8);
        ReflectionHelpers.callInstanceMethod(activity, "updateVideoLikeButton");
        requests.get(0).complete(new ZeroChillSocialRepository.VideoLikeState(7, false), null);
        assertTrue((Boolean) ReflectionHelpers.getField(activity, "videoLiked"));
        assertEquals(8, (int) ReflectionHelpers.<Integer>getField(activity, "videoLikeCount"));
        assertFalse(((TextView) ReflectionHelpers.getField(activity, "videoLikeButton")).isEnabled());
        refresh(activity); assertEquals("Resume cannot start a competing read during the write", 1, requests.size());
    }
    @Test public void changingContentClearsPendingUiStateAndAllowsNewRead() {
        ArrayList<ZeroChillSocialRepository.Callback<ZeroChillSocialRepository.VideoLikeState>> requests = new ArrayList<>();
        VideoDetailActivity activity = shell(requests);
        begin(activity);
        ReflectionHelpers.setField(activity, "pageUrl", "https://example.com/video/two");
        refresh(activity); assertEquals(1, requests.size());
        assertEquals("", ReflectionHelpers.getField(activity, "pendingVideoLikeUrl"));
        requests.get(0).complete(new ZeroChillSocialRepository.VideoLikeState(2, false), null);
        assertTrue(((TextView) ReflectionHelpers.getField(activity, "videoLikeButton")).isEnabled());
    }
}
