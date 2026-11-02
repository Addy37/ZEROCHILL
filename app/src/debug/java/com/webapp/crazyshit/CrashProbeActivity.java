package com.webapp.crazyshit;

/** Explicit debug/Beta-only physical fixture. Never included in release source sets. */
public final class CrashProbeActivity extends android.app.Activity {
    @Override
    public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.widget.Button trigger = new android.widget.Button(this);
        trigger.setText("Trigger controlled ZEROCHILL Beta crash");
        trigger.setOnClickListener(
                v ->
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .post(
                                        () -> {
                                            throw new IllegalStateException(
                                                    "Controlled ZEROCHILL Beta crash");
                                        }));
        setContentView(trigger);
    }
}
