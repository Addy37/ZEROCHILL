package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** ZeroChill-owned in-place comments, replies, and comment likes. */
final class InlineCommentsDialog extends BottomSheetDialog {
    interface ResizeListener {
        void onSheetTopChanged(int topOnScreen);
        void onSheetClosed();
    }

    private final Activity activity;
    private final String pageUrl;
    private final String pageTitle;
    private final String focusCommentId;
    private final ResizeListener resizeListener;

    private LinearLayout commentsContainer;
    private EditText composerInput;
    private TextView replyContext;
    private TextView headerTitle;
    private ProgressBar loading;
    private ScrollView scrollView;
    private BottomSheetBehavior<FrameLayout> behavior;
    private FrameLayout bottomSheet;
    private ZeroChillSocialRepository.Comment replyTarget;
    private ZeroChillSocialRepository.Comment editTarget;
    private boolean autoReplyConsumed;
    private final boolean autoReplyToFocus;
    private boolean closeDispatched;
    private boolean accountLaunched;
    private boolean posting;
    private boolean focusConsumed;
    private int loadGeneration;
    private final Map<String, View> commentViews = new HashMap<>();
    private final Map<String, ZeroChillSocialRepository.Comment> commentStates = new HashMap<>();
    private final Map<String, TextView> likeViews = new HashMap<>();
    private final java.util.Set<String> likesInFlight = new java.util.HashSet<>();
    private String renderedAccount = "";
    private static final java.util.WeakHashMap<Activity, java.lang.ref.WeakReference<InlineCommentsDialog>> OPEN_THREADS = new java.util.WeakHashMap<>();

    static boolean isOpenFor(Activity host, String url) {
        java.lang.ref.WeakReference<InlineCommentsDialog> reference = OPEN_THREADS.get(host);
        InlineCommentsDialog dialog = reference == null ? null : reference.get();
        return dialog != null && dialog.isShowing() && ZeroChillSocialRepository.contentKey(url).equals(ZeroChillSocialRepository.contentKey(dialog.pageUrl));
    }

    InlineCommentsDialog(
            Activity activity,
            String pageUrl,
            String pageTitle,
            String ignoredSourceCount,
            ResizeListener resizeListener
    ) {
        this(activity, pageUrl, pageTitle, ignoredSourceCount, "", false, resizeListener);
    }

    InlineCommentsDialog(
            Activity activity,
            String pageUrl,
            String pageTitle,
            String ignoredSourceCount,
            String focusCommentId,
            ResizeListener resizeListener
    ) {
        this(activity, pageUrl, pageTitle, ignoredSourceCount, focusCommentId, false, resizeListener);
    }

    InlineCommentsDialog(
            Activity activity,
            String pageUrl,
            String pageTitle,
            String ignoredSourceCount,
            String focusCommentId,
            boolean autoReplyToFocus,
            ResizeListener resizeListener
    ) {
        super(activity);
        this.activity = activity;
        this.pageUrl = clean(pageUrl);
        this.pageTitle = clean(pageTitle).isEmpty() ? "Comments" : clean(pageTitle);
        this.focusCommentId = clean(focusCommentId);
        this.autoReplyToFocus = autoReplyToFocus;
        this.resizeListener = resizeListener;
        setCancelable(true);
        setCanceledOnTouchOutside(true);
        setDismissWithAnimation(true);
        setContentView(buildContent());
        setOnDismissListener(dialog -> dispatchClosed());
    }

