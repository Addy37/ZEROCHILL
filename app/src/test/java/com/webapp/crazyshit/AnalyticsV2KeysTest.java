package com.webapp.crazyshit;
import java.time.LocalDate;
import org.junit.Test;
import static org.junit.Assert.*;
public class AnalyticsV2KeysTest {
 @Test public void threeHundredActionsBecomeOneLocalCounterBeforeNetwork() throws Exception {
  java.lang.reflect.Field field=AnalyticsTracker.class.getDeclaredField("COUNTERS");field.setAccessible(true);
  java.util.Map<String,Long> counters=(java.util.Map<String,Long>)field.get(null);counters.clear();
  java.lang.reflect.Method count=AnalyticsTracker.class.getDeclaredMethod("count",String.class,String.class,long.class);count.setAccessible(true);
  for(int i=0;i<300;i++) count.invoke(null,"search","all",1L);
  assertEquals(1,counters.size());assertEquals(Long.valueOf(300),counters.get("search\nall"));
  count.invoke(null,"search_terms","private query",1L);assertEquals(1,counters.size());counters.clear();
 }
 @Test public void anonymousKeysArePeriodScopedAndIndependent() throws Exception {
  LocalDate day=LocalDate.of(2026,11,1);
  String a=AnalyticsTracker.anonymousKey("random-period-seed","day",day);
  assertTrue(a.matches("[a-f0-9]{64}"));
  assertEquals(a,AnalyticsTracker.anonymousKey("random-period-seed","day",day));
  assertNotEquals(a,AnalyticsTracker.anonymousKey("random-period-seed","day",day.plusDays(1)));
  assertNotEquals(a,AnalyticsTracker.anonymousKey("independent-next-seed","day",day));
  assertNotEquals(a,AnalyticsTracker.anonymousKey("independent-month-seed","month",day));
 }
}
