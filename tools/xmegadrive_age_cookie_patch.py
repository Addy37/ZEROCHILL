from pathlib import Path
import re
import textwrap

ROOT = Path('.')

def write(path, content):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(textwrap.dedent(content).lstrip(), encoding='utf-8')

def replace(path, old, new):
    p = ROOT / path
    text = p.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'missing replacement in {path}: {old[:80]!r}')
    p.write_text(text.replace(old, new), encoding='utf-8')

def regex_replace(path, pattern, replacement, count=1):
    p = ROOT / path
    text = p.read_text(encoding='utf-8')
    updated, n = re.subn(pattern, replacement, text, count=count, flags=re.S)
    if n != count:
        raise SystemExit(f'expected {count} regex replacements in {path}, got {n}')
    p.write_text(updated, encoding='utf-8')

# Direct, explicit age-session marker discovered from the physical diagnostic.
write('app/src/main/java/com/webapp/crazyshit/XMegaDriveAgeSession.java', r'''
package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.CookieManager;

/** Owns the explicit XMegaDrive 18+ age-session marker after ZEROCHILL confirmation. */
final class XMegaDriveAgeSession {
    static final String COOKIE_NAME = "kt_agecheck";
    static final String COOKIE_VALUE = "1";
    static final String COOKIE_URL = "https://www.xmegadrive.com/";
    static final String PREF_SETUP_COMPLETE = "xmegadrive_access_setup_v1_complete";
    static final String PREF_REPAIR_HANDLED = "xmegadrive_access_setup_v1_repair_handled";

    private static final String PREFS = "app_prefs";
    private static final long COOKIE_MAX_AGE_SECONDS = 365L * 24L * 60L * 60L;

    interface Callback {
        void onComplete(boolean ready);
    }

    private XMegaDriveAgeSession() {
    }

    static String cookieDirective() {
        return COOKIE_NAME + "=" + COOKIE_VALUE
                + "; Max-Age=" + COOKIE_MAX_AGE_SECONDS
                + "; Path=/; SameSite=Lax; Secure";
    }

    static boolean hasAgeCookieHeader(String header) {
        if (header == null || header.trim().isEmpty()) return false;
        for (String part : header.split(";")) {
            String item = part == null ? "" : part.trim();
            int split = item.indexOf('=');
            if (split <= 0) continue;
            String name = item.substring(0, split).trim();
            String value = item.substring(split + 1).trim();
            if (COOKIE_NAME.equals(name) && COOKIE_VALUE.equals(value)) return true;
        }
        return false;
    }

    static boolean isCookieReady(Context context) {
        try {
            return hasAgeCookieHeader(CookieManager.getInstance().getCookie(COOKIE_URL));
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isSetupComplete(Context context) {
        return context != null && prefs(context).getBoolean(PREF_SETUP_COMPLETE, false);
    }

    static boolean isRepairHandled(Context context) {
        return context != null && prefs(context).getBoolean(PREF_REPAIR_HANDLED, false);
    }

    static boolean shouldShowRepair(Context context) {
        if (context == null || !AccessNoticeDialog.isAccepted(context)) return false;
        if (isSetupComplete(context)) {
            restoreIfAuthorized(context);
            return false;
        }
        if (isCookieReady(context)) {
            markSetupComplete(context);
            return false;
        }
        return !isRepairHandled(context);
    }

    static boolean shouldShowRepairFromState(
            boolean accessAccepted,
            boolean setupComplete,
            boolean repairHandled,
            boolean cookieReady
    ) {
        return accessAccepted && !setupComplete && !repairHandled && !cookieReady;
    }

    static void markSetupComplete(Context context) {
        if (context == null) return;
        prefs(context).edit()
                .putBoolean(PREF_SETUP_COMPLETE, true)
                .putBoolean(PREF_REPAIR_HANDLED, true)
                .apply();
    }

    static void markRepairHandled(Context context) {
        if (context == null) return;
        prefs(context).edit().putBoolean(PREF_REPAIR_HANDLED, true).apply();
    }

    static void restoreIfAuthorized(Context context) {
        if (context == null
                || !AccessNoticeDialog.isAccepted(context)
                || !isSetupComplete(context)
                || isCookieReady(context)) {
            return;
        }
        writeCookie(context, null);
    }

    static void authorize(Context context, Callback callback) {
        writeCookie(context, callback);
    }

    private static void writeCookie(Context context, Callback callback) {
        if (context == null) {
            finish(callback, false);
            return;
        }
        try {
            CookieManager cookies = CookieManager.getInstance();
            cookies.setAcceptCookie(true);
            cookies.setCookie(COOKIE_URL, cookieDirective(), accepted -> {
                try {
                    cookies.flush();
                } catch (Exception ignored) {
                }
                finish(callback, isCookieReady(context));
            });
        } catch (Exception failure) {
            finish(callback, false);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void finish(Callback callback, boolean ready) {
        if (callback != null) callback.onComplete(ready);
    }
}
''')

