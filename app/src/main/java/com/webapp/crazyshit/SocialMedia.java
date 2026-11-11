package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;

/** Single-image preparation and private, memory-only rendering shared by comments and DMs. */
final class SocialMedia {
    static final int PHOTO_SOURCE_LIMIT = 20 * 1024 * 1024;
    static final int PHOTO_LIMIT = 5 * 1024 * 1024;
    static final int GIF_LIMIT = 10 * 1024 * 1024;
    static final int DIMENSION = 2560;
    static final java.util.concurrent.ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final android.os.Handler UI = new android.os.Handler(android.os.Looper.getMainLooper());
    static final class Prepared {
        final byte[] bytes;
        final String mime;
        Prepared(byte[] bytes, String mime) { this.bytes = bytes; this.mime = mime; }
        boolean gif() { return mime.equals("image/gif"); }
        String fallback() { return gif() ? "GIF" : "Photo"; }
        String bucket() { return gif() ? "social-gifs" : "social-photos"; }
    }
    static String signature(byte[] b) {
        if (b.length >= 6 && (new String(b, 0, 6, java.nio.charset.StandardCharsets.US_ASCII).matches("GIF8[79]a"))) return "image/gif";
        if (b.length >= 3 && (b[0]&255)==255 && (b[1]&255)==216 && (b[2]&255)==255) return "image/jpeg";
        if (b.length >= 8 && ByteBuffer.wrap(b).getLong()==0x89504e470d0a1a0aL) return "image/png";
        if (b.length >= 12 && new String(b,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                && new String(b,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        return "";
    }
    static Prepared prepare(Activity activity, Uri uri) throws Exception {
        byte[] source;
        try (InputStream in = activity.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalArgumentException("Unable to open this image.");
            byte[] chunk = new byte[16384]; int size; boolean gif=false, checked=false;
            while ((size = in.read(chunk)) != -1) {
                if (out.size() + size > PHOTO_SOURCE_LIMIT) throw new IllegalArgumentException("This photo is too large. Maximum source size is 20 MB.");
                out.write(chunk,0,size);
                if (!checked && out.size() >= 6) {
                    gif = signature(out.toByteArray()).equals("image/gif");
                    checked = true;
                }
                // Detect GIF early so oversized GIFs always receive the GIF-specific message.
                if (out.size() > GIF_LIMIT && gif)
                    throw new IllegalArgumentException("This GIF is too large. Maximum size is 10 MB.");
            }
            source = out.toByteArray();
        }
        return prepareBytes(activity,source,activity.getContentResolver().getType(uri));
    }
    static Prepared prepareBytes(android.content.Context activity, byte[] source, String declared) throws Exception {
        String mime = signature(source);
        if (mime.equals("image/gif") && source.length>GIF_LIMIT) throw new IllegalArgumentException("This GIF is too large. Maximum size is 10 MB.");
        if (source.length>PHOTO_SOURCE_LIMIT) throw new IllegalArgumentException("This photo is too large. Maximum source size is 20 MB.");
        if (mime.isEmpty() || (declared != null && !declared.equals(mime)))
            throw new IllegalArgumentException("Choose a JPEG, PNG, WebP or animated GIF image.");
        if (mime.equals("image/gif")) return new Prepared(source,mime); // byte-for-byte animation preservation
        // ImageDecoder applies encoded EXIF orientation before scaling and strips location metadata.
        Bitmap bitmap;
        com.bumptech.glide.request.FutureTarget<Bitmap> legacyTarget=null;
        if (android.os.Build.VERSION.SDK_INT < 28) {
            legacyTarget = Glide.with(activity.getApplicationContext()).asBitmap().load(source)
                    .downsample(com.bumptech.glide.load.resource.bitmap.DownsampleStrategy.AT_MOST)
                    .diskCacheStrategy(DiskCacheStrategy.NONE).skipMemoryCache(true).submit(DIMENSION,DIMENSION);
            bitmap=legacyTarget.get();
        } else bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(source)), (decoder, info, unused) -> {
            int w=info.getSize().getWidth(), h=info.getSize().getHeight();
            float scale=Math.min(1f, DIMENSION/(float)Math.max(w,h));
            decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
        try {
            for (int quality : new int[]{90, 84, 78}) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG,quality,out);
                if (out.size() <= PHOTO_LIMIT) return new Prepared(out.toByteArray(),"image/jpeg");
            }
            throw new IllegalArgumentException("Unable to prepare this photo at a reasonable quality. Choose a smaller image.");
        } finally {
            if(legacyTarget!=null) Glide.with(activity.getApplicationContext()).clear(legacyTarget);
            else bitmap.recycle();
        }
    }
    static void pick(Activity activity, ZeroChillSocialRepository.Callback<Prepared> callback) {
        Picker fragment = new Picker(); fragment.callback = callback;
        activity.getFragmentManager().beginTransaction().add(fragment,"zc-social-picker").commit();
    }
    public static final class Picker extends Fragment {
        ZeroChillSocialRepository.Callback<Prepared> callback;
        @Override public void onCreate(Bundle state) {
            super.onCreate(state);
            if (state != null) { getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss(); return; }
            Intent intent = new androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
                    .createIntent(getActivity(), new androidx.activity.result.PickVisualMediaRequest.Builder()
                            .setMediaType(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
            startActivityForResult(intent,73);
        }
        @Override public void onActivityResult(int request,int result,Intent data) {
            Activity host=getActivity();
            if (host==null) return;
            ZeroChillSocialRepository.Callback<Prepared> done=callback;
            getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
            if (done==null) return;
            if (result!=Activity.RESULT_OK || data==null || data.getData()==null) { done.complete(null,null); return; }
            WORK.execute(() -> {
                Prepared prepared=null; Exception failure=null;
                try { prepared=prepare(host,data.getData()); } catch(Exception e) { failure=e; }
                Prepared value=prepared; Exception error=failure;
                host.runOnUiThread(() -> { if (!host.isDestroyed()) done.complete(value,error); });
            });
        }
    }
    static final class Composer extends LinearLayout {
        Prepared selected;
        final TextView control;
        final ImageView preview;
        boolean busy, preparing;
        int preparationGeneration;
        Runnable changed = () -> {};

        Composer(Activity activity) {
            super(activity);
            setOrientation(HORIZONTAL);
            setGravity(android.view.Gravity.CENTER_VERTICAL);
            setVisibility(GONE);
            control = new TextView(activity);
            control.setText("＋");
            control.setTextSize(24);
            control.setTextColor(AppearanceTheme.accent(activity));
            control.setGravity(android.view.Gravity.CENTER);
            control.setContentDescription("Attach photo or GIF");
            control.setLayoutParams(new LayoutParams(dp(48), dp(48)));
            preview = new ImageView(activity);
            preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            addView(preview, new LayoutParams(dp(72), dp(64)));
            TextView remove = new TextView(activity);
            remove.setText("×");
            remove.setTextSize(24);
            remove.setTextColor(AppearanceTheme.accent(activity));
            remove.setGravity(android.view.Gravity.CENTER);
            remove.setContentDescription("Remove attachment");
            addView(remove, new LayoutParams(dp(48), dp(48)));
            remove.setOnClickListener(
                    v -> {
                        if (!busy && !preparing) clear();
                    });
            control.setOnClickListener(
                    v -> {
                        if (busy || preparing) return;
                        if (!ZeroChillAccountRepository.hasStoredSession(activity)) {
                            activity.startActivity(
                                    new Intent(activity, ZeroChillAccountActivity.class));
                            return;
                        }
                        String owner = ZeroChillSessionStore.currentUserId(activity);
                        int generation = ++preparationGeneration;
                        preparing = true;
                        refresh();
                        pick(
                                activity,
                                (media, error) -> {
                                    if (generation != preparationGeneration) return;
                                    preparing = false;
                                    if (owner.equals(
                                            ZeroChillSessionStore.currentUserId(activity))) {
                                        if (error != null)
                                            ZeroChillToast.makeText(
                                                            activity,
                                                            error.getMessage(),
                                                            ZeroChillToast.LENGTH_LONG)
                                                    .show();
                                        else if (media != null) {
                                            select(media);
                                        }
                                    }
                                    refresh();
                                });
                    });
        }

        private int dp(int value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }

        void enableKeyboard(android.widget.EditText input, java.util.function.BooleanSupplier allowed) {
            new SocialKeyboardContent((Activity) getContext(), input, this, allowed).install();
        }

        void select(Prepared media) {
            selected = media;
            Glide.with(preview).load(media.bytes).diskCacheStrategy(DiskCacheStrategy.NONE).into(preview);
        }

        void refresh() {
            setVisibility(selected == null ? GONE : VISIBLE);
            control.setEnabled(!busy && !preparing);
            control.setText(busy || preparing ? "…" : "＋");
            control.setContentDescription(
                    busy
                            ? "Uploading / sending attachment"
                            : preparing ? "Preparing image" : "Attach photo or GIF");
            changed.run();
        }

        void busy(boolean value) {
            busy = value;
            refresh();
        }

        void clear() {
            cancelPreparation();
            selected = null;
            Glide.with(preview).clear(preview);
            refresh();
        }

        void cancelPreparation() {
            preparationGeneration++;
            preparing = false;
            refresh();
        }

        @Override protected void onDetachedFromWindow() {
            cancelPreparation();
            super.onDetachedFromWindow();
        }
    }

    /**
     * Measure from the decoded drawable, including animated GIFs, without cropping or stretching.
     */
    static final class MediaView extends androidx.appcompat.widget.AppCompatImageView {
        final int maximumWidth, maximumHeight;

        MediaView(Activity activity, int width, int height) {
            super(activity);
            float density = getResources().getDisplayMetrics().density;
            maximumWidth = Math.round(width * density);
            maximumHeight = Math.round(height * density);
            setScaleType(ScaleType.FIT_CENTER);
            setAdjustViewBounds(true);
            setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
            setOutlineProvider(
                    new android.view.ViewOutlineProvider() {
                        public void getOutline(View v, android.graphics.Outline o) {
                            o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 12 * density);
                        }
                    });
            setClipToOutline(true);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width =
                    Math.min(
                            maximumWidth,
                            MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
                                    ? maximumWidth
                                    : MeasureSpec.getSize(widthSpec));
            int height =
                    Math.min(
                            maximumHeight,
                            MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED
                                    ? maximumHeight
                                    : MeasureSpec.getSize(heightSpec));
            android.graphics.drawable.Drawable drawable = getDrawable();
            int naturalWidth = drawable == null ? width : Math.max(1, drawable.getIntrinsicWidth());
            int naturalHeight =
                    drawable == null ? height : Math.max(1, drawable.getIntrinsicHeight());
            float scale =
                    Math.min(
                            1f,
                            Math.min(width / (float) naturalWidth, height / (float) naturalHeight));
            setMeasuredDimension(
                    Math.max(1, Math.round(naturalWidth * scale)),
                    Math.max(1, Math.round(naturalHeight * scale)));
        }
    }

    private static final class Binding {
        final Object scope;
        final String bucket, path;
        java.util.concurrent.Future<?> preparation;
        boolean preparing = true;
        Binding(Object scope, String bucket, String path) {
            this.scope = scope; this.bucket = bucket; this.path = path;
        }
        boolean current(ImageView image) {
            return image.getTag(R.id.social_media_binding) == this
                    && SocialMediaSession.valid(scope, image.getContext())
                    && (!(image.getContext() instanceof Activity)
                    || !((Activity) image.getContext()).isDestroyed());
        }
    }

    static void clear(ImageView image) {
        Binding binding = (Binding) image.getTag(R.id.social_media_binding);
        image.setTag(R.id.social_media_binding, null);
        if (binding != null && binding.preparation != null) binding.preparation.cancel(true);
        SocialMediaSession.forget(image);
        Glide.with(image.getContext().getApplicationContext()).clear(image);
        image.setImageDrawable(null);
    }

    static void bind(ImageView image, String bucket, String path) {
        Object scope = SocialMediaSession.current(image.getContext());
        Binding old = (Binding) image.getTag(R.id.social_media_binding);
        com.bumptech.glide.request.Request request =
                new com.bumptech.glide.request.target.DrawableImageViewTarget(image).getRequest();
        if (!path.isEmpty() && old != null && old.scope == scope
                && old.bucket.equals(bucket) && old.path.equals(path)
                && (old.preparing || (request != null && (request.isRunning() || request.isComplete()) && !request.isCleared()))) return;
        clear(image);
        image.setVisibility(path.isEmpty() ? View.GONE : View.VISIBLE);
        if (path.isEmpty()) return;
        Binding binding = new Binding(scope, bucket, path);
        image.setTag(R.id.social_media_binding, binding);
        SocialMediaSession.track(image);
        binding.preparation = WORK.submit(() -> {
            try {
                String token = ZeroChillAccountRepository.hasStoredSession(image.getContext())
                        ? ZeroChillAccountRepository.accessTokenBlocking(image.getContext()) : "";
                LazyHeaders.Builder headers = new LazyHeaders.Builder()
                        .addHeader("apikey", BuildConfig.ACCOUNT_SUPABASE_PUBLISHABLE_KEY);
                if (!token.isEmpty()) headers.addHeader("Authorization", "Bearer " + token);
                else if (BuildConfig.ACCOUNT_SUPABASE_PUBLISHABLE_KEY.startsWith("eyJ"))
                    headers.addHeader("Authorization", "Bearer " + BuildConfig.ACCOUNT_SUPABASE_PUBLISHABLE_KEY);
                GlideUrl url = new GlideUrl(BuildConfig.ACCOUNT_SUPABASE_URL
                        + "/storage/v1/object/authenticated/" + bucket + "/" + path, headers.build());
                UI.post(() -> {
                    if (!binding.current(image)) return;
                    binding.preparing = false;
                    int width = image instanceof MediaView ? ((MediaView) image).maximumWidth
                            : image.getResources().getDisplayMetrics().widthPixels;
                    int height = image instanceof MediaView ? ((MediaView) image).maximumHeight
                            : Math.round(image.getResources().getDisplayMetrics().heightPixels * 0.72f);
                    Glide.with(image).load(url).signature(new com.bumptech.glide.signature.ObjectKey(scope))
                            .override(width, height).fitCenter()
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                                @Override public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException error,
                                        Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                        boolean first) { return !binding.current(image); }
                                @Override public boolean onResourceReady(android.graphics.drawable.Drawable resource,
                                        Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                        com.bumptech.glide.load.DataSource source, boolean first) {
                                    return !binding.current(image);
                                }
                            }).error(R.drawable.ic_more_account).into(image);
                });
            } catch (Exception failure) {
                UI.post(() -> {
                    if (!binding.current(image)) return;
                    binding.preparing = false;
                    image.setImageResource(R.drawable.ic_more_account);
                });
            }
        });
    }
    static ImageView inline(Activity activity,String bucket,String path) {return inline(activity,bucket,path,false);}
    static ImageView inline(Activity activity,String bucket,String path,boolean message) {
        ImageView image=new MediaView(activity,message?250:220,message?280:150);
        image.setContentDescription("Attachment. Tap to enlarge");bind(image,bucket,path);
        image.setOnClickListener(v->showViewer(activity,bucket,path));return image;
    }
    static void showViewer(Activity activity,String bucket,String path) {
        if (activity.isDestroyed() || activity.isFinishing() || path.isEmpty()) return;
        ImageView large = new ImageView(activity);
        large.setScaleType(ImageView.ScaleType.FIT_CENTER);
        large.setContentDescription("Attachment viewer");
        android.widget.FrameLayout viewport = new android.widget.FrameLayout(activity);
        viewport.setBackgroundColor(android.graphics.Color.BLACK);
        viewport.addView(large, new android.widget.FrameLayout.LayoutParams(-1, -1));
        android.app.Dialog dialog = new android.app.Dialog(activity, R.style.Theme_ZeroChill_AttachmentViewer);
        dialog.setContentView(viewport);
        ImageView close = new ImageView(activity);
        close.setImageResource(R.drawable.ic_more_close);
        close.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
        close.setScaleType(ImageView.ScaleType.CENTER);
        close.setContentDescription("Close attachment");
        close.setFocusable(true);
        int target = Math.round(48 * activity.getResources().getDisplayMetrics().density);
        android.widget.FrameLayout.LayoutParams closeParams = new android.widget.FrameLayout.LayoutParams(target, target,
                android.view.Gravity.TOP | android.view.Gravity.END);
        viewport.addView(close, closeParams);
        close.setOnClickListener(v -> dialog.dismiss());
        ZeroChillMotion.installPressFeedback(close);
        // Keep the close target clear of cutouts and navigation in edge-to-edge windows.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(viewport, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        dialog.setOnDismissListener(ignored -> clear(large));
        ZeroChillToast.registerDialog(dialog);
        dialog.show();
        dialog.getWindow().setLayout(-1, -1);
        androidx.core.view.ViewCompat.requestApplyInsets(viewport);
        bind(large, bucket, path);
    }

}