    @Override
    protected void onStart() {
        super.onStart();
        java.lang.ref.WeakReference<InlineCommentsDialog> previous = OPEN_THREADS.get(activity);
        InlineCommentsDialog old = previous == null ? null : previous.get();
        if (old != null && old != this && old.isShowing()) old.dismiss();
        OPEN_THREADS.put(activity, new java.lang.ref.WeakReference<>(this));
        configureWindow();
        configureSheet();
        loadComments();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || !accountLaunched || !isShowing()) return;
        accountLaunched = false;
        updateComposerHint();
        loadComments();
    }

    private View buildContent() {
        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackground(roundedTop(Color.rgb(13, 17, 21), 24));

        View handle = new View(activity);
        handle.setBackground(roundRect(Color.rgb(88, 88, 96), 3));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(38), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.setMargins(0, dp(8), 0, dp(6));
        shell.addView(handle, handleParams);

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), 0, dp(8), 0);
        shell.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));

        headerTitle = text("Comments", 17, Color.WHITE, true);
        headerTitle.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(headerTitle, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView close = text("×", 28, Color.WHITE, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close comments");
        ZeroChillMotion.installPressFeedback(close);
        close.setOnClickListener(v -> dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(48), -1));

        View divider = new View(activity);
        divider.setBackgroundColor(Color.rgb(39, 39, 44));
        shell.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        FrameLayout body = new FrameLayout(activity);
        shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        body.addView(scrollView, new FrameLayout.LayoutParams(-1, -1));

        commentsContainer = new LinearLayout(activity);
        commentsContainer.setOrientation(LinearLayout.VERTICAL);
        commentsContainer.setPadding(dp(14), dp(10), dp(14), dp(20));
        scrollView.addView(commentsContainer, new ScrollView.LayoutParams(-1, -2));

        loading = new ZeroChillProgressBar(activity);
        ZeroChillUi.styleProgress(loading);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(dp(42), dp(42));
        loadingParams.gravity = Gravity.CENTER;
        body.addView(loading, loadingParams);

        shell.addView(buildComposer(), new LinearLayout.LayoutParams(-1, -2));
        return shell;
    }

    private View buildComposer() {
        LinearLayout composer = new LinearLayout(activity);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setPadding(dp(12), dp(9), dp(12), dp(10));
        composer.setBackgroundColor(Color.rgb(16, 21, 27));

        replyContext = text("", 12, UiPalette.PRIMARY, false);
        replyContext.setPadding(dp(6), 0, dp(6), 0);
        replyContext.setVisibility(View.GONE);
        replyContext.setOnClickListener(v -> { if (!posting) cancelReply(); });
        replyContext.setMinimumHeight(dp(36));
        replyContext.setGravity(Gravity.CENTER_VERTICAL);
        replyContext.setContentDescription("Comment mode. Tap to cancel");
        ZeroChillMotion.installPressFeedback(replyContext);
        composer.addView(replyContext, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        composer.addView(row, new LinearLayout.LayoutParams(-1, -2));

        composerInput = new ZeroChillEditText(activity);
        composerInput.setTextColor(Color.WHITE);
        composerInput.setHintTextColor(Color.rgb(150, 150, 160));
        composerInput.setTextSize(14);
        composerInput.setMinHeight(dp(48));
        composerInput.setMaxLines(3);
        composerInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        composerInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        composerInput.setPadding(dp(16), dp(12), dp(14), dp(12));
        composerInput.setBackground(roundRect(Color.rgb(27, 34, 42), 22));
        composerInput.setOnFocusChangeListener((v, focused) -> {
            if (focused && !ZeroChillAccountRepository.hasStoredSession(activity)) {
                composerInput.clearFocus();
                openAccount();
            }
        });
        composerInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEND) return false;
            submit();
            return true;
        });
        row.addView(composerInput, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView send = text("↑", 23, Color.WHITE, true);
        send.setGravity(Gravity.CENTER);
        send.setBackground(circle(UiPalette.PRIMARY));
        send.setContentDescription("Post comment");
        ZeroChillMotion.installPressFeedback(send);
        send.setOnClickListener(v -> submit());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        sendParams.setMargins(dp(8), 0, 0, 0);
        row.addView(send, sendParams);

        updateComposerHint();
        return composer;
    }

    private void updateComposerHint() {
        if (composerInput == null) return;
        composerInput.setHint(
                ZeroChillAccountRepository.hasStoredSession(activity)
                        ? "Add a comment…"
                        : "Sign in to comment"
        );
    }

    private void loadComments() {
        final int generation = ++loadGeneration;
        final String account = ZeroChillSessionStore.currentUserId(activity);
        if (commentViews.isEmpty()) loading.setVisibility(View.VISIBLE);
        ZeroChillSocialRepository.loadComments(activity, pageUrl, (comments, error) ->
                activity.runOnUiThread(() -> {
                    if (!isShowing() || activity.isDestroyed() || generation != loadGeneration) return;
                    if (!account.equals(ZeroChillSessionStore.currentUserId(activity))) {
                        loadComments();
                        return;
                    }
                    loading.setVisibility(View.GONE);
                    if (error != null) {
                        if (commentViews.isEmpty()) showMessage("Comments couldn't load. Tap to retry.");
                        else ZeroChillToast.makeText(activity, "Comments couldn't refresh.", ZeroChillToast.LENGTH_SHORT).show();
                    } else {
                        render(comments == null ? new ArrayList<>() : comments);
                    }
                })
        );
    }

    private void render(ArrayList<ZeroChillSocialRepository.Comment> comments) {
        int visibleCount = 0;
        for (ZeroChillSocialRepository.Comment comment : comments) if (!comment.deleted()) visibleCount++;
        headerTitle.setText(visibleCount + (visibleCount == 1 ? " comment" : " comments"));
        if (comments.isEmpty()) {
            commentViews.clear();
            commentStates.clear();
            likeViews.clear();
            showMessage("No comments yet. Start the conversation.");
            return;
        }
        if (commentViews.isEmpty()) commentsContainer.removeAllViews();
        String account = ZeroChillSessionStore.currentUserId(activity);
        boolean accountChanged = !account.equals(renderedAccount);
        renderedAccount = account;
        int previousScroll = scrollView.getScrollY();
        Map<String, ZeroChillSocialRepository.Comment> byId = new HashMap<>();
        for (ZeroChillSocialRepository.Comment comment : comments) byId.put(comment.id, comment);
        java.util.Iterator<Map.Entry<String, View>> iterator = commentViews.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, View> old = iterator.next();
            if (!byId.containsKey(old.getKey())) {
                commentsContainer.removeView(old.getValue());
                likeViews.remove(old.getKey());
                commentStates.remove(old.getKey());
                iterator.remove();
            }
        }
        int index = 0;
        for (ZeroChillSocialRepository.Comment comment : ordered(comments)) {
            View row = commentViews.get(comment.id);
            ZeroChillSocialRepository.Comment old = commentStates.get(comment.id);
            boolean changed = accountChanged || !sameRow(old, comment);
            commentStates.put(comment.id, comment);
            if (row == null || changed) {
                if (row != null) commentsContainer.removeView(row);
                likeViews.remove(comment.id);
                row = commentRow(comment);
                commentViews.put(comment.id, row);
                commentsContainer.addView(row, Math.min(index, commentsContainer.getChildCount()), commentParams(depth(comment, byId)));
                animateRow(row);
            } else {
                if (commentsContainer.indexOfChild(row) != index) {
                    commentsContainer.removeView(row);
                    commentsContainer.addView(row, Math.min(index, commentsContainer.getChildCount()), commentParams(depth(comment, byId)));
                } else row.setLayoutParams(commentParams(depth(comment, byId)));
                bindLike(comment);
            }
            index++;
        }
        scrollView.post(() -> {
            if (!isShowing()) return;
            View target = commentViews.get(focusCommentId);
            if (target != null && !focusConsumed) {
                focusConsumed = true;
                scrollView.smoothScrollTo(0, Math.max(0, target.getTop() - dp(18)));
                highlight(target);
                ZeroChillSocialRepository.Comment focused = commentStates.get(focusCommentId);
                if (autoReplyToFocus && !autoReplyConsumed && focused != null && !focused.deleted()) {
                    autoReplyConsumed = true;
                    startReply(focused);
                }
            } else scrollView.scrollTo(0, previousScroll);
        });
    }

    // Render actual parent-child adjacency, not only chronological indentation.
    // Missing parents and cycles are displayed once without recursive traversal.
    static ArrayList<ZeroChillSocialRepository.Comment> ordered(ArrayList<ZeroChillSocialRepository.Comment> comments) {
        Map<String, ArrayList<ZeroChillSocialRepository.Comment>> children = new HashMap<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (ZeroChillSocialRepository.Comment c : comments) ids.add(c.id);
        for (ZeroChillSocialRepository.Comment c : comments) children.computeIfAbsent(c.parentId, k -> new ArrayList<>()).add(c);
        ArrayList<ZeroChillSocialRepository.Comment> roots = new ArrayList<>();
        for (ZeroChillSocialRepository.Comment c : comments) if (c.parentId.isEmpty() || !ids.contains(c.parentId)) roots.add(c);
        roots.addAll(comments);
        ArrayList<ZeroChillSocialRepository.Comment> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<ZeroChillSocialRepository.Comment> pending = new java.util.ArrayDeque<>();
        for (ZeroChillSocialRepository.Comment root : roots) {
            pending.push(root);
            while (!pending.isEmpty()) {
                ZeroChillSocialRepository.Comment c = pending.pop();
                if (!seen.add(c.id)) continue;
                result.add(c);
                ArrayList<ZeroChillSocialRepository.Comment> replies = children.get(c.id);
                if (replies != null) for (int i = replies.size() - 1; i >= 0; i--) pending.push(replies.get(i));
            }
        }
        return result;
    }

    private static boolean sameRow(ZeroChillSocialRepository.Comment a, ZeroChillSocialRepository.Comment b) {
        return a != null && a.body.equals(b.body) && a.parentId.equals(b.parentId)
                && a.userId.equals(b.userId) && a.username.equals(b.username) && a.displayName.equals(b.displayName)
                && a.avatarPath.equals(b.avatarPath) && a.createdAt.equals(b.createdAt)
                && a.editedAt.equals(b.editedAt) && a.deletedAt.equals(b.deletedAt);
    }

    private void animateRow(View row) {
        if (!ZeroChillMotion.animationsEnabled(activity)) return;
        row.setAlpha(0f);
        row.setTranslationY(dp(5));
        row.animate().alpha(1f).translationY(0).setDuration(ZeroChillMotion.QUICK_MS).start();
    }

    private void highlight(View row) {
        GradientDrawable glow = roundRect(Color.TRANSPARENT, 12);
        row.setBackground(glow);
        if (!ZeroChillMotion.animationsEnabled(activity)) {
            glow.setColor(Color.argb(38, 8, 146, 208));
            row.postDelayed(() -> glow.setColor(Color.TRANSPARENT), 1200L);
            return;
        }
        android.animation.ValueAnimator pulse = android.animation.ValueAnimator.ofInt(0, 50, 24, 0);
        pulse.setDuration(1400L);
        pulse.addUpdateListener(value -> glow.setColor(Color.argb((Integer) value.getAnimatedValue(), 8, 146, 208)));
        pulse.start();
    }

    private View commentRow(ZeroChillSocialRepository.Comment comment) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(2), dp(10), dp(2), dp(8));

        ImageView avatar = new ImageView(activity);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        avatar.setClipToOutline(true);
        avatar.setBackground(circle(Color.rgb(40, 40, 46)));
        AccountAvatarImages.track(avatar, comment.userId);
        String avatarUrl = ZeroChillAccountRepository.avatarUrl(comment.avatarPath);
        if (!avatarUrl.isEmpty()) {
            AccountAvatarImages.bind(avatar, comment.userId, comment.avatarPath);
        } else {
            avatar.setImageResource(R.drawable.ic_more_account);
            avatar.setPadding(dp(9), dp(9), dp(9), dp(9));
            avatar.setColorFilter(UiPalette.PRIMARY);
        }
        avatar.setClickable(true);
        avatar.setFocusable(true);
        avatar.setContentDescription("Open " + SocialUi.name(comment.displayName, comment.username) + " profile");
        avatar.setOnClickListener(v -> openProfile(comment));
        ZeroChillMotion.installPressFeedback(avatar);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(0, -2, 1f);
        bodyParams.setMargins(dp(10), 0, 0, 0);
        row.addView(body, bodyParams);

        LinearLayout nameRow = new LinearLayout(activity);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(nameRow, new LinearLayout.LayoutParams(-1, -2));

        String identity = SocialUi.name(comment.displayName, comment.username);
        TextView name = text(identity, 13, comment.deleted() ? Color.rgb(135, 145, 155) : Color.WHITE, true);
        name.setClickable(!comment.deleted());
        name.setFocusable(!comment.deleted());
        name.setContentDescription("Open " + SocialUi.name(comment.displayName, comment.username) + " profile");
        if (!comment.deleted()) name.setOnClickListener(v -> openProfile(comment));
        ZeroChillMotion.installPressFeedback(name);
        nameRow.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

        if (!comment.deleted() && isOwner(comment)) {
            TextView menu = text("⋮", 20, Color.rgb(170, 170, 180), true);
            menu.setGravity(Gravity.CENTER);
            menu.setContentDescription("Comment options");
            ZeroChillMotion.installPressFeedback(menu);
            menu.setOnClickListener(v -> showCommentMenu(v, commentStates.get(comment.id)));
            nameRow.addView(menu, new LinearLayout.LayoutParams(dp(44), dp(36)));
        }

        TextView copy = text(
                comment.deleted() ? "Comment deleted" : comment.body,
                14,
                comment.deleted() ? Color.rgb(135, 135, 145) : Color.rgb(235, 235, 239),
                false
        );
        if (comment.deleted()) {
            copy.setTypeface(null, android.graphics.Typeface.ITALIC);
        }
        copy.setLineSpacing(0f, 1.08f);
        copy.setPadding(0, dp(4), 0, dp(7));
        body.addView(copy);

        TextView metadata = text(SocialUi.relativeTime(comment.createdAt)
                + (comment.edited() ? "  ·  Edited" : ""), 10, Color.rgb(126, 140, 153), false);
        metadata.setPadding(0, 0, 0, dp(2));
        body.addView(metadata);
        if (comment.deleted()) return row;

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(actions);

        TextView reply = text("Reply", 11, Color.rgb(170, 170, 180), true);
        reply.setPadding(0, dp(7), dp(18), dp(7));
        reply.setMinimumHeight(dp(36));
        reply.setGravity(Gravity.CENTER_VERTICAL);
        ZeroChillMotion.installPressFeedback(reply);
        reply.setOnClickListener(v -> startReply(commentStates.get(comment.id)));
        actions.addView(reply);

        TextView like = text(
                (comment.likedByMe ? "♥ " : "♡ ") + comment.likeCount,
                12,
                comment.likedByMe ? UiPalette.PRIMARY : Color.rgb(170, 170, 180),
                true
        );
        like.setPadding(dp(8), dp(7), dp(10), dp(7));
        like.setMinimumWidth(dp(44));
        like.setMinimumHeight(dp(36));
        like.setGravity(Gravity.CENTER);
        ZeroChillMotion.installPressFeedback(like);
        like.setOnClickListener(v -> toggleLike(commentStates.get(comment.id)));
        likeViews.put(comment.id, like);
        actions.addView(like);

        return row;
    }

    private void bindLike(ZeroChillSocialRepository.Comment comment) {
        TextView like = likeViews.get(comment.id);
        if (like == null) return;
        like.setText((comment.likedByMe ? "♥ " : "♡ ") + comment.likeCount);
        like.setTextColor(comment.likedByMe ? UiPalette.PRIMARY : Color.rgb(156, 169, 181));
        like.setContentDescription((comment.likedByMe ? "Unlike" : "Like") + " comment. " + comment.likeCount + " likes");
    }

    private void toggleLike(ZeroChillSocialRepository.Comment comment) {
        if (comment == null || comment.deleted() || likesInFlight.contains(comment.id)) return;
        if (!ZeroChillAccountRepository.hasStoredSession(activity)) { openAccount(); return; }
        final String account = ZeroChillSessionStore.currentUserId(activity);
        likesInFlight.add(comment.id);
        ZeroChillSocialRepository.toggleCommentLike(activity, comment.id, comment.likedByMe,
                (liked, error) -> activity.runOnUiThread(() -> {
                    likesInFlight.remove(comment.id);
                    if (!isShowing() || !account.equals(ZeroChillSessionStore.currentUserId(activity))) return;
                    if (error != null) ZeroChillToast.makeText(activity, error.getMessage(), ZeroChillToast.LENGTH_LONG).show();
                    else {
                        ZeroChillSocialRepository.Comment current = commentStates.get(comment.id);
                        if (current == null || current.deleted()) return;
                        ZeroChillSocialRepository.Comment updated = current.withLikeState(Boolean.TRUE.equals(liked));
                        commentStates.put(comment.id, updated);
                        bindLike(updated);
                        TextView heart = likeViews.get(comment.id);
                        if (heart != null && ZeroChillMotion.animationsEnabled(activity)) {
                            heart.setScaleX(1.12f); heart.setScaleY(1.12f);
                            heart.animate().scaleX(1f).scaleY(1f).setDuration(ZeroChillMotion.QUICK_MS).start();
                        }
                    }
                }));
    }

    private void showComposerMode(String copy) {
        replyContext.animate().cancel();
        boolean visible = !copy.isEmpty();
        replyContext.setText(copy);
        replyContext.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible && ZeroChillMotion.animationsEnabled(activity)) {
            replyContext.setAlpha(0f); replyContext.setTranslationY(dp(5));
            replyContext.animate().alpha(1f).translationY(0).setDuration(ZeroChillMotion.QUICK_MS).start();
        } else { replyContext.setAlpha(1f); replyContext.setTranslationY(0); }
    }

    private void startReply(ZeroChillSocialRepository.Comment comment) {
        if (posting || comment == null || comment.deleted()) return;
        if (!ZeroChillAccountRepository.hasStoredSession(activity)) {
            openAccount();
            return;
        }
        editTarget = null;
        replyTarget = comment;
        composerInput.setText("");
        showComposerMode("Replying to " + SocialUi.name(comment.displayName, comment.username) + "  ·  Cancel");
        focusComposer();
    }

    private void startEdit(ZeroChillSocialRepository.Comment comment) {
        if (posting || comment == null || comment.deleted() || !isOwner(comment)) return;
        replyTarget = null;
        editTarget = comment;
        showComposerMode("Editing your comment  ·  Cancel");
        composerInput.setText(comment.body);
        composerInput.setSelection(composerInput.length());
        focusComposer();
    }

    private void cancelReply() {
        replyTarget = null;
        editTarget = null;
        showComposerMode("");
        composerInput.setText("");
        updateComposerHint();
    }

    private void submit() {
        if (posting) return;
        if (!ZeroChillAccountRepository.hasStoredSession(activity)) {
            openAccount();
            return;
        }
        String message = clean(composerInput.getText().toString());
        if (message.isEmpty()) return;
        final String submittedAccount = ZeroChillSessionStore.currentUserId(activity);
        posting = true;
        composerInput.setEnabled(false);
        ZeroChillSocialRepository.Callback<Boolean> completed = (ok, error) ->
                activity.runOnUiThread(() -> {
                    posting = false;
                    if (!isShowing() || activity.isDestroyed()) return;
                    composerInput.setEnabled(true);
                    if (!submittedAccount.equals(ZeroChillSessionStore.currentUserId(activity))) { loadComments(); return; }
                    if (error != null) {
                        ZeroChillToast.makeText(activity, error.getMessage(), ZeroChillToast.LENGTH_LONG).show();
                    } else {
                        cancelReply();
                        loadComments();
                    }
                });
        if (editTarget != null) {
            ZeroChillSocialRepository.editComment(
                    activity,
                    editTarget.id,
                    message,
                    completed
            );
        } else {
            ZeroChillSocialRepository.postComment(
                    activity,
                    pageUrl,
                    pageTitle,
                    replyTarget == null ? "" : replyTarget.id,
                    message,
                    completed
            );
        }
    }

    private boolean isOwner(ZeroChillSocialRepository.Comment comment) {
        return comment != null
                && !clean(comment.userId).isEmpty()
                && comment.userId.equals(ZeroChillSessionStore.currentUserId(activity));
    }

    private void showCommentMenu(View anchor, ZeroChillSocialRepository.Comment comment) {
        if (posting || comment == null || comment.deleted() || !isOwner(comment)) return;
        ZeroChillMenu menu = new ZeroChillMenu(activity, anchor);
        menu.getMenu().add("Edit");
        menu.getMenu().add("Delete");
        menu.setOnMenuItemClickListener(item -> {
            if ("Edit".contentEquals(item.getTitle())) {
                startEdit(comment);
                return true;
            }
            if ("Delete".contentEquals(item.getTitle())) {
                confirmDelete(comment);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void confirmDelete(ZeroChillSocialRepository.Comment comment) {
        if (!isOwner(comment) || comment.deleted()) return;
        new ZeroChillDialog.Builder(activity)
                .setTitle("Delete comment?")
                .setMessage("Replies will stay in the conversation.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    ZeroChillSocialRepository.deleteComment(
                            activity,
                            comment.id,
                            (ok, error) -> activity.runOnUiThread(() -> {
                                if (error != null) {
                                    ZeroChillToast.makeText(activity, error.getMessage(), ZeroChillToast.LENGTH_LONG).show();
                                } else {
                                    if (editTarget != null && comment.id.equals(editTarget.id)) {
                                        cancelReply();
                                    }
                                    loadComments();
                                }
                            })
                    );
                })
                .show();
    }

    private void focusComposer() {
        composerInput.requestFocus();
        composerInput.post(() -> {
            InputMethodManager keyboard = (InputMethodManager)
                    activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.showSoftInput(composerInput, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void openAccount() {
        accountLaunched = true;
        activity.startActivity(new Intent(activity, ZeroChillAccountActivity.class));
    }

    private void openProfile(ZeroChillSocialRepository.Comment comment) {
        if (comment == null || clean(comment.userId).isEmpty()) return;
        Intent intent = new Intent(activity, ZeroChillPublicProfileActivity.class);
        intent.putExtra(ZeroChillPublicProfileActivity.EXTRA_USER_ID, comment.userId);
        activity.startActivity(intent);
    }

    private int depth(
            ZeroChillSocialRepository.Comment comment,
            Map<String, ZeroChillSocialRepository.Comment> byId
    ) {
        int depth = 0;
        String parent = comment.parentId;
        while (!clean(parent).isEmpty() && depth < 3) {
            depth++;
            ZeroChillSocialRepository.Comment next = byId.get(parent);
            if (next == null || next.id.equals(next.parentId)) break;
            parent = next.parentId;
        }
        return depth;
    }

    private LinearLayout.LayoutParams commentParams(int depth) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(Math.min(2, depth) * 12), 0, 0, dp(2));
        return params;
    }

    private void showMessage(String message) {
        commentsContainer.removeAllViews();
        TextView empty = text(message, 14, Color.rgb(185, 185, 194), false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(20), dp(42), dp(20), dp(42));
        if (message.contains("retry")) empty.setOnClickListener(v -> loadComments());
        commentsContainer.addView(empty, new LinearLayout.LayoutParams(-1, -2));
    }

    private void configureWindow() {
        Window window = getWindow();
        if (window == null) return;
        window.setDimAmount(0.18f);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setNavigationBarColor(Color.BLACK);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void configureSheet() {
        bottomSheet = findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) return;
        bottomSheet.setBackgroundColor(Color.TRANSPARENT);
        ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
        params.height = desiredHeight();
        bottomSheet.setLayoutParams(params);
        behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.setFitToContents(true);
        behavior.setHideable(true);
        behavior.setSkipCollapsed(true);
        behavior.setDraggable(true);
        behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(View sheet, int newState) {
                notifySheetTop();
                if (newState == BottomSheetBehavior.STATE_HIDDEN) dismiss();
            }

            @Override
            public void onSlide(View sheet, float slideOffset) {
                notifySheetTop();
            }
        });
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        bottomSheet.post(this::notifySheetTop);
    }

    private int desiredHeight() {
        View content = activity.findViewById(android.R.id.content);
        int available = content == null ? 0 : content.getHeight();
        if (available <= 0) available = activity.getResources().getDisplayMetrics().heightPixels;
        boolean landscape = activity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        return Math.round(available * (landscape ? 0.72f : 0.68f));
    }

    private void notifySheetTop() {
        if (resizeListener == null || bottomSheet == null || !isShowing()) return;
        int[] location = new int[2];
        bottomSheet.getLocationOnScreen(location);
        resizeListener.onSheetTopChanged(location[1]);
    }

    private void dispatchClosed() {
        java.lang.ref.WeakReference<InlineCommentsDialog> reference = OPEN_THREADS.get(activity);
        if (reference != null && reference.get() == this) OPEN_THREADS.remove(activity);
        loadGeneration++;
        if (closeDispatched) return;
        closeDispatched = true;
        if (resizeListener != null) resizeListener.onSheetClosed();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private GradientDrawable roundedTop(int color, int radiusDp) {
        float radius = dp(radiusDp);
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        return background;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radiusDp));
        return background;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(color);
        return background;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