write('app/src/main/java/com/webapp/crazyshit/XMegaDriveAgePrompt.java', r'''
package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Native ZEROCHILL consent prompt for XMegaDrive's explicit age-session marker. */
final class XMegaDriveAgePrompt {
    static final String TAG_TITLE = "xmegadrive_age_prompt_title";
    static final String TAG_CONTINUE = "xmegadrive_age_prompt_continue";
    static final String TAG_CANCEL = "xmegadrive_age_prompt_cancel";
    static final String TAG_NOTE = "xmegadrive_age_prompt_note";

    interface ConfirmHandler {
        void onConfirm(XMegaDriveAgePrompt prompt);
    }

    private final Activity activity;
    private final ConfirmHandler onConfirm;
    private final Runnable onCancel;
    private final FrameLayout root;
    private final TextView continueButton;
    private final TextView note;

    XMegaDriveAgePrompt(Activity activity, ConfirmHandler onConfirm, Runnable onCancel) {
        this.activity = activity;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;

        root = new FrameLayout(activity);
        root.setBackgroundColor(activity.getColor(R.color.zc_scrim));
        root.setClickable(true);
        root.setFocusable(true);
        root.setElevation(dp(activity, 96));

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int cardPad = dp(activity, 24);
        card.setPadding(cardPad, cardPad, cardPad, cardPad);
        card.setBackground(rounded(
                activity,
                activity.getColor(R.color.zc_surface_raised),
                activity.getColor(R.color.zc_edge),
                1,
                28
        ));

        TextView eyebrow = text(activity, "ZEROCHILL • XMEGADRIVE", 12,
                activity.getColor(R.color.zc_cyan), Typeface.BOLD);
        eyebrow.setLetterSpacing(0.08f);
        card.addView(eyebrow, wrap());

        TextView title = text(activity, "Set up XMegaDrive access", 24,
                activity.getColor(R.color.zc_text_primary), Typeface.BOLD);
        title.setTag(TAG_TITLE);
        LinearLayout.LayoutParams titleParams = wrap();
        titleParams.topMargin = dp(activity, 14);
        card.addView(title, titleParams);

        TextView body = text(
                activity,
                "Confirm that you're 18 or older. ZEROCHILL will prepare XMegaDrive's age-access marker on this device so the site's age page stays out of the way.",
                15,
                activity.getColor(R.color.zc_text_secondary),
                Typeface.NORMAL
        );
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(0f, 1.08f);
        LinearLayout.LayoutParams bodyParams = matchWidthWrap();
        bodyParams.topMargin = dp(activity, 12);
        card.addView(body, bodyParams);

        continueButton = text(activity, "CONFIRM 18+ & CONTINUE", 16,
                activity.getColor(R.color.app_on_primary), Typeface.BOLD);
        continueButton.setTag(TAG_CONTINUE);
        continueButton.setGravity(Gravity.CENTER);
        continueButton.setMinHeight(dp(activity, 52));
        continueButton.setClickable(true);
        continueButton.setFocusable(true);
        continueButton.setBackground(rounded(
                activity,
                activity.getColor(R.color.zc_cyan),
                activity.getColor(R.color.zc_cyan),
                0,
                18
        ));
        continueButton.setOnClickListener(view -> {
            showVerifying();
            if (onConfirm != null) onConfirm.onConfirm(this);
        });
        LinearLayout.LayoutParams continueParams = matchWidthWrap();
        continueParams.topMargin = dp(activity, 22);
        card.addView(continueButton, continueParams);

        note = text(
                activity,
                "This only prepares XMegaDrive access. It does not share your ZEROCHILL account or messages.",
                12,
                activity.getColor(R.color.zc_text_muted),
                Typeface.NORMAL
        );
        note.setTag(TAG_NOTE);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = matchWidthWrap();
        noteParams.topMargin = dp(activity, 12);
        card.addView(note, noteParams);

        TextView cancel = text(activity, "NOT NOW", 14,
                activity.getColor(R.color.zc_text_secondary), Typeface.BOLD);
        cancel.setTag(TAG_CANCEL);
        cancel.setGravity(Gravity.CENTER);
        int cancelPad = dp(activity, 12);
        cancel.setPadding(cancelPad, cancelPad, cancelPad, cancelPad);
        cancel.setOnClickListener(view -> {
            if (onCancel != null) onCancel.run();
        });
        LinearLayout.LayoutParams cancelParams = wrap();
        cancelParams.topMargin = dp(activity, 4);
        card.addView(cancel, cancelParams);

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int margin = dp(activity, 24);
        cardParams.leftMargin = margin;
        cardParams.rightMargin = margin;
        cardParams.gravity = Gravity.CENTER;
        root.addView(card, cardParams);
    }

    void show() {
        if (root.getParent() != null || activity.isFinishing() || activity.isDestroyed()) return;
        activity.addContentView(root, new FrameLayout.LayoutParams(-1, -1));
        root.bringToFront();
        root.requestFocus();
    }

    void dismiss() {
        try {
            ViewParent parent = root.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(root);
        } catch (Exception ignored) {
        }
    }

    void showVerifying() {
        continueButton.setText("SETTING UP…");
        continueButton.setEnabled(false);
        continueButton.setAlpha(0.78f);
    }

    void showReady() {
        continueButton.setText("✓ ACCESS READY");
        continueButton.setEnabled(false);
        continueButton.setAlpha(1f);
        note.setText("XMegaDrive age access is ready on this device.");
    }

    void showFailure() {
        continueButton.setText("TRY AGAIN");
        continueButton.setEnabled(true);
        continueButton.setAlpha(1f);
        note.setText("Access is confirmed. ZEROCHILL couldn't store the marker yet. Try again or choose Not now.");
    }

    FrameLayout rootForTest() {
        return root;
    }

    private static TextView text(Context context, String value, float sp, int color, int style) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(-2, -2);
    }

    private static LinearLayout.LayoutParams matchWidthWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private static GradientDrawable rounded(
            Context context,
            int fill,
            int stroke,
            int strokeDp,
            int radiusDp
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(context, strokeDp), stroke);
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
''')

