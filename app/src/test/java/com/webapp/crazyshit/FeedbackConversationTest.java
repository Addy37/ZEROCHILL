package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class FeedbackConversationTest {
    @Test
    public void feedbackItemParsesConversationPreviewAndUnreadCount() throws Exception {
        FeedbackRepository.FeedbackItem item = new FeedbackRepository.FeedbackItem(
                new JSONObject()
                        .put("id", "thread-1")
                        .put("type", "bug_report")
                        .put("message", "Original report")
                        .put("status", "reviewing")
                        .put("last_message", "Can you try again?")
                        .put("last_sender", "developer")
                        .put("last_message_at", "2026-09-29T15:00:00Z")
                        .put("unread_count", 2)
        );

        assertEquals("Can you try again?", item.lastMessage);
        assertEquals("developer", item.lastSender);
        assertEquals(2, item.unreadCount);
    }

    @Test
    public void feedbackMessageExposesSenderAndReadReceipt() throws Exception {
        FeedbackRepository.FeedbackMessage developer = new FeedbackRepository.FeedbackMessage(
                new JSONObject()
                        .put("sender", "developer")
                        .put("message", "Fixed")
                        .put("read_at", "2026-09-29T15:05:00Z")
        );
        FeedbackRepository.FeedbackMessage user = new FeedbackRepository.FeedbackMessage(
                new JSONObject()
                        .put("sender", "user")
                        .put("message", "Thanks")
        );

        assertTrue(developer.fromDeveloper());
        assertTrue(developer.isRead());
        assertFalse(user.fromDeveloper());
        assertFalse(user.isRead());
    }
}
