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
    static class Backend implements ProfileAvatarUpload.Backend {
        String path = USER + "/avatar.jpg";
        Set<String> objects = new HashSet<>();
        boolean failUpload, failReplace, failDelete, lostResponse, competingUpdate;
        int uploads;
        boolean firstReconcileFails;
        boolean reconcileFailed;
        Backend() { objects.add(path); }
        public String currentPath() throws Exception {
            if (firstReconcileFails && path.contains("avatar-") && !reconcileFailed) {
                reconcileFailed = true; throw new Exception("temporary read failure");
            }
            return path;
        }
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
    @Test public void lostResponseAndFirstReadFailureCanStillConfirmCommittedAvatar() throws Exception {
        Backend server = new Backend(); Pending queue = new Pending();
        server.lostResponse = true; server.firstReconcileFails = true;
        String saved = ProfileAvatarUpload.save(USER, new byte[]{1}, server, queue);
        assertEquals(saved, server.path); assertTrue(server.reconcileFailed);
        assertEquals(2, server.objects.size()); assertTrue(queue.paths.isEmpty());
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
    @Test public void recoveryKeepsCurrentLegacyAndRecentStagingButRemovesLostOldObject() throws Exception {
        long now=1700000000000L; // Trusted Storage server time, independent of any phone clock.
        String current=USER+"/avatar-00000000-0000-4000-8000-000000000010.jpg";
        String old=USER+"/avatar-00000000-0000-4000-8000-000000000011.jpg";
        String recent=USER+"/avatar-00000000-0000-4000-8000-000000000012.jpg";
        Backend server=new Backend() {
            public ProfileAvatarUpload.StoredImages oldestImages() {
                java.util.List<ProfileAvatarUpload.StoredImage> rows=new java.util.ArrayList<>();
                for(String object:objects) rows.add(new ProfileAvatarUpload.StoredImage(
                        object.substring(USER.length()+1),object.equals(recent)?now:now-172800000L));
                return new ProfileAvatarUpload.StoredImages(rows, now);
            }
        };
        server.path=current;server.objects.add(current);server.objects.add(old);server.objects.add(recent);
        ProfileAvatarUpload.recoverAbandoned(USER,server);
        assertFalse(server.objects.contains(old));assertTrue(server.objects.contains(current));
        assertTrue(server.objects.contains(recent));assertTrue(server.objects.contains(USER+"/avatar.jpg"));
    }
    @Test public void missingServerDateSkipsRemoteRecovery() throws Exception {
        String old=USER+"/avatar-00000000-0000-4000-8000-000000000011.jpg";
        Backend server=new Backend() {
            public ProfileAvatarUpload.StoredImages oldestImages() {
                return new ProfileAvatarUpload.StoredImages(java.util.Collections.singletonList(
                        new ProfileAvatarUpload.StoredImage(old.substring(USER.length()+1),1L)),0L);
            }
        };
        server.objects.add(old);
        ProfileAvatarUpload.recoverAbandoned(USER,server);
        assertTrue(server.objects.contains(old));
    }
    @Test public void suspendedUploadCannotPublishExpiredStaging() throws Exception {
        Backend server=new Backend() {
            long elapsed;
            public long monotonicMillis() { return elapsed; }
            public void upload(String value,byte[] jpeg) throws Exception {
                super.upload(value,jpeg); elapsed=24*60*60*1000L;
            }
        };
        String previous=server.path;
        try { ProfileAvatarUpload.save(USER,new byte[]{1},server,new Pending()); fail(); }
        catch (Exception expected) { assertEquals("Avatar upload expired. Try again.",expected.getMessage()); }
        assertEquals(previous,server.path);assertEquals(1,server.objects.size());
    }
    @Test public void cleanupRejectsAnotherAccountPath() throws Exception {
        Backend server = new Backend(); Pending queue = new Pending(); queue.paths.add("other/avatar.jpg");
        try { ProfileAvatarUpload.save(USER, new byte[]{1}, server, queue); fail(); }
        catch (Exception expected) { assertEquals("Invalid avatar cleanup path.", expected.getMessage()); }
        assertEquals(0, server.uploads);
    }
}