write('app/src/main/java/com/webapp/crazyshit/XMegaDriveAccessSetupActivity.java', r'''
package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.FrameLayout;

/** One-time upgrade repair that unifies existing 18+ acceptance with XMegaDrive access. */
public final class XMegaDriveAccessSetupActivity extends Activity {
    private XMegaDriveAgePrompt prompt;
    private boolean leaving;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ZeroChillUi.applySystemBars(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        if (!XMegaDriveAgeSession.shouldShowRepair(this)) {
            completeLaunch();
            return;
        }
        showPrompt();
    }

    private void showPrompt() {
        prompt = new XMegaDriveAgePrompt(
                this,
                active -> {
                    XMegaDriveAgeSession.markSetupComplete(this);
                    XMegaDriveAgeSession.authorize(this, ready -> runOnUiThread(() -> {
                        if (leaving || isFinishing() || isDestroyed()) return;
                        if (!ready) {
                            active.showFailure();
                            return;
                        }
                        active.showReady();
                        active.rootForTest().postDelayed(this::completeLaunch, 220L);
                    }));
                },
                () -> {
                    XMegaDriveAgeSession.markRepairHandled(this);
                    completeLaunch();
                }
        );
        prompt.show();
    }

    private void completeLaunch() {
        if (leaving) return;
        leaving = true;
        if (prompt != null) prompt.dismiss();
        Intent app = new Intent(this, NativeMainActivity.class);
        String action = getIntent() == null ? null : getIntent().getAction();
        if (AppShortcuts.isShortcutAction(action)) {
            app.setAction(action);
            app.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }
        startActivity(app);
        overridePendingTransition(0, 0);
        finish();
    }

    @Override
    public void onBackPressed() {
        XMegaDriveAgeSession.markRepairHandled(this);
        completeLaunch();
    }
}
''')

