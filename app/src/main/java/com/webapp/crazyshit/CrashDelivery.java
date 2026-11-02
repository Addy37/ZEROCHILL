package com.webapp.crazyshit;

import java.util.Set;

final class CrashDelivery {
    interface Transport {
        String find(String marker) throws Exception;

        String create(CrashReport report) throws Exception;

        Set<String> messages(String threadId) throws Exception;

        void reply(String threadId, String text) throws Exception;
    }

    static boolean flush(CrashStore store, Transport transport, long now) throws Exception {
        for (CrashReport report : store.read()) {
            if (!report.pending()) continue;
            if (report.reported > 0 && now - report.deliveredAt < 60 * 60 * 1000L) continue;
            if (report.threadId.isEmpty()) {
                // Reconcile uncertain POST responses before attempting a second creation.
                String existing = transport.find(report.marker());
                if (existing.isEmpty() && !store.mayCreate(now)) continue;
                report.threadId = existing.isEmpty() ? transport.create(report) : existing;
                if (report.threadId.isEmpty())
                    throw new IllegalStateException("No crash thread ID");
                store.linked(report.id, report.threadId, now);
            }
            Set<String> sent = transport.messages(report.threadId);
            if (!report.diagnosticsSent)
                for (String chunk : report.chunks())
                    if (!sent.contains(chunk)) transport.reply(report.threadId, chunk);
            if (report.reported > 0) {
                String repeat =
                        report.marker()
                                + " Repeated "
                                + report.repeats
                                + " times. Last seen: "
                                + java.time.Instant.ofEpochMilli(report.lastAt);
                if (!sent.contains(repeat))
                    transport.reply(report.threadId, CrashSanitizer.clean(repeat));
            }
            store.completed(report.id, report.repeats, now);
            return true; // One report per launch; at most one new thread per day.
        }
        return false;
    }
}
