package com.webapp.crazyshit;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class CommentOwnershipModelTest {
    private static ZeroChillSocialRepository.Comment comment(
            String body,
            String editedAt,
            String deletedAt
    ) throws Exception {
        JSONObject value = new JSONObject()
                .put("id", "comment-1")
                .put("parent_id", "")
                .put("user_id", "user-1")
                .put("body", body)
                .put("created_at", "2026-09-30T12:00:00Z")
                .put("username", "addy37")
                .put("display_name", "Addy")
                .put("avatar_path", "")
                .put("like_count", 3);
        if (editedAt == null) value.put("edited_at", JSONObject.NULL);
        else value.put("edited_at", editedAt);
        if (deletedAt == null) value.put("deleted_at", JSONObject.NULL);
        else value.put("deleted_at", deletedAt);
        return new ZeroChillSocialRepository.Comment(value, false);
    }

    @Test
    public void activeEditedAndDeletedStatesStayDistinct() throws Exception {
        ZeroChillSocialRepository.Comment active = comment("Hello", null, null);
        ZeroChillSocialRepository.Comment edited =
                comment("Updated", "2026-09-30T12:05:00Z", null);
        ZeroChillSocialRepository.Comment deleted =
                comment("Comment deleted", "2026-09-30T12:05:00Z", "2026-09-30T12:10:00Z");

        assertFalse(active.edited());
        assertFalse(active.deleted());

        assertTrue(edited.edited());
        assertFalse(edited.deleted());

        assertTrue(deleted.deleted());
        assertFalse(deleted.edited());
    }
}