# Startup wizard: replace checkbox with unified action.
path = 'app/src/main/java/com/webapp/crazyshit/StartupWizardActivity.java'
replace(path,
'''    private boolean ageConfirmed;\n    private boolean favoriteAlerts;''',
'''    private boolean ageConfirmed;\n    private boolean xmegaAccessAuthorized;\n    private boolean xmegaAccessReady;\n    private boolean accessSetupInProgress;\n    private TextView accessSetupButton;\n    private TextView accessSetupStatus;\n    private boolean favoriteAlerts;''')
replace(path,
'''        ageConfirmed = AccessNoticeDialog.isAccepted(this);\n        favoriteAlerts = prefs.getBoolean(NotificationCoordinator.PREF_NEW_VIDEO_ALERTS, true);''',
'''        ageConfirmed = AccessNoticeDialog.isAccepted(this);\n        xmegaAccessReady = XMegaDriveAgeSession.isCookieReady(this);\n        xmegaAccessAuthorized = XMegaDriveAgeSession.isSetupComplete(this)\n                || (ageConfirmed && xmegaAccessReady);\n        favoriteAlerts = prefs.getBoolean(NotificationCoordinator.PREF_NEW_VIDEO_ALERTS, true);''')
old_checkbox = '''        ZeroChillCheckBox confirm = new ZeroChillCheckBox(this);\n        confirm.setText("I confirm that I am 18 or older.");\n        confirm.setTextColor(Color.WHITE);\n        confirm.setTextSize(15f);\n        confirm.setChecked(ageConfirmed);\n        confirm.setPadding(dp(2), dp(8), dp(2), dp(8));\n        confirm.setOnCheckedChangeListener((button, checked) -> {\n            ageConfirmed = checked;\n            updateNextButtonState();\n            haptic(button);\n        });\n        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(-1, -2);\n        checkParams.topMargin = dp(5);\n        column.addView(confirm, checkParams);\n        return scroll;\n    }\n'''
new_access = '''        accessSetupButton = actionButton(\n                xmegaAccessAuthorized && xmegaAccessReady\n                        ? "✓ ACCESS READY"\n                        : "CONFIRM 18+ & SET UP ACCESS",\n                true\n        );\n        accessSetupButton.setOnClickListener(v -> beginAccessSetup());\n        LinearLayout.LayoutParams setupParams = new LinearLayout.LayoutParams(-1, dp(52));\n        setupParams.topMargin = dp(9);\n        column.addView(accessSetupButton, setupParams);\n\n        accessSetupStatus = text(\n                xmegaAccessAuthorized\n                        ? (xmegaAccessReady\n                        ? "✓ Age confirmed. XMegaDrive access is ready."\n                        : "Age confirmed. ZEROCHILL will retry XMegaDrive access when needed.")\n                        : "One confirmation prepares both ZEROCHILL 18+ access and XMegaDrive.",\n                12.5f,\n                color(R.color.zc_text_muted),\n                false\n        );\n        accessSetupStatus.setGravity(Gravity.CENTER);\n        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);\n        statusParams.topMargin = dp(10);\n        column.addView(accessSetupStatus, statusParams);\n        return scroll;\n    }\n\n    private void beginAccessSetup() {\n        if (accessSetupInProgress || xmegaAccessAuthorized && xmegaAccessReady) return;\n        ageConfirmed = true;\n        xmegaAccessAuthorized = true;\n        accessSetupInProgress = true;\n        if (accessSetupButton != null) {\n            accessSetupButton.setText("SETTING UP…");\n            accessSetupButton.setEnabled(false);\n            accessSetupButton.setAlpha(0.78f);\n        }\n        if (accessSetupStatus != null) {\n            accessSetupStatus.setText("Preparing XMegaDrive access on this device…");\n        }\n        updateNextButtonState();\n        XMegaDriveAgeSession.authorize(this, ready -> runOnUiThread(() -> {\n            if (isFinishing() || isDestroyed()) return;\n            accessSetupInProgress = false;\n            xmegaAccessReady = ready;\n            if (accessSetupButton != null) {\n                accessSetupButton.setText(ready ? "✓ ACCESS READY" : "RETRY XMEGADRIVE SETUP");\n                accessSetupButton.setEnabled(!ready);\n                accessSetupButton.setAlpha(1f);\n            }\n            if (accessSetupStatus != null) {\n                accessSetupStatus.setText(ready\n                        ? "✓ Age confirmed. XMegaDrive access is ready."\n                        : "Age confirmed. ZEROCHILL will retry XMegaDrive access when needed.");\n            }\n            updateNextButtonState();\n        }));\n    }\n'''
replace(path, old_checkbox, new_access)
replace(path,
'''        boolean enabled = page != PAGE_ACCESS || ageConfirmed;''',
'''        boolean enabled = page != PAGE_ACCESS\n                || (ageConfirmed && xmegaAccessAuthorized && !accessSetupInProgress);''')
replace(path,
'''        AccessNoticeDialog.markAccepted(this);\n        GestureGuideDialog.markSeen(this);''',
'''        AccessNoticeDialog.markAccepted(this);\n        if (xmegaAccessAuthorized) XMegaDriveAgeSession.markSetupComplete(this);\n        GestureGuideDialog.markSeen(this);''')
replace(path,
'''            if (page == PAGE_ACCESS && !ageConfirmed) return;''',
'''            if (page == PAGE_ACCESS\n                    && (!ageConfirmed || !xmegaAccessAuthorized || accessSetupInProgress)) return;''')

