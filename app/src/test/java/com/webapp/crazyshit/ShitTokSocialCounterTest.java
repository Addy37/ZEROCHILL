package com.webapp.crazyshit;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class ShitTokSocialCounterTest {
    @Test
    public void visibleCommentCountExcludesDeletedRows() throws Exception {
        ZeroChillSocialRepository.Comment topLevel = new ZeroChillSocialRepository.Comment(
                new JSONObject()
                        .put("id", "one")
                        .put("body", "hello")
                        .put("deleted_at", JSONObject.NULL),
                false
        );
        ZeroChillSocialRepository.Comment reply = new ZeroChillSocialRepository.Comment(
                new JSONObject()
                        .put("id", "two")
                        .put("parent_id", "one")
                        .put("body", "reply")
                        .put("deleted_at", JSONObject.NULL),
                false
        );
        ZeroChillSocialRepository.Comment deleted = new ZeroChillSocialRepository.Comment(
                new JSONObject()
                        .put("id", "three")
                        .put("body", "Comment deleted")
                        .put("deleted_at", "2026-10-01T12:00:00Z"),
                false
        );

        assertEquals(2, ChaosFeedView.visibleCommentCount(
                Arrays.asList(topLevel, reply, deleted)
        ));
        assertEquals(0, ChaosFeedView.visibleCommentCount(null));
    }
}
