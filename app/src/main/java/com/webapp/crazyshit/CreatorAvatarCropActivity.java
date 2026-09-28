package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.android.material.card.MaterialCardView;

/** Circular pinch/drag editor for a selected Favorite Creator avatar image. */
public final class CreatorAvatarCropActivity extends Activity {
    static final String EXTRA_IMAGE_URL = "creator_avatar_crop_image_url";
    static final String EXTRA_REFERER = "creator_avatar_crop_referer";
    static final String EXTRA_FOCUS_X = "creator_avatar_crop_focus_x";
    static final String EXTRA_FOCUS_Y = "creator_avatar_crop_focus_y";
    static final String EXTRA_ZOOM = "creator_avatar_crop_zoom";

    private CreatorAvatarImageView image;
    private ScaleGestureDetector scaleDetector;
    private float lastX;
    private float lastY;

    static Intent create(Activity activity, String imageUrl, String referer) {
        return new Intent(activity, CreatorAvatarCropActivity.class)
                .putExtra(EXTRA_IMAGE_URL, imageUrl)
                .putExtra(EXTRA_REFERER, referer);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String imageUrl = clean(getIntent().getStringExtra(EXTRA_IMAGE_URL));
        String referer = clean(getIntent().getStringExtra(EXTRA_REFERER));
        if (!remote(imageUrl)) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        LinearLayout root = BrowseUi.screen(this);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(4));
        header.addView(
                BrowseUi.action(this, "‹", "Back", v -> finish()),
                new LinearLayout.LayoutParams(dp(48), dp(48))
        );
        TextView title = BrowseUi.text(this, "Adjust avatar", 20, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dp(10), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = BrowseUi.text(
                this,
                "Pinch to zoom  •  drag to reposition",
                13,
                BrowseUi.MUTED
        );
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.setMargins(dp(18), dp(8), dp(18), dp(18));
        root.addView(hint, hintParams);

        int previewSize = Math.min(dp(340), getResources().getDisplayMetrics().widthPixels - dp(36));
        FrameLayout previewFrame = new FrameLayout(this);
        LinearLayout.LayoutParams previewParams =
                new LinearLayout.LayoutParams(previewSize, previewSize);
        previewParams.setMargins(0, dp(8), 0, dp(18));
        root.addView(previewFrame, previewParams);

        MaterialCardView cropCard = new MaterialCardView(this);
        cropCard.setRadius(previewSize / 2f);
        cropCard.setCardElevation(0f);
        cropCard.setStrokeColor(UiPalette.PRIMARY);
        cropCard.setStrokeWidth(dp(2));
        cropCard.setCardBackgroundColor(Color.rgb(19, 23, 27));
        cropCard.setClipToOutline(true);
        previewFrame.addView(cropCard, new FrameLayout.LayoutParams(-1, -1));

        image = new CreatorAvatarImageView(this);
        image.setBackgroundColor(Color.rgb(19, 23, 27));
        image.resetAvatarCrop();
        cropCard.addView(image, new MaterialCardView.LayoutParams(-1, -1));

        scaleDetector = new ScaleGestureDetector(
                this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        image.setAvatarZoom(image.avatarZoom() * detector.getScaleFactor());
                        return true;
                    }
                }
        );

        image.setOnTouchListener((view, event) -> {
            scaleDetector.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float x = event.getX();
                    float y = event.getY();
                    if (!scaleDetector.isInProgress() && event.getPointerCount() == 1) {
                        image.dragAvatarBy(x - lastX, y - lastY);
                    }
                    lastX = x;
                    lastY = y;
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.performClick();
                    return true;
                default:
                    return true;
            }
        });

        GlideUrl glideUrl = new GlideUrl(
                imageUrl,
                new LazyHeaders.Builder()
                        .addHeader("Referer", referer.isEmpty() ? imageUrl : referer)
                        .addHeader(
                                "User-Agent",
                                "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/139.0 Mobile Safari/537.36"
                        )
                        .build()
        );
        Glide.with(image)
                .load(glideUrl)
                .dontAnimate()
                .placeholder(new ColorDrawable(Color.rgb(19, 23, 27)))
                .error(R.drawable.ic_more_account)
                .into(image);

        TextView zoom = BrowseUi.text(
                this,
                "Zoom and center the part you want inside the circle.",
                12,
                BrowseUi.MUTED
        );
        zoom.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams zoomParams = new LinearLayout.LayoutParams(-1, -2);
        zoomParams.setMargins(dp(24), 0, dp(24), dp(18));
        root.addView(zoom, zoomParams);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        buttons.setPadding(dp(18), dp(4), dp(18), dp(24));

        TextView reset = BrowseUi.action(this, "Reset", "Reset avatar crop", v -> {
            image.resetAvatarCrop();
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
        });
        reset.setTextSize(14);
        reset.setTextColor(Color.WHITE);
        reset.setBackground(BrowseUi.rounded(this, Color.rgb(25, 31, 37), 16));
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        resetParams.setMargins(0, 0, dp(8), 0);
        buttons.addView(reset, resetParams);

        TextView save = BrowseUi.action(this, "Save", "Save avatar crop", v -> {
            Intent result = new Intent()
                    .putExtra(EXTRA_IMAGE_URL, imageUrl)
                    .putExtra(EXTRA_REFERER, referer)
                    .putExtra(EXTRA_FOCUS_X, image.avatarFocusX())
                    .putExtra(EXTRA_FOCUS_Y, image.avatarFocusY())
                    .putExtra(EXTRA_ZOOM, image.avatarZoom());
            setResult(RESULT_OK, result);
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
            finish();
        });
        save.setTextSize(14);
        save.setTextColor(Color.BLACK);
        save.setTypeface(null, android.graphics.Typeface.BOLD);
        save.setBackground(BrowseUi.rounded(this, UiPalette.PRIMARY, 16));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        saveParams.setMargins(dp(8), 0, 0, 0);
        buttons.addView(save, saveParams);

        root.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private int dp(int value) {
        return BrowseUi.dp(this, value);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean remote(String value) {
        String lower = clean(value).toLowerCase(java.util.Locale.US);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }
}