# Startup routing: fresh install wizard first, existing installs get one repair screen.
path = 'app/src/main/java/com/webapp/crazyshit/SplashActivity.java'
replace(path,
'''    private boolean startupWizard;\n    private String launchAction;''',
'''    private boolean startupWizard;\n    private boolean xmegaAccessRepair;\n    private String launchAction;''')
replace(path,
'''        startupWizard = StartupWizardPolicy.shouldShow(this);\n        chaosHandoff = shouldPreload(startupWizard, launchAction);''',
'''        startupWizard = StartupWizardPolicy.shouldShow(this);\n        xmegaAccessRepair = !startupWizard && XMegaDriveAgeSession.shouldShowRepair(this);\n        chaosHandoff = shouldPreload(startupWizard || xmegaAccessRepair, launchAction);''')
replace(path,
'''        Intent intent = new Intent(\n                this,\n                startupWizard ? StartupWizardActivity.class : NativeMainActivity.class\n        );''',
'''        Class<?> destination = startupWizard\n                ? StartupWizardActivity.class\n                : (xmegaAccessRepair ? XMegaDriveAccessSetupActivity.class : NativeMainActivity.class);\n        Intent intent = new Intent(this, destination);''')
replace(path,
'''            if (!startupWizard) {\n                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);\n            }''',
'''            if (!startupWizard && !xmegaAccessRepair) {\n                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);\n            }''')

# Manifest route for one-time repair activity.
path = 'app/src/main/AndroidManifest.xml'
replace(path,
'''        <activity\n            android:name=".StartupWizardActivity"\n            android:screenOrientation="portrait"\n            android:enableOnBackInvokedCallback="true"\n            android:exported="false" />\n''',
'''        <activity\n            android:name=".StartupWizardActivity"\n            android:screenOrientation="portrait"\n            android:enableOnBackInvokedCallback="true"\n            android:exported="false" />\n\n        <activity\n            android:name=".XMegaDriveAccessSetupActivity"\n            android:screenOrientation="portrait"\n            android:enableOnBackInvokedCallback="true"\n            android:exported="false" />\n''')

# Browser path: cookie first, native direct consent if needed, raw site only for human challenges.
path = 'app/src/main/java/com/webapp/crazyshit/XMegaDriveBrowserFetch.java'
replace(path,
'''    private static final long EXTRACTION_RETRY_MS = 800L;\n    private static final long NATIVE_AGE_FALLBACK_GRACE_MS = 6500L;\n    private static final long NATIVE_AGE_TARGET_DISCOVERY_TIMEOUT_MS = 12000L;\n    private static final long NATIVE_AGE_TARGET_RETRY_MS = 300L;''',
'''    private static final long EXTRACTION_RETRY_MS = 800L;''')
replace(path,
'''            CookieManager cookies = CookieManager.getInstance();\n            cookies.setAcceptCookie(true);''',
'''            XMegaDriveAgeSession.restoreIfAuthorized(activity);\n            CookieManager cookies = CookieManager.getInstance();\n            cookies.setAcceptCookie(true);''')
replace(path,
'''            WebView webView = new WebView(activity);\n            activeWebView.set(webView);''',
'''            WebView webView = new WebView(activity);\n            webView.setTag(new AgeGateState());\n            activeWebView.set(webView);''')
text = (ROOT / path).read_text(encoding='utf-8')
text = text.replace('''                XMegaDriveAgePrompt prompt = agePrompt(webView);\n                if (prompt != null) prompt.showRawFallback();\n                reveal(overlay, webView);''',
'''                dismissAgePrompt(webView);\n                reveal(overlay, webView);''')
text = text.replace('''                    XMegaDriveAgePrompt prompt = agePrompt(webView);\n                    if (prompt != null) prompt.showRawFallback();\n                    reveal(overlay, webView);''',
'''                    dismissAgePrompt(webView);\n                    reveal(overlay, webView);''')
text = text.replace('''                + ageGateSafetyScript()''',
'''                + "const age=/age confirmation/i.test(bodyText);"''')
old_ready = '''                    Runnable finishReady = () -> {\n                        result.set(document);\n                        complete(completed, error, finished, null);\n                    };\n                    if (BuildConfig.DEBUG && XMegaDriveAgeDiagnostics.hasBefore(webView)) {\n                        XMegaDriveAgeDiagnostics.captureAfterAndShow(\n                                webView,\n                                webView.getUrl(),\n                                finishReady\n                        );\n                    } else {\n                        finishReady.run();\n                    }\n                    return;'''
new_ready = '''                    result.set(document);\n                    complete(completed, error, finished, null);\n                    return;'''
if old_ready not in text:
    raise SystemExit('missing diagnostic ready block')
