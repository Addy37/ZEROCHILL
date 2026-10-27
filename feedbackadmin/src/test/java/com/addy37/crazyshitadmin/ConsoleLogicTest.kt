package com.addy37.crazyshitadmin

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConsoleLogicTest {
    private fun config(version: Int=5) = JSONObject("""{"configVersion":$version,"updatedAt":"old","global":{"fallbacksEnabled":true},"sources":{"fapello":{"enabled":true,"baseUrl":"https://example.com"},"future":{"enabled":true,"advanced":{"key":"keep"}}}}""")
    @Test fun editRevokesValidationAndDiscardRestoresLoadedSnapshot() {
        val raw=config().toString();val draft=ConfigDraft(raw,raw,8)
        val candidate=draft.candidate();val validated=draft.acceptValidation(candidate)
        assertTrue(validated.canPublish)
        assertEquals(9,JSONObject(candidate).getInt("configVersion"))
        val edited=validated.edit(JSONObject(validated.text).apply {getJSONObject("sources").getJSONObject("fapello").put("enabled",false)})
        assertFalse(edited.canPublish);assertTrue(edited.dirty)
        assertEquals(9,JSONObject(edited.candidate()).getInt("configVersion"))
        assertEquals(listOf("sources.fapello.enabled"),edited.changes())
        assertFalse(edited.discard().dirty);assertFalse(edited.discard().canPublish)
    }
    @Test fun candidateNeverDowngradesBundledVersionAndUnknownFieldsSurvive() {
        val raw=config(20).toString();val draft=ConfigDraft(raw,raw,3)
        val next=JSONObject(draft.candidate())
        assertEquals(21,next.getInt("configVersion"))
        assertEquals("keep",next.getJSONObject("sources").getJSONObject("future").getJSONObject("advanced").getString("key"))
    }
    @Test fun jsonKeyOrderDoesNotCreateFalseDirtyState() {
        val a="""{"a":1,"b":{"x":true,"y":[]}}"""
        val b="""{"b":{"y":[],"x":true},"a":1}"""
        val draft=ConfigDraft(a,b,0)
        assertFalse(draft.dirty);assertTrue(draft.changes().isEmpty());assertFalse(draft.canPublish)
    }
    @Test fun publicationRequiresExactValidatedCandidate() {
        val raw=config().toString()
        val draft=ConfigDraft(raw,raw,5).acceptValidation(ConfigDraft(raw,raw,5).candidate())
        assertTrue(draft.canPublish)
        assertFalse(draft.copy(text=draft.text+" ").canPublish)
        // Restoring process state intentionally does not restore publication permission.
        assertFalse(ConfigDraft(draft.baseline,draft.text,draft.published).canPublish)
    }
    private fun item(id:String,time:String,type:String="bug_report",status:String="submitted",unread:Int=0,message:String="Playback fails") =
        AdminRepository.Item(JSONObject().put("id",id).put("created_at",time).put("last_message_at",time).put("type",type).put("status",status).put("unread_count",unread).put("message",message).put("device","CPH2655").put("app_version","5.0.1"))
    @Test fun inboxCombinesSearchStatusTypeUnreadAndChronologicalSort() {
        val older=item("old","2026-10-01T10:00:00Z",unread=2)
        val newer=item("new","2026-10-02T10:00:00Z",status="in_progress")
        val feature=item("feature","2026-10-03T10:00:00Z",type="feature_request",unread=1)
        val all=listOf(older,newer,feature)
        assertEquals(listOf("old"),filterFeedback(all,"cph2655","bug_report","submitted",true,false).map {it.id})
        assertEquals(listOf("feature","new","old"),filterFeedback(all,"5.0.1","","",false,false).map {it.id})
        assertEquals(listOf("old","new","feature"),filterFeedback(all,"","","",false,true).map {it.id})
        assertTrue(filterFeedback(all,"no match","","",false,false).isEmpty())
    }
    @Test fun statusNamesAndDeviceFilteringPreserveFriendlyContracts() {
        assertEquals("New",statusLabel("submitted"));assertEquals("In Progress",statusLabel("in_progress"))
        listOf("unknown","","sdk_gphone64_x86_64","ranchu","Android SDK built for x86").forEach {assertFalse(realDevice(it))}
        assertTrue(realDevice("CPH2655"));assertEquals("OnePlus 13",deviceName("CPH2655"))
        assertEquals("Samsung Galaxy S25 Ultra",deviceName("samsung SM-S938B"))
    }
}
