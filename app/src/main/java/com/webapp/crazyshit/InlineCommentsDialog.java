package com.webapp.crazyshit;

import android.app.Activity;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
    private boolean closeDispatched;
    private boolean accountLaunched;
    private boolean posting;

    InlineCommentsDialog(
            Activity activity,
            String pageUrl,
            String pageTitle,
            String ignoredSourceCount,
            ResizeListener resizeListener
    ) {
        this(activity, pageUrl, pageTitle, ignoredSourceCount, "", resizeListener);
    }

    InlineCommentsDialog(
            Activity activity,
            String pageUrl,
            String pageTitle,
            String ignoredSourceCount,
            String focusCommentId,
            ResizeListener resizeListener
    ) {
        super(activity);
        this.activity = activity;
        this.pageUrl = clean(pageUrl);
        this.pageTitle = clean(pageTitle).isEmpty() ? "Comments" : clean(pageTitle);
        this.focusCommentId = clean(focusCommentId);
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
        shell.setBackground(roundedTop(Color.rgb(16, 16, 19), 24));

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

        headerTitle = text("Comments", 15, Color.WHITE, true);
        header.addView(headerTitle, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView close = text("×", 28, Color.WHITE, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close comments");
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

        loading = new ProgressBar(activity);
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
        composer.setPadding(dp(12), dp(5), dp(12), dp(9));
        composer.setBackgroundColor(Color.rgb(20, 20, 23));

        replyContext = text("", 11, UiPalette.PRIMARY, false);
        replyContext.setPadding(dp(6), 0, dp(6), 0);
        replyContext.setVisibility(View.GONE);
        replyContext.setOnClickListener(v -> cancelReply());
        composer.addView(replyContext, new LinearLayout.LayoutParams(-1, dp(28)));

        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        composer.addView(row, new LinearLayout.LayoutParams(-1, dp(50)));

        composerInput = new EditText(activity);
        composerInput.setTextColor(Color.WHITE);
        composerInput.setHintTextColor(Color.rgb(150, 150, 160));
        composerInput.setTextSize(13);
        composerInput.setMaxLines(3);
        composerInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        composerInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        composerInput.setPadding(dp(14), 0, dp(12), 0);
        composerInput.setBackground(roundRect(Color.rgb(34, 34, 39), 20));
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
        row.addView(composerInput, new LinearLayout.LayoutParams(0, dp(44), 1f));

        TextView send = text("↑", 22, Color.rgb(18, 18, 20), true);
        send.setGravity(Gravity.CENTER);
        send.setBackground(circle(UiPalette.PRIMARY));
        send.setContentDescription("Post comment");
        send.setOnClickListener(v -> submit());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(40), dp(40));
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
        loading.setVisibility(View.VISIBLE);
        commentsContainer.setVisibility(View.INVISIBLE);
        ZeroChillSocialRepository.loadComments(activity, pageUrl, (comments, error) ->
                activity.runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    commentsContainer.setVisibility(View.VISIBLE);
                    if (error != null) {
                        showMessage(error.getMessage() == null ? "Comments couldn't load." : error.getMessage());
                    } else {
                        render(comments == null ? new ArrayList<>() : comments);
                    }
                })
        );
    }

    private void render(ArrayList<ZeroChillSocialRepository.Comment> comments) {
        commentsContainer.removeAllViews();
        headerTitle.setText(comments.size() + (comments.size() == 1 ? " comment" : " comments"));

        if (comments.isEmpty()) {
            showMessage("No comments yet. Start the conversation.");
            return;
        }

        Map<String, ZeroChillSocialRepository.Comment> byId = new HashMap<>();
        for (ZeroChillSocialRepository.Comment comment : comments) byId.put(comment.id, comment);

        View focusRow = null;
        for (ZeroChillSocialRepository.Comment comment : comments) {
            int depth = depth(comment, byId);
            View row = commentRow(comment);
            commentsContainer.addView(row, commentParams(depth));
            if (!focusCommentId.isEmpty() && focusCommentId.equals(comment.id)) {
                focusRow = row;
            }
        }

        if (focusRow != null) {
            final View target = focusRow;
            scrollView.post(() -> {
                scrollView.smoothScrollTo(0, Math.max(0, target.getTop() - dp(18)));
                target.setBackground(roundRect(Color.argb(52, 8, 146, 208), 12));
                target.postDelayed(() -> target.setBackgroundColor(Color.TRANSPARENT), 1600L);
            });
        }
    }

    private View commentRow(ZeroChillSocialRepository.Comment comment) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(9), 0, dp(9));

        ImageView avatar = new ImageView(activity);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(circle(Color.rgb(40, 40, 46)));
        String avatarUrl = ZeroChillAccountRepository.avatarUrl(comment.avatarPath);
        if (!avatarUrl.isEmpty()) {
            Glide.with(avatar).load(avatarUrl).circleCrop().into(avatar);
        } else {
            avatar.setImageResource(R.drawable.ic_more_account);
            avatar.setPadding(dp(9), dp(9), dp(9), dp(9));
            avatar.setColorFilter(UiPalette.PRIMARY);
        }
        avatar.setClickable(true);
        avatar.setFocusable(true);
        avatar.setContentDescription("Open @" + comment.username + " profile");
        avatar.setOnClickListener(v -> openProfile(comment));
        row.addView(avatar, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(0, -2, 1f);
        bodyParams.setMargins(dp(10), 0, 0, 0);
        row.addView(body, bodyParams);

        String identity = comment.displayName.isEmpty()
                ? "@" + comment.username
                : comment.displayName + "  @" + comment.username;
        TextView name = text(identity, 12, UiPalette.PRIMARY, true);
        name.setClickable(true);
        name.setFocusable(true);
        name.setContentDescription("Open @" + comment.username + " profile");
        name.setOnClickListener(v -> openProfile(comment));
        body.addView(name);

        TextView copy = text(comment.body, 14, Color.rgb(235, 235, 239), false);
        copy.setLineSpacing(0f, 1.08f);
        copy.setPadding(0, dp(4), 0, dp(7));
        body.addView(copy);

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(actions);

        TextView reply = text("Reply", 11, Color.rgb(170, 170, 180), true);
        reply.setPadding(0, dp(4), dp(18), dp(4));
        reply.setOnClickListener(v -> startReply(comment));
        actions.addView(reply);

        TextView like = text(
                (comment.likedByMe ? "♥ " : "♡ ") + comment.likeCount,
                12,
                comment.likedByMe ? UiPalette.PRIMARY : Color.rgb(170, 170, 180),
                true
        );
        like.setPadding(dp(6), dp(4), dp(8), dp(4));
        like.setOnClickListener(v -> toggleLike(comment));
        actions.addView(like);
        return row;
    }

    private void toggleLike(ZeroChillSocialRepository.Comment comment) {
        if (!ZeroChillAccountRepository.hasStoredSession(activity)) {
            openAccount();
            return;
        }
        ZeroChillSocialRepository.toggleCommentLike(
                activity,
                comment.id,
                comment.likedByMe,
                (liked, error) -> activity.runOnUiThread(() -> {
                    if (error != null) {
                        Toast.makeText(activity, error.getMessage(), Toast.LENGTH_LONG).show();
                    } else {
                        loadComments();
                    }
                })
        );
    }

    private void startReply(ZeroChillSocialRepository.Comment comment) {
        if (!ZeroChillAccountRepository.hasStoredSession(activity)) {
            openAccount();
            return;
        }
        replyTarget = comment;
        replyContext.setText("Replying to @" + comment.username + "  •  tap to cancel");
        replyContext.setVisibility(View.VISIBLE);
        composerInput.requestFocus();
    }

    private void cancelReply() {
        replyTarget = null;
        replyContext.setText("");
        replyContext.setVisibility(View.GONE);
    }

    private void submit() {
        if (posting) return;
        if (!ZeroChillAccountRepository.hasStoredSession(activity)) {
            openAccount();
            return;
        }
        String message = clean(composerInput.getText().toString());
        if (message.isEmpty()) return;
        posting = true;
        composerInput.setEnabled(false);
        ZeroChillSocialRepository.postComment(
                activity,
                pageUrl,
                pageTitle,
                replyTarget == null ? "" : replyTarget.id,
                message,
                (ok, error) -> activity.runOnUiThread(() -> {
                    posting = false;
                    composerInput.setEnabled(true);
                    if (error != null) {
                        Toast.makeText(activity, error.getMessage(), Toast.LENGTH_LONG).show();
                    } else {
                        composerInput.setText("");
                        cancelReply();
                        loadComments();
                    }
                })
        );
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
        params.setMargins(dp(Math.min(3, depth) * 18), 0, 0, dp(2));
        return params;
    }

    private void showMessage(String message) {
        commentsContainer.removeAllViews();
        TextView empty = text(message, 14, Color.rgb(185, 185, 194), false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(20), dp(42), dp(20), dp(42));
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