text = text.replace(old_ready, new_ready)
old_age = '''                if (age && attempt >= 4) {\n                    XMegaDriveAgePrompt prompt = agePrompt(webView);\n                    if (prompt == null) {\n                        prompt = showNativeAgePrompt(webView, overlay, completed, error, finished);\n                    }\n                    if (prompt == null) {\n                        revealRawAgeFallback(overlay, webView);\n                    } else if (!prompt.isRawFallbackShown()\n                            && prompt.hasContinueBeenPressed()\n                            && prompt.millisSinceContinue() >= NATIVE_AGE_FALLBACK_GRACE_MS) {\n                        prompt.showRawFallback();\n                        revealRawAgeFallback(overlay, webView);\n                    }\n                }'''
new_age = '''                if (age && attempt >= 2\n                        && handleAgeGate(webView, completed, error, finished)) {\n                    return;\n                }'''
if old_age not in text:
    raise SystemExit('missing age fallback block')
text = text.replace(old_age, new_age)
(ROOT / path).write_text(text, encoding='utf-8')

regex_replace(path,
    r'/\*\*\n \* Keep the reliable 5\.2\.1 visible fallback, but make every known age-decline.*?\n    private static boolean needsPerson',
    r'''private static final class AgeGateState {
        XMegaDriveAgePrompt prompt;
        boolean autoRepairAttempted;
    }

    private static AgeGateState ageState(WebView webView) {
        if (webView == null) return null;
        Object tag = webView.getTag();
        if (tag instanceof AgeGateState) return (AgeGateState) tag;
        AgeGateState state = new AgeGateState();
        webView.setTag(state);
        return state;
    }

    private static XMegaDriveAgePrompt agePrompt(WebView webView) {
        AgeGateState state = ageState(webView);
        return state == null ? null : state.prompt;
    }

    private static boolean handleAgeGate(
            WebView webView,
            AtomicBoolean completed,
            AtomicReference<IOException> error,
            CountDownLatch finished
    ) {
        if (webView == null || completed.get()) return true;
        Activity host = activity(webView.getContext());
        if (host == null || host.isFinishing() || host.isDestroyed()) {
            complete(completed, error, finished,
                    new IOException("XMegaDrive age confirmation is unavailable"));
            return true;
        }
        AgeGateState state = ageState(webView);
        if (state != null
                && !state.autoRepairAttempted
                && AccessNoticeDialog.isAccepted(host)
                && XMegaDriveAgeSession.isSetupComplete(host)) {
            state.autoRepairAttempted = true;
            XMegaDriveAgeSession.authorize(host, ready -> host.runOnUiThread(() -> {
                if (completed.get() || webView.getParent() == null) return;
                if (ready) {
                    webView.reload();
                } else {
                    showNativeAgePrompt(webView, completed, error, finished);
                }
            }));
            return true;
        }
        if (agePrompt(webView) == null) {
            showNativeAgePrompt(webView, completed, error, finished);
        }
        return true;
    }

    private static XMegaDriveAgePrompt showNativeAgePrompt(
            WebView webView,
            AtomicBoolean completed,
            AtomicReference<IOException> error,
            CountDownLatch finished
    ) {
        if (webView == null || completed.get()) return null;
        AgeGateState state = ageState(webView);
        if (state != null && state.prompt != null) return state.prompt;
        Activity host = activity(webView.getContext());
        if (host == null || host.isFinishing() || host.isDestroyed()) return null;
        XMegaDriveAgePrompt created = new XMegaDriveAgePrompt(
                host,
                prompt -> {
                    XMegaDriveAgeSession.markSetupComplete(host);
                    XMegaDriveAgeSession.authorize(host, ready -> host.runOnUiThread(() -> {
                        if (completed.get() || webView.getParent() == null) return;
                        if (!ready) {
                            prompt.showFailure();
                            return;
                        }
                        prompt.showReady();
                        dismissAgePrompt(webView);
                        webView.reload();
                    }));
                },
                () -> complete(completed, error, finished,
                        new IOException("XMegaDrive age access was not confirmed"))
        );
        if (state != null) state.prompt = created;
        created.show();
        return created;
    }

    private static void dismissAgePrompt(WebView webView) {
        AgeGateState state = ageState(webView);
        if (state == null || state.prompt == null) return;
        state.prompt.dismiss();
        state.prompt = null;
    }

    private static boolean needsPerson''')

