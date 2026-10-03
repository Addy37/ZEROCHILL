package com.webapp.crazyshit;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class ProfileAvatarUploadTest {
    private static final String USER = "00000000-0000-0000-0000-000000000001";
    static final class Pending implements ProfileAvatarUpload.Pending {
        Set<String> paths = new HashSet<>();
        public Set<String> read() { return new HashSet<>(paths); }
        public void write(Set<String> value) { paths = new HashSet<>(value); }
    }
    static final class Backend implements ProfileAvatarUpload.Backend {
        String path = USER + "/avatar.jpg";
        Set<String> objects = new HashSet<>();
        boolean failUpload, failReplace, failDelete, lostResponse, competingUpdate;
        int uploads;
        Backend() { objects.add(path); }
        public String currentPath() { return path; }
        public void upload(String value, byte[] jpeg) throws Exception {
            uploads++;
            if (failUpload) throw new Exception("upload failed");
            objects.add(value);
        }
        public boolean replace(String previous, String next) throws Exception {
            if (failReplace) throw new Exception("profile failed");
            if (competingUpdate) { path = USER + "/avatar-other.jpg"; objects.add(path); return false; }
            if (!path.equals(previous)) return false;
            path = next;
            if (lostResponse) throw new Exception("response lost");
            return true;
        }
        public void remove(String value) throws Exception {
            assertNotEquals("Must never remove the active profile", path, value);
            if (failDelete) throw new Exception("delete failed");
            objects.remove(value);
        }
    }
    @Test public void repeatedUpdatesHaveNewIdentityAndOnlyOneVersionedObject() throws Exception {
        Backend server = new Backend(); Pending queue = new Pending();
        String first = ProfileAvatarUpload.save(USER, new byte[]{1}, server, queue);
        String second = ProfileAvatarUpload.save(USER, new byte[]{2}, server, queue);
        assertNotEquals(first, second);
        assertEquals(second, server.path);
        assertEquals(2, server.objects.size());
        assertTrue(queue.paths.isEmpty());
    }
    @Test public void failedProfileKeepsOldImageAndCleansStagedObject() throws Exception {
        Backend server = new Backend(); Pending queue = new Pending(); server.failReplace = true;
        String previous = server.path;
        try { ProfileAvatarUpload.save(USER, new byte[]{1}, server, queue); fail(); }
        catch (Exception expected) { assertEquals("profile failed", expected.getMessage()); }
        assertEquals(previous, server.path); assertEquals(1, server.objects.size());
    }
    @Test public void failedUploadKeepsOldImage() throws Exception {
        Backend server = new Backend(); Pending queue = new Pending(); server.failUpload = true;
        String previous = server.path;
        try { ProfileAvatarUpload.save(USER, new byte[]{1}, server, queue); fail(); }
        catch (Exception expected) { assertEquals("upload failed", expected.getMessage()); }
        assertEquals(previous, server.path); assertEquals(1, server.objects.size());
    }
    @Test public void lostProfileResponseReconcilesWithoutDeletingLiveAvatar() throws Exception {
        Backend server = new Backend(); Pending queue = new Pending(); server.lostResponse = true;
        String saved = ProfileAvatarUpload.save(USER, new byte[]{1}, server, queue);
        assertEquals(saved, server.path);
        assertEquals(2, server.objects.size());
    }
    @Test public void cleanupDebtBlocksMoreUploadsUntilDeletionWorks() throws Exception {
        Backend server = new Backend(); Pending queue = new Pending();
        server.objects.clear(); server.path=USER+"/avatar-initial.jpg"; server.objects.add(server.path); server.failDelete = true;
        String current = ProfileAvatarUpload.save(USER, new byte[]{1}, server, queue);
        assertEquals(current, server.path); assertEquals(2, server.objects.size());
        try { ProfileAvatarUpload.save(USER, new byte[]{2}, server, queue); fail(); }
        catch (Exception expected) { assertEquals("delete failed", expected.getMessage()); }
        assertEquals(1, server.uploads);
        server.failDelete = false;
        ProfileAvatarUpload.save(USER, new byte[]{3}, server, queue);
        assertEquals(1, server.objects.size()); assertTrue(queue.paths.isEmpty());
    }
    @Test public void interruptedUploadDebtIsRecoveredBeforeNextImage() throws Exception {
        Backend server = new Backend(); Pending queue = new Pending();
        String abandoned = USER + "/avatar-abandoned.jpg";
        queue.paths.add(abandoned); server.objects.add(abandoned);
        ProfileAvatarUpload.save(USER, new byte[]{1}, server, queue);
        assertFalse(server.objects.contains(abandoned)); assertEquals(2, server.objects.size());
    }
    @Test public void competingProfileUpdateIsPreserved() throws Exception {
        Backend server = new Backend(); Pending queue = new Pending(); server.competingUpdate = true;
        try { ProfileAvatarUpload.save(USER, new byte[]{1}, server, queue); fail(); }
        catch (Exception expected) { assertTrue(expected.getMessage().contains("another device")); }
        assertEquals(USER + "/avatar-other.jpg", server.path); assertEquals(2, server.objects.size());
    }
    @Test public void cleanupRejectsAnotherAccountPath() throws Exception {
        Backend server = new Backend(); Pending queue = new Pending(); queue.paths.add("other/avatar.jpg");
        try { ProfileAvatarUpload.save(USER, new byte[]{1}, server, queue); fail(); }
        catch (Exception expected) { assertEquals("Invalid avatar cleanup path.", expected.getMessage()); }
        assertEquals(0, server.uploads);
    }
}
