package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** One-to-one ZEROCHILL direct-message thread. */
public final class ZeroChillMessageActivity extends Activity {
    static final String EXTRA_USER_ID = "zerochill_dm_user_id";
    private static final long REFRESH_MS = 2500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!resumed) return;
            loadThread(false);
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    interface MessageSender {
        void send(Context context, String recipient, String clientId, String body,
                  ZeroChillSocialRepository.Callback<ZeroChillSocialRepository.DirectMessage> callback);
    }

    interface ThreadLoader {
        void load(Context context, String partner,
                  ZeroChillSocialRepository.Callback<ArrayList<ZeroChillSocialRepository.DirectMessage>> callback);
    }

    private MessageSender messageSender = ZeroChillSocialRepository::sendDirectMessage;
    private ThreadLoader threadLoader = ZeroChillSocialRepository::loadDirectMessages;

    private String partnerId = "";
    private String renderedUser = "";
    private ZeroChillSocialRepository.PublicProfile partner;
    private boolean blockedByMe;
    private boolean resumed;
    private boolean loading;
    private boolean sending;
    private boolean refreshQueued;
    private long threadRevision;
    private int partnerRequestGeneration;
    private String lastMessageId = "";

    private TextView headerBack;
    private ImageView headerMore;
    private LinearLayout selectionActions;
    private ZeroChillSocialRepository.DirectMessage selectedMessage;
    private android.widget.PopupWindow reactionPopup;
    private ImageView avatar;
    private TextView title;
    private TextView subtitle;
    private RecyclerView recycler;
    private MessageAdapter adapter;
    private ProgressBar progress;
    private LinearLayout threadState;
    private TextView threadStateText;
    private TextView retryThread;
    private EditText composer;
    private ImageView send;
    private SocialMedia.Composer attachment;
    private String retryId = "";
    private String retryBody = "";
    private SocialMedia.Prepared retryMedia;
    private final java.util.Map<String,String> reactionLabels = new java.util.HashMap<>();
    private boolean reactionsLoading;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ZeroChillUi.applySystemBars(this);
        partnerId = clean(getIntent().getStringExtra(EXTRA_USER_ID));
        if (partnerId.isEmpty() || !ZeroChillAccountRepository.hasStoredSession(this)) {
            if (!ZeroChillAccountRepository.hasStoredSession(this)) {
                startActivity(new Intent(this, ZeroChillAccountActivity.class));
            }
            finish();
            return;
        }
        renderedUser = ZeroChillSessionStore.currentUserId(this);
        buildUi();
        ResponsiveFitmentController.applySoon(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!accountStillCurrent()) {
            finish();
            return;
        }
        resumed = true;
        if (avatar != null && !renderedUser.isEmpty()) loadPartner();
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, REFRESH_MS);
    }

    @Override
    protected void onPause() {
        clearSelection();
        resumed = false;
        handler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        clearSelection();
        handler.removeCallbacksAndMessages(null);
        ResponsiveFitmentController.release(this);
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppearanceTheme.background(this));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(4), dp(8), dp(4));

        TextView back = text("‹", 31, AppearanceTheme.primaryText(this), false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        headerBack=back;
        back.setOnClickListener(v -> {if(selectedMessage!=null)clearSelection();else finish();});
        ZeroChillMotion.installPressFeedback(back);
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setImageResource(R.drawable.ic_more_account);
        avatar.setPadding(dp(8), dp(8), dp(8), dp(8));
        avatar.setColorFilter(AppearanceTheme.secondaryText(ZeroChillMessageActivity.this));
        avatar.setOnClickListener(v -> openProfile());
        header.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(10), 0, dp(6), 0);
        labels.setOnClickListener(v -> {if(selectedMessage==null)openProfile();});
        title = text("Loading…", 16, AppearanceTheme.primaryText(this), true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(title);
        subtitle = text("ZEROCHILL DM", 11, ZeroChillUi.color(this, R.color.zc_text_muted), false);
        labels.addView(subtitle);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        selectionActions=new LinearLayout(this);selectionActions.setVisibility(View.GONE);
        header.addView(selectionActions);
        ImageView more = new ImageView(this);
        headerMore=more;
        more.setImageResource(R.drawable.ic_more_overflow);
        more.setColorFilter(AppearanceTheme.primaryText(this));
        more.setPadding(dp(9), dp(9), dp(9), dp(9));
        more.setContentDescription("Conversation options");
        more.setOnClickListener(this::showOptions);
        ZeroChillMotion.installPressFeedback(more);
        header.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));

        root.addView(header, new LinearLayout.LayoutParams(-1, dp(56)));

        recycler = new RecyclerView(this);
        LinearLayoutManager manager = new LinearLayoutManager(this);
        manager.setStackFromEnd(true);
        recycler.setLayoutManager(manager);
        recycler.setItemAnimator(null);
        recycler.setClipToPadding(false);
        recycler.setPadding(dp(12), dp(10), dp(12), dp(12));
        adapter = new MessageAdapter();
        recycler.setAdapter(adapter);
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener(){@Override public void onScrollStateChanged(RecyclerView r,int state){if(state!=RecyclerView.SCROLL_STATE_IDLE)clearSelection();}});
        FrameLayout threadBody = new FrameLayout(this);
        threadBody.addView(recycler, new FrameLayout.LayoutParams(-1, -1));
        root.addView(threadBody, new LinearLayout.LayoutParams(-1, 0, 1f));

        progress = new ZeroChillProgressBar(this);
        ZeroChillUi.styleProgress(progress);
        progress.setVisibility(View.VISIBLE);
        threadBody.addView(progress, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));

        threadState = new LinearLayout(this);
        threadState.setOrientation(LinearLayout.VERTICAL);
        threadState.setGravity(Gravity.CENTER);
        threadState.setPadding(dp(24), dp(24), dp(24), dp(24));
        threadState.setVisibility(View.GONE);
        threadStateText = text("", 14, AppearanceTheme.secondaryText(ZeroChillMessageActivity.this), false);
        threadStateText.setGravity(Gravity.CENTER);
        threadStateText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        threadState.addView(threadStateText, new LinearLayout.LayoutParams(-1, -2));
        retryThread = text("Try again", 14, AppearanceTheme.primaryText(ZeroChillMessageActivity.this), true);
        ZeroChillUi.styleAction(retryThread, false);
        retryThread.setPadding(dp(16), 0, dp(16), 0);
        retryThread.setOnClickListener(v -> loadThread(true));
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(-2, dp(48));
        retryParams.topMargin = dp(16);
        threadState.addView(retryThread, retryParams);
        threadBody.addView(threadState, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        LinearLayout compose = new LinearLayout(this);
        compose.setGravity(Gravity.BOTTOM | Gravity.CENTER_VERTICAL);
        compose.setPadding(dp(10), dp(7), dp(10), dp(10));
        compose.setBackgroundColor(AppearanceTheme.surface(this));

        composer = new ZeroChillEditText(this);
        composer.setHint("Message @" + partnerId);
        composer.setHintTextColor(Color.rgb(116, 122, 132));
        composer.setTextColor(AppearanceTheme.primaryText(this));
        composer.setTextSize(14);
        composer.setMinLines(1);
        composer.setMaxLines(5);
        composer.setPadding(dp(14), dp(10), dp(14), dp(10));
        composer.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
        composer.setBackground(inputBackground());
        composer.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) return;
            recycler.postDelayed(() -> {
                int count = adapter == null ? 0 : adapter.getItemCount();
                if (count > 0) recycler.scrollToPosition(count - 1);
            }, 180L);
        });
        compose.addView(composer, new LinearLayout.LayoutParams(0, -2, 1f));

        send = new ImageView(this);
        send.setImageResource(R.drawable.ic_action_send);
        send.setColorFilter(Color.rgb(5, 19, 28));
        send.setPadding(dp(12), dp(12), dp(12), dp(12));
        send.setBackground(circle(AppearanceTheme.accent(ZeroChillMessageActivity.this)));
        send.setContentDescription("Send message");
        send.setOnClickListener(v -> sendMessage());
        ZeroChillMotion.installPressFeedback(send);
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        sendParams.setMarginStart(dp(8));
        compose.addView(send, sendParams);

        attachment=new SocialMedia.Composer(this);
        compose.addView(attachment.control,1);
        attachment.changed=this::updateSendReady;
        attachment.enableKeyboard(composer, () -> !blockedByMe && !sending);
        composer.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){updateSendReady();}public void afterTextChanged(android.text.Editable e){}});
        updateSendReady();root.addView(attachment);
        root.addView(compose);
        setContentView(root);
    }

    private void loadPartner() {
        final int generation = ++partnerRequestGeneration;
        ZeroChillSocialRepository.loadProfile(this, partnerId, (profile, error) -> runOnUiThread(() -> {
            if (generation != partnerRequestGeneration || !accountStillCurrent()
                    || isFinishing() || isDestroyed()) return;
            if (error != null || profile == null || profile.currentUser) {
                ZeroChillToast.showAfterNavigation(
                        this,
                        error == null ? "This conversation is unavailable." : error.getMessage(),
                        ZeroChillToast.LENGTH_LONG
                );
                finish();
                return;
            }
            partner = profile;
            String label = profile.displayName.isEmpty()
                    ? "@" + profile.username
                    : profile.displayName;
            if(selectedMessage==null)title.setText(label);
            subtitle.setText("@" + profile.username);
            composer.setHint("Message @" + profile.username);
            AccountAvatarImages.track(avatar, profile.userId);
            String url = ZeroChillAccountRepository.avatarUrl(profile.avatarPath);
            if (url.isEmpty()) {
                avatar.setImageResource(R.drawable.ic_more_account);
                avatar.setPadding(dp(8), dp(8), dp(8), dp(8));
                avatar.setColorFilter(AppearanceTheme.secondaryText(ZeroChillMessageActivity.this));
            } else {
                avatar.setPadding(0, 0, 0, 0);
                avatar.clearColorFilter();
                AccountAvatarImages.bind(avatar, profile.userId, profile.avatarPath);
            }
            loadBlockState();
            loadThread(true);
        }));
    }

    private void loadThread(boolean showLoading) {
        if (loading) {
            refreshQueued = true;
            return;
        }
        loading = true;
        loadReactions();
        long revisionAtStart = threadRevision;
        if (showLoading && adapter.getItemCount() == 0) {
            threadState.setVisibility(View.GONE);
            progress.setVisibility(View.VISIBLE);
        }
        threadLoader.load(
                this,
                partnerId,
                (items, error) ->
                        runOnUiThread(
                                () -> {
                                    if (!accountStillCurrent() || isFinishing() || isDestroyed())
                                        return;
                                    loading = false;
                                    progress.setVisibility(View.GONE);
                                    boolean stale = revisionAtStart != threadRevision;
                                    boolean rerun = refreshQueued || stale;
                                    refreshQueued = false;
                                    if (error != null || items == null) {
                                        if (adapter.getItemCount() == 0) {
                                            showThreadState(
                                                    "Couldn't load messages. Check your connection"
                                                        + " and try again.",
                                                    true);
                                        } else if (showLoading) {
                                            ZeroChillToast.makeText(
                                                            this,
                                                            "Couldn't refresh messages.",
                                                            ZeroChillToast.LENGTH_LONG)
                                                    .show();
                                        }
                                        if (rerun) loadThread(false);
                                        return;
                                    }
                                    if (stale) {
                                        loadThread(false);
                                        return;
                                    }
                                    String newest =
                                            items.isEmpty() ? "" : items.get(items.size() - 1).id;
                                    boolean changed =
                                            !newest.equals(lastMessageId)
                                                    || adapter.getItemCount() != items.size();
                                    boolean nearBottom = !recycler.canScrollVertically(1);
                                    adapter.replace(items);
                                    if (adapter.getItemCount() == 0)
                                        showThreadState(
                                                "No messages yet. Send a message to start the"
                                                    + " conversation.",
                                                false);
                                    else threadState.setVisibility(View.GONE);
                                    lastMessageId = newest;
                                    if (changed && !items.isEmpty() && nearBottom) {
                                        recycler.scrollToPosition(items.size() - 1);
                                    }
                                    boolean hasUnreadIncoming = false;
                                    for (ZeroChillSocialRepository.DirectMessage item : items) {
                                        if (partnerId.equals(item.senderId)
                                                && item.readAt.isEmpty()) {
                                            hasUnreadIncoming = true;
                                            break;
                                        }
                                    }
                                    if (hasUnreadIncoming) {
                                        ZeroChillSocialRepository.markDirectMessagesRead(
                                                this,
                                                partnerId,
                                                (ignored, markError) -> {
                                                    if (markError == null)
                                                        ZeroChillMessageBadgeStore.refresh(this);
                                                });
                                    }
                                    if (rerun) loadThread(false);
                                }));
    }

    private void showThreadState(String message, boolean retry) {
        if (adapter.getItemCount() > 0) {
            threadState.setVisibility(View.GONE);
            return;
        }
        if (!message.contentEquals(threadStateText.getText())) threadStateText.setText(message);
        retryThread.setVisibility(retry ? View.VISIBLE : View.GONE);
        threadState.setVisibility(View.VISIBLE);
    }

    private boolean accountStillCurrent() {
        return renderedUser.isEmpty() || renderedUser.equals(ZeroChillSessionStore.currentUserId(this));
    }

    private void sendMessage() {
        if (!accountStillCurrent()) { finish(); return; }
        if (blockedByMe) {
            ZeroChillToast.makeText(
                            this,
                            "Unblock this user before messaging them.",
                            ZeroChillToast.LENGTH_SHORT)
                    .show();
            return;
        }
        String value = composer.getText().toString();
        if ((value.trim().isEmpty() && attachment.selected==null) || sending || attachment.preparing || !send.isEnabled()) return;
        if(!value.equals(retryBody) || retryMedia!=attachment.selected) retryId="";
        retryBody=value; retryMedia=attachment.selected;
        String clientId = retryId.isEmpty() ? UUID.randomUUID().toString() : retryId;
        retryId=clientId;
        threadRevision++;
        adapter.upsert(new ZeroChillSocialRepository.DirectMessage(clientId, partnerId, value));
        threadState.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        recycler.scrollToPosition(adapter.getItemCount() - 1);
        sending = true;
        attachment.busy(true);
        composer.setEnabled(false);
        send.setEnabled(false);
        // Keep the draft until confirmation so a failed request never loses it.
        ZeroChillSocialRepository.Callback<ZeroChillSocialRepository.DirectMessage> completion = (message, error) ->
                runOnUiThread(() -> {
                    if (!accountStillCurrent() || isFinishing() || isDestroyed()) return;
                    sending = false;
                    attachment.busy(false);
                    composer.setEnabled(!blockedByMe);
                    updateSendReady();
                    if (error != null) {
                        // A refresh may already have confirmed this UUID despite a lost POST response.
                        if (adapter.removePending(clientId)) {
                            threadRevision++;
                            ZeroChillToast.makeText(this, error.getMessage(), ZeroChillToast.LENGTH_LONG).show();
                        } else if (value.equals(composer.getText().toString())) {
                            composer.setText("");
                            attachment.clear(); retryId="";
                        }
                        return;
                    }
                    if (value.equals(composer.getText().toString())) composer.setText("");
                    attachment.clear(); retryId="";
                    threadRevision++;
                    if (message != null) {
                        adapter.upsert(message);
                        lastMessageId = message.id;
                    }
                    recycler.scrollToPosition(adapter.getItemCount() - 1);
                    loadThread(false);
                });
        if (attachment.selected == null) messageSender.send(this,partnerId,clientId,value,completion);
        else ZeroChillSocialRepository.sendDirectMessage(this,partnerId,clientId,value,attachment.selected,completion);
    }

    private void loadReactions() {
        if(reactionsLoading) return;
        reactionsLoading=true;
        ZeroChillSocialRepository.loadReactions(this,partnerId,(rows,error) -> runOnUiThread(() -> {
            reactionsLoading=false;
            if(!accountStillCurrent() || isDestroyed() || rows==null) return;
            reactionLabels.clear();
            for(int i=0;i<rows.length();i++) {
                org.json.JSONObject r=rows.optJSONObject(i); if(r==null) continue;
                String id=r.optString("message_id");
                String label=r.optString("emoji")+" "+r.optInt("count")+(r.optBoolean("mine")?" •":"");
                reactionLabels.put(id,reactionLabels.getOrDefault(id,"")+"  "+label);
            }
            adapter.notifyDataSetChanged();
        }));
    }

    private void showReactions(View anchor, ZeroChillSocialRepository.DirectMessage item) {
        if (item.pending || blockedByMe) return;
        clearSelection();
        selectedMessage = item;
        anchor.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        title.setText("1 selected");
        subtitle.setVisibility(View.GONE);
        avatar.setVisibility(View.GONE);
        headerMore.setVisibility(View.GONE);
        headerBack.setText("×");
        headerBack.setContentDescription("Clear message selection");
        selectionActions.removeAllViews();
        selectionActions.setVisibility(View.VISIBLE);
        if (canCopy(item))
            selectionAction(
                    "Copy",
                    () -> {
                        ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                                .setPrimaryClip(
                                        android.content.ClipData.newPlainText(
                                                "Message", item.body));
                        clearSelection();
                    });
        if (partnerId.equals(item.senderId))
            selectionAction(
                    "Report",
                    () -> {
                        clearSelection();
                        showReportDialog(item.id);
                    });
        adapter.notifyDataSetChanged();
        LinearLayout choices = new LinearLayout(this);
        choices.setGravity(Gravity.CENTER);
        choices.setPadding(dp(5), dp(3), dp(5), dp(3));
        choices.setBackground(inputBackground());
        for (String emoji : new String[] {"❤️", "😂", "🔥", "👍", "😮", "👎"}) {
            TextView choice = text(emoji, 24, Color.WHITE, false);
            choice.setGravity(Gravity.CENTER);
            choice.setContentDescription("React " + emoji);
            choices.addView(choice, new LinearLayout.LayoutParams(0, dp(48), 1f));
            choice.setOnClickListener(
                    v -> {
                        clearSelection();
                        ZeroChillSocialRepository.react(
                                this,
                                item.id,
                                emoji,
                                (ok, error) ->
                                        runOnUiThread(
                                                () -> {
                                                    if (!accountStillCurrent() || isDestroyed())
                                                        return;
                                                    if (error != null)
                                                        ZeroChillToast.makeText(
                                                                        this,
                                                                        error.getMessage(),
                                                                        ZeroChillToast.LENGTH_LONG)
                                                                .show();
                                                    else loadReactions();
                                                }));
                    });
        }
        int popupWidth = Math.min(dp(274), getResources().getDisplayMetrics().widthPixels - dp(16));
        reactionPopup = new android.widget.PopupWindow(choices, popupWidth, dp(54), false);
        reactionPopup.setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        reactionPopup.setOutsideTouchable(true);
        reactionPopup.setElevation(dp(12));
        reactionPopup.setOnDismissListener(this::clearSelection);
        int[] point = new int[2];
        anchor.getLocationOnScreen(point);
        android.graphics.Rect bounds = new android.graphics.Rect();
        anchor.getWindowVisibleDisplayFrame(bounds);
        int x =
                Math.max(
                        bounds.left + dp(8), Math.min(point[0], bounds.right - popupWidth - dp(8)));
        int y = point[1] - dp(60);
        if (y < bounds.top + dp(56))
            y = Math.min(point[1] + anchor.getHeight() + dp(6), bounds.bottom - dp(60));
        reactionPopup.showAtLocation(
                recycler, Gravity.TOP | Gravity.LEFT, x, Math.max(bounds.top, y));
    }

    static boolean canCopy(ZeroChillSocialRepository.DirectMessage item) {
        return !item.body.trim().isEmpty()
                && (item.attachmentPath.isEmpty()
                        || (!item.body.equals("Photo") && !item.body.equals("GIF")));
    }

    private void selectionAction(String label, Runnable action) {
        TextView button = text(label, 13, AppearanceTheme.accent(ZeroChillMessageActivity.this), true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setMinHeight(dp(48));
        button.setOnClickListener(v -> action.run());
        selectionActions.addView(button);
    }

    private void clearSelection() {
        android.widget.PopupWindow popup = reactionPopup;
        reactionPopup = null;
        if (popup != null) {
            popup.setOnDismissListener(null);
            popup.dismiss();
        }
        if (selectedMessage == null) return;
        selectedMessage = null;
        headerBack.setText("‹");
        headerBack.setContentDescription("Back");
        avatar.setVisibility(View.VISIBLE);
        subtitle.setVisibility(View.VISIBLE);
        headerMore.setVisibility(View.VISIBLE);
        selectionActions.setVisibility(View.GONE);
        title.setText(
                partner == null
                        ? "Loading…"
                        : partner.displayName.isEmpty()
                                ? "@" + partner.username
                                : partner.displayName);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    public void onBackPressed() {
        if (selectedMessage != null) clearSelection();
        else super.onBackPressed();
    }

    private void updateSendReady() {
        if (send == null || attachment == null) return;
        attachment.control.setEnabled(!blockedByMe && !sending && !attachment.preparing);
        boolean ready =
                !blockedByMe
                        && !sending
                        && !attachment.preparing
                        && (!composer.getText().toString().trim().isEmpty()
                                || attachment.selected != null);
        send.setEnabled(ready);
        send.setAlpha(ready ? 1f : 0.35f);
    }

    private void loadBlockState() {
        ZeroChillSocialRepository.blockState(this, partnerId, (blocked, error) ->
                runOnUiThread(() -> {
                    if (!accountStillCurrent() || isFinishing() || isDestroyed()) return;
                    if (error == null) {
                        blockedByMe = Boolean.TRUE.equals(blocked);
                        updateComposerState();
                    }
                })
        );
    }

    private void updateComposerState() {
        composer.setEnabled(!blockedByMe);
        updateSendReady();
        composer.setHint(blockedByMe
                ? "You blocked this user"
                : "Message @" + (partner == null ? "" : partner.username));
        attachment.control.setEnabled(!blockedByMe&&!sending&&!attachment.preparing);
        if(blockedByMe)clearSelection();
        updateSendReady();
    }

    private void showOptions(View anchor) {
        ZeroChillMenu menu = new ZeroChillMenu(this, anchor);
        menu.getMenu().add("View profile");
        menu.getMenu().add(blockedByMe ? "Unblock user" : "Block user");
        menu.getMenu().add("Report user");
        menu.setOnMenuItemClickListener(item -> {
            String label = String.valueOf(item.getTitle());
            if ("View profile".equals(label)) {
                openProfile();
            } else if ("Block user".equals(label) || "Unblock user".equals(label)) {
                confirmBlock(!blockedByMe);
            } else if ("Report user".equals(label)) {
                showReportDialog("");
            }
            return true;
        });
        menu.show();
    }

    private void confirmBlock(boolean block) {
        String username = partner == null ? "this user" : "@" + partner.username;
        new ZeroChillDialog.Builder(this).destructive()
                .setTitle(block ? "Block " + username + "?" : "Unblock " + username + "?")
                .setMessage(block
                        ? "You won't be able to message each other while blocked."
                        : "You and this user will be able to message each other again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(block ? "Block" : "Unblock", (dialog, which) ->
                        ZeroChillSocialRepository.setBlocked(
                                this,
                                partnerId,
                                block,
                                (value, error) -> runOnUiThread(() -> {
                                    if (error != null) {
                                        ZeroChillToast.makeText(this, error.getMessage(), ZeroChillToast.LENGTH_LONG).show();
                                        return;
                                    }
                                    blockedByMe = Boolean.TRUE.equals(value);
                                    updateComposerState();
                                    ZeroChillToast.makeText(
                                            this,
                                            blockedByMe ? "User blocked." : "User unblocked.",
                                            ZeroChillToast.LENGTH_SHORT
                                    ).show();
                                })
                        ))
                .show();
    }

    private void showReportDialog(String messageId) {
        String[] reasons = {"Spam", "Harassment", "Other"};
        new ZeroChillDialog.Builder(this)
                .setTitle(messageId.isEmpty() ? "Report user" : "Report message")
                .setItems(reasons, (dialog, which) -> {
                    String reason = which == 0 ? "spam" : which == 1 ? "harassment" : "other";
                    ZeroChillSocialRepository.Callback<Boolean> callback =
                            (ok, error) -> runOnUiThread(() -> ZeroChillToast.makeText(
                                    this,
                                    error == null ? "Report submitted." : error.getMessage(),
                                    error == null ? ZeroChillToast.LENGTH_SHORT : ZeroChillToast.LENGTH_LONG
                            ).show());
                    if (messageId.isEmpty()) {
                        ZeroChillSocialRepository.reportUser(this, partnerId, reason, callback);
                    } else {
                        ZeroChillSocialRepository.reportDirectMessage(
                                this,
                                partnerId,
                                messageId,
                                reason,
                                callback
                        );
                    }
                })
                .show();
    }

    private void openProfile() {
        Intent intent = new Intent(this, ZeroChillPublicProfileActivity.class);
        intent.putExtra(ZeroChillPublicProfileActivity.EXTRA_USER_ID, partnerId);
        startActivity(intent);
    }

    private static boolean atOrBefore(String timestamp, String cutoff) {
        try { return !Instant.parse(timestamp).isAfter(Instant.parse(cutoff)); }
        catch (Exception ignored) { return false; }
    }

    // A pause or a local calendar-day boundary starts a new visual group.
    static boolean grouped(ZeroChillSocialRepository.DirectMessage first,
                           ZeroChillSocialRepository.DirectMessage second) {
        if (first == null || second == null || !first.senderId.equals(second.senderId)) return false;
        try {
            Instant a = Instant.parse(first.createdAt);
            Instant b = Instant.parse(second.createdAt);
            long gap = b.toEpochMilli() - a.toEpochMilli();
            ZoneId zone = ZoneId.systemDefault();
            return gap >= 0 && gap <= 300_000L
                    && a.atZone(zone).toLocalDate().equals(b.atZone(zone).toLocalDate());
        } catch (Exception ignored) {
            return false;
        }
    }

    static String separator(ZeroChillSocialRepository.DirectMessage previous,ZeroChillSocialRepository.DirectMessage current){
        try{Instant now=Instant.parse(current.createdAt);ZoneId zone=ZoneId.systemDefault();
            if (previous == null
                    || !Instant.parse(previous.createdAt)
                            .atZone(zone)
                            .toLocalDate()
                            .equals(now.atZone(zone).toLocalDate()))
                return DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
                        .withZone(zone)
                        .format(now);
            if (now.toEpochMilli() - Instant.parse(previous.createdAt).toEpochMilli()
                    > 30 * 60 * 1000L)
                return DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
                        .withZone(zone)
                        .format(now);
        }catch(Exception ignored){}return "";
    }

    private final class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.Holder> {
        private final ArrayList<ZeroChillSocialRepository.DirectMessage> items = new ArrayList<>();

        private final java.util.Map<String, Long> rowIds = new java.util.HashMap<>();
        private long nextRowId;

        MessageAdapter() { setHasStableIds(true); }

        @Override public long getItemId(int position) {
            return rowIds.computeIfAbsent(items.get(position).id, ignored -> nextRowId++);
        }

        @Override public void onViewRecycled(Holder holder) {
            SocialMedia.clear(holder.media);
            holder.media.setOnClickListener(null);
            holder.media.setOnLongClickListener(null);
            super.onViewRecycled(holder);
        }

        // Retain local rows until a refresh includes their UUID, even if that
        // refresh started before Send. Confirmed echoes are retained the same way.
        private final LinkedHashMap<String, ZeroChillSocialRepository.DirectMessage> echoes = new LinkedHashMap<>();

        void replace(List<ZeroChillSocialRepository.DirectMessage> next) {
            if (next instanceof ZeroChillSocialRepository.ClearedMessageList) {
                String cutoff = ((ZeroChillSocialRepository.ClearedMessageList) next).cutoff;
                if (!cutoff.isEmpty()) {
                    echoes.values().removeIf(message -> !message.pending && atOrBefore(message.createdAt, cutoff));
                }
            }
            items.clear();
            if (next != null) {
                for (ZeroChillSocialRepository.DirectMessage message : next) {
                    echoes.remove(message.id);
                    items.add(message);
                }
            }
            items.addAll(echoes.values());
            if(selectedMessage!=null&&items.stream().noneMatch(item->item.id.equals(selectedMessage.id)))clearSelection();
            notifyDataSetChanged();
        }

        void upsert(ZeroChillSocialRepository.DirectMessage message) {
            if (message == null) return;
            for (int i = 0; i < items.size(); i++) {
                if (message.id.equals(items.get(i).id)) {
                    ZeroChillSocialRepository.DirectMessage current = items.get(i);
                    // A refresh can carry a newer read receipt than the POST response.
                    if (!current.pending && !current.readAt.isEmpty() && message.readAt.isEmpty()) {
                        message = current;
                    }
                    echoes.put(message.id, message);
                    items.set(i, message);
                    notifyDataSetChanged(); // Group endings and Seen can change on adjacent rows.
                    return;
                }
            }
            echoes.put(message.id, message);
            items.add(message);
            notifyDataSetChanged();
        }

        boolean removePending(String id) {
            for (int i = 0; i < items.size(); i++) {
                if (id.equals(items.get(i).id) && items.get(i).pending) {
                    items.remove(i);
                    echoes.remove(id);
                    notifyDataSetChanged();
                    return true;
                }
            }
            return false;
        }

        int latestSeenPosition() {
            for (int i = items.size() - 1; i >= 0; i--) {
                ZeroChillSocialRepository.DirectMessage item = items.get(i);
                if (!partnerId.equals(item.senderId) && !item.pending && !item.readAt.isEmpty()) return i;
            }
            return -1;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(ZeroChillMessageActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.BOTTOM);
            LinearLayout outer=new LinearLayout(ZeroChillMessageActivity.this);outer.setOrientation(LinearLayout.VERTICAL);
            outer.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));
            TextView separator = text("", 11, AppearanceTheme.mutedText(ZeroChillMessageActivity.this), false);
            separator.setGravity(Gravity.CENTER);separator.setPadding(0,dp(10),0,dp(10));
            outer.addView(separator,new LinearLayout.LayoutParams(-1,-2));outer.addView(row,new LinearLayout.LayoutParams(-1,-2));

            ImageView senderAvatar = new ImageView(ZeroChillMessageActivity.this);
            senderAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            senderAvatar.setBackground(circle(Color.rgb(24, 27, 33)));
            senderAvatar.setClipToOutline(true);
            senderAvatar.setOnClickListener(v -> openProfile());
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(28), dp(28));
            avatarParams.setMarginEnd(dp(7));
            avatarParams.bottomMargin = dp(2);
            row.addView(senderAvatar, avatarParams);

            LinearLayout bubble = new LinearLayout(ZeroChillMessageActivity.this);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setPadding(dp(10), dp(6), dp(10), dp(5));

            TextView body = text("", 14, AppearanceTheme.primaryText(ZeroChillMessageActivity.this), false);
            body.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
            // Vertical LinearLayout defaults to MATCH_PARENT width. Its uniform-width
            // second pass freezes the first height while narrowing the text, clipping
            // wrapped lines. Measure both dimensions from the text in the first pass.
            bubble.addView(body, new LinearLayout.LayoutParams(-2, -2));

            ImageView media=SocialMedia.inline(ZeroChillMessageActivity.this,"","",true);
            bubble.addView(media,new LinearLayout.LayoutParams(-2,-2));
            TextView reactions=text("",13,AppearanceTheme.accent(ZeroChillMessageActivity.this),false);
            bubble.addView(reactions,new LinearLayout.LayoutParams(-2,-2));
            TextView time = text("", 10, AppearanceTheme.mutedText(ZeroChillMessageActivity.this), false);
            time.setPadding(0, dp(4), 0, 0);
            LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(-2, -2);
            timeParams.gravity = Gravity.END;
            bubble.addView(time, timeParams);

            LinearLayout messageColumn = new LinearLayout(ZeroChillMessageActivity.this);
            messageColumn.setOrientation(LinearLayout.VERTICAL);
            messageColumn.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
            TextView status =
                    text(
                            "",
                            12,
                            ZeroChillUi.color(ZeroChillMessageActivity.this, R.color.zc_text_muted),
                            false);
            status.setPadding(dp(12), dp(3), dp(12), 0);
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-2, -2);
            statusParams.gravity = Gravity.END;
            messageColumn.addView(status, statusParams);
            row.addView(messageColumn, new LinearLayout.LayoutParams(-2, -2));
            return new Holder(outer, row, separator, senderAvatar, bubble, body, time, status,media,reactions);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            ZeroChillSocialRepository.DirectMessage item = items.get(position);
            boolean incoming = partnerId.equals(item.senderId);
            boolean joinsPrevious = position > 0 && grouped(items.get(position - 1), item);
            boolean joinsNext = position + 1 < items.size() && grouped(item, items.get(position + 1));
            LinearLayout row = holder.row;
            String date=separator(position==0?null:items.get(position-1),item);
            holder.separator.setText(date);holder.separator.setVisibility(date.isEmpty()?View.GONE:View.VISIBLE);
            row.setGravity((incoming ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM);
            ((LinearLayout) holder.bubble.getParent()).setGravity(incoming ? Gravity.LEFT : Gravity.RIGHT);
            // Keep the avatar gutter throughout an incoming group; outgoing rows have no gutter.
            holder.senderAvatar.setVisibility(incoming
                    ? (joinsNext ? View.INVISIBLE : View.VISIBLE) : View.GONE);
            Glide.with(holder.senderAvatar).clear(holder.senderAvatar);
            holder.senderAvatar.setImageDrawable(null);
            holder.senderAvatar.clearColorFilter();
            holder.senderAvatar.setPadding(0, 0, 0, 0);
            AccountAvatarImages.track(holder.senderAvatar, incoming ? partnerId : "");
            if (incoming && !joinsNext) {
                String url =
                        partner == null
                                ? ""
                                : ZeroChillAccountRepository.avatarUrl(partner.avatarPath);
                holder.senderAvatar.setContentDescription(partner == null
                        ? "Sender profile" : "@" + partner.username + " profile");
                if (url.isEmpty()) {
                    holder.senderAvatar.setImageResource(R.drawable.ic_more_account);
                    holder.senderAvatar.setPadding(dp(6), dp(6), dp(6), dp(6));
                    holder.senderAvatar.setColorFilter(AppearanceTheme.secondaryText(ZeroChillMessageActivity.this));
                } else {
                    AccountAvatarImages.bind(holder.senderAvatar, partnerId, partner.avatarPath);
                }
            }
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) row.getLayoutParams();
            params.topMargin = dp(joinsPrevious ? 1 : 5);
            params.bottomMargin = dp(joinsNext ? 0 : 2);
            row.setLayoutParams(params);
            int width = recycler.getWidth() > 0 ? recycler.getWidth()
                    : getResources().getDisplayMetrics().widthPixels;
            int available = width - recycler.getPaddingLeft() - recycler.getPaddingRight();
            // Leave an opposite-side gutter even for long/unbroken text on narrow phones.
            int bodyWidth = Math.max(dp(48), Math.min(dp(280),
                    (int) (available * 0.76f) - dp(24) - (incoming ? dp(35) : 0)));
            holder.body.setMaxWidth(bodyWidth);
            holder.time.setMaxWidth(bodyWidth);
            GradientDrawable background=messageBubble(!incoming,joinsPrevious,joinsNext);
            if(selectedMessage!=null&&selectedMessage.id.equals(item.id)){background.setColor(AppearanceTheme.selectionSurface(ZeroChillMessageActivity.this));background.setStroke(dp(2),AppearanceTheme.accent(ZeroChillMessageActivity.this));}
            holder.bubble.setBackground(background);
            holder.body.setText(item.body);
            holder.body.setVisibility(
                    !item.attachmentPath.isEmpty()
                                    && (item.body.equals("Photo") || item.body.equals("GIF"))
                            ? View.GONE
                            : View.VISIBLE);
            SocialMedia.bind(holder.media,item.attachmentBucket,item.attachmentPath);
            holder.media.setOnClickListener(v -> SocialMedia.showViewer(ZeroChillMessageActivity.this,item.attachmentBucket,item.attachmentPath));
            holder.media.setOnLongClickListener(v -> {showReactions(holder.bubble,item); return true;});
            holder.reactions.setText(reactionLabels.getOrDefault(item.id,""));
            holder.reactions.setVisibility(holder.reactions.length()==0?View.GONE:View.VISIBLE);
            holder.time.setText(formatTime(item.createdAt));
            holder.time.setVisibility(joinsNext ? View.GONE : View.VISIBLE);
            String status = !incoming && item.pending ? "Sending…"
                    : position == latestSeenPosition() ? "Seen" : "";
            holder.status.setText(status);
            holder.status.setVisibility(status.isEmpty() ? View.GONE : View.VISIBLE);
            holder.bubble.animate().cancel();
            boolean settled = item.id.equals(holder.boundId) && holder.wasPending && !item.pending;
            holder.bubble.setAlpha(item.pending || settled ? 0.82f : 1f);
            if (settled) holder.bubble.animate().alpha(1f).setDuration(ZeroChillMotion.duration(ZeroChillMessageActivity.this, ZeroChillMotion.QUICK_MS)).start();
            holder.boundId = item.id;
            holder.wasPending = item.pending;
            holder.time.setTextColor(incoming ? AppearanceTheme.mutedText(ZeroChillMessageActivity.this) : AppearanceTheme.secondaryText(ZeroChillMessageActivity.this));
            holder.itemView.setContentDescription((incoming ? "From " : "You: ") + item.body);
            holder.bubble.setOnLongClickListener(v -> {
                showReactions(holder.bubble,item);
                return true;
            });
        }

        final class Holder extends RecyclerView.ViewHolder {
            final LinearLayout row;
            final TextView separator;
            final ImageView senderAvatar;
            final ImageView media;
            final TextView reactions;
            final LinearLayout bubble;
            final TextView body;
            final TextView time;
            final TextView status;
            String boundId = "";
            boolean wasPending;

            Holder(View itemView, LinearLayout row, TextView separator, ImageView senderAvatar, LinearLayout bubble, TextView body, TextView time, TextView status, ImageView media, TextView reactions) {
                super(itemView);this.row=row;this.separator=separator;
                this.senderAvatar = senderAvatar;
                this.bubble = bubble;
                this.body = body;
                this.time = time;
                this.status = status;
                this.media=media; this.reactions=reactions;
            }
        }
    }

    private GradientDrawable messageBubble(boolean mine, boolean joinsPrevious, boolean joinsNext) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(mine ? AppearanceTheme.floating(ZeroChillMessageActivity.this) : AppearanceTheme.surface(ZeroChillMessageActivity.this));
        float round = dp(16);
        float joined = dp(7);
        float top = joinsPrevious ? joined : round;
        float bottom = joinsNext ? joined : round;
        background.setCornerRadii(mine
                ? new float[]{round, round, top, top, bottom, bottom, round, round}
                : new float[]{top, top, round, round, round, round, bottom, bottom});
        return background;
    }

    private GradientDrawable inputBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(AppearanceTheme.surface(ZeroChillMessageActivity.this));
        background.setCornerRadius(dp(17));
        background.setStroke(dp(1), AppearanceTheme.outline(ZeroChillMessageActivity.this));
        return background;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(color);
        return background;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private String formatTime(String raw) {
        try {
            return DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.parse(raw));
        } catch (Exception ignored) {
            return "";
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