# Update BrowserFetch class comment to describe the new rule.
replace(path,
''' * only brought on-screen when a person has to tap a challenge or the age check.\n * The "; wv" token is removed from the user-agent string.''',
''' * only brought on-screen for a human/browser challenge. XMegaDrive age access is\n * handled by ZEROCHILL's explicit 18+ confirmation and the site's kt_agecheck marker.\n * The "; wv" token is removed from the user-agent string.''')

# Benchmark startup wizard now uses the unified access CTA.
path = 'baselineprofile/src/main/java/com/webapp/crazyshit/baselineprofile/CriticalUserJourneys.kt'
regex_replace(path,
    r'''    val age = checkNotNull\(\n        device\.wait\(Until\.findObject\(By\.text\("I confirm that I am 18 or older\."\)\), 4_000\)\n    \) \{\n        "Startup wizard age confirmation did not appear"\n    \}\n    if \(!age\.isChecked\) age\.click\(\)\n    device\.waitForIdle\(150\)\n    checkNotNull\(device\.findObject\(By\.text\("CONTINUE"\)\)\) \{\n        "Startup wizard age Continue action was not enabled"\n    \}\.click\(\)''',
    '''    checkNotNull(
        device.wait(Until.findObject(By.text("CONFIRM 18+ & SET UP ACCESS")), 4_000)
    ) {
        "Startup wizard unified 18+ access action did not appear"
    }.click()
    device.waitForIdle(250)
    checkNotNull(device.wait(Until.findObject(By.text("CONTINUE")), 4_000)) {
        "Startup wizard age Continue action was not enabled"
    }.click()''')

# Pure regression tests for the marker, repair policy, and native prompt.
write('app/src/test/java/com/webapp/crazyshit/XMegaDriveAgeGateSafetyTest.java', r'''
package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XMegaDriveAgeGateSafetyTest {
    @Test public void directCookieUsesOnlyDiscoveredAgeMarker() {
        String directive = XMegaDriveAgeSession.cookieDirective();
        assertTrue(directive.startsWith("kt_agecheck=1;"));
        assertTrue(directive.contains("Max-Age=31536000"));
        assertTrue(directive.contains("Path=/"));
        assertTrue(directive.contains("SameSite=Lax"));
        assertTrue(directive.contains("Secure"));
        assertFalse(directive.contains("cf_clearance"));
        assertFalse(directive.contains("__cf_bm"));
    }

    @Test public void markerRequiresExactNameAndValue() {
        assertTrue(XMegaDriveAgeSession.hasAgeCookieHeader("foo=a; kt_agecheck=1; bar=b"));
        assertFalse(XMegaDriveAgeSession.hasAgeCookieHeader("kt_agecheck=0"));
        assertFalse(XMegaDriveAgeSession.hasAgeCookieHeader("other_kt_agecheck=1"));
        assertFalse(XMegaDriveAgeSession.hasAgeCookieHeader(null));
    }
}
''')

write('app/src/test/java/com/webapp/crazyshit/XMegaDriveAgePromptTest.java', r'''
package com.webapp.crazyshit;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class XMegaDriveAgePromptTest {
    @Test public void continueIsNativeClickableAndNeverExposesSiteExit() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AtomicInteger continued = new AtomicInteger();
        AtomicInteger cancelled = new AtomicInteger();
        XMegaDriveAgePrompt prompt = new XMegaDriveAgePrompt(
                activity,
                ignored -> continued.incrementAndGet(),
                cancelled::incrementAndGet
        );

        prompt.show();
        View root = prompt.rootForTest();
        TextView title = root.findViewWithTag(XMegaDriveAgePrompt.TAG_TITLE);
        TextView button = root.findViewWithTag(XMegaDriveAgePrompt.TAG_CONTINUE);
        TextView cancel = root.findViewWithTag(XMegaDriveAgePrompt.TAG_CANCEL);

        assertNotNull(title);
        assertNotNull(button);
        assertNotNull(cancel);
        assertEquals("Set up XMegaDrive access", title.getText().toString());
        assertEquals("CONFIRM 18+ & CONTINUE", button.getText().toString());
        assertTrue(button.isEnabled());
        assertTrue(button.isClickable());
        assertFalse(containsText(root, "EXIT"));

        button.performClick();
        assertEquals(1, continued.get());
        assertEquals(0, cancelled.get());
        assertEquals("SETTING UP…", button.getText().toString());
        assertFalse(button.isEnabled());

        prompt.showFailure();
        assertEquals("TRY AGAIN", button.getText().toString());
        assertTrue(button.isEnabled());
        prompt.dismiss();
    }

    @Test public void notNowCancelsWithoutConfirming() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AtomicInteger continued = new AtomicInteger();
        AtomicInteger cancelled = new AtomicInteger();
        XMegaDriveAgePrompt prompt = new XMegaDriveAgePrompt(
                activity,
                ignored -> continued.incrementAndGet(),
                cancelled::incrementAndGet
        );
        prompt.show();
        TextView cancel = prompt.rootForTest().findViewWithTag(XMegaDriveAgePrompt.TAG_CANCEL);
        assertNotNull(cancel);
        cancel.performClick();
        assertEquals(0, continued.get());
        assertEquals(1, cancelled.get());
        prompt.dismiss();
    }

    private static boolean containsText(View view, String needle) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && value.toString().toUpperCase().contains(needle)) return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                if (containsText(group.getChildAt(index), needle)) return true;
            }
        }
        return false;
    }
}
''')

write('app/src/test/java/com/webapp/crazyshit/XMegaDriveAgeSessionTest.java', r'''
package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XMegaDriveAgeSessionTest {
    @Test public void existingAcceptedInstallGetsOneRepairWhenMarkerMissing() {
        assertTrue(XMegaDriveAgeSession.shouldShowRepairFromState(true, false, false, false));
    }

    @Test public void freshOrHandledOrAlreadyReadyInstallSkipsRepair() {
        assertFalse(XMegaDriveAgeSession.shouldShowRepairFromState(false, false, false, false));
        assertFalse(XMegaDriveAgeSession.shouldShowRepairFromState(true, true, false, false));
        assertFalse(XMegaDriveAgeSession.shouldShowRepairFromState(true, false, true, false));
        assertFalse(XMegaDriveAgeSession.shouldShowRepairFromState(true, false, false, true));
    }
}
''')

# Remove the one-time diagnostic source/test now that the marker is known.
for obsolete in [
    'app/src/main/java/com/webapp/crazyshit/XMegaDriveAgeDiagnostics.java',
    'app/src/test/java/com/webapp/crazyshit/XMegaDriveAgeDiagnosticsTest.java',
]:
    p = ROOT / obsolete
    if p.exists():
        p.unlink()

# Add a focused wizard UI regression without depending on CookieManager callback timing.
path = 'app/src/test/java/com/webapp/crazyshit/StartupWizardUiTest.java'
text = (ROOT / path).read_text(encoding='utf-8')
insert = r'''
    @Test public void accessPageUsesUnifiedSetupActionInsteadOfCheckbox() throws Exception {
        StartupWizardActivity activity = create();
        Method showPage = StartupWizardActivity.class.getDeclaredMethod(
                "showPage", int.class, boolean.class);
        showPage.setAccessible(true);
        showPage.invoke(activity, 1, true);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        View root = activity.getWindow().getDecorView();
        assertNotNull(find(root, "CONFIRM 18+ & SET UP ACCESS"));
        assertNull(find(root, "I confirm that I am 18 or older."));
    }

'''
needle = '    @Test public void finalPageOffersOptionalCreateAndSignInEntries() throws Exception {'
if needle not in text:
    raise SystemExit('missing StartupWizardUiTest insertion point')
text = text.replace(needle, insert + needle)
(ROOT / path).write_text(text, encoding='utf-8')

print('XMegaDrive age-cookie patch applied')
