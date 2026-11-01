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
            byte[] chunk = new byte[16384]; int size; boolean gif=false;
            while ((size = in.read(chunk)) != -1) {
                if (out.size() + size > PHOTO_SOURCE_LIMIT) throw new IllegalArgumentException("This photo is too large. Maximum source size is 20 MB.");
                if(out.size()==0) gif=signature(java.util.Arrays.copyOf(chunk,size)).equals("image/gif");
                out.write(chunk,0,size);
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
        boolean busy;
        boolean preparing;
        Composer(Activity activity) {
            super(activity); setOrientation(VERTICAL);
            control=new TextView(activity); control.setText("＋ Attach photo / GIF"); control.setTextColor(UiPalette.PRIMARY);
            control.setPadding(12,12,12,12); control.setMinHeight(Math.round(48*getResources().getDisplayMetrics().density));
            addView(control);
            preview=new ImageView(activity); preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            addView(preview,new LayoutParams(-1,Math.round(100*getResources().getDisplayMetrics().density)));
            preview.setVisibility(GONE);
            control.setOnClickListener(v -> {
                if (busy) return;
                if (selected!=null) { clear(); return; }
                if (!ZeroChillAccountRepository.hasStoredSession(activity)) return;
                String owner=ZeroChillSessionStore.currentUserId(activity);
                preparing=true; control.setEnabled(false); control.setText("Preparing image…");
                pick(activity,(media,error) -> {
                    preparing=false; control.setEnabled(true); control.setText("＋ Attach photo / GIF");
                    if (!owner.equals(ZeroChillSessionStore.currentUserId(activity))) return;
                    if(error!=null) { ZeroChillToast.makeText(activity,error.getMessage(),ZeroChillToast.LENGTH_LONG).show(); return; }
                    if(media==null) return;
                    selected=media; preview.setVisibility(VISIBLE);
                    Glide.with(preview).load(media.bytes).diskCacheStrategy(DiskCacheStrategy.NONE).into(preview);
                    control.setText(media.fallback()+" selected · Remove");
                });
            });
        }
        void busy(boolean value) { busy=value; control.setEnabled(!value); if(value) control.setText("Uploading / sending…");
            else control.setText(selected==null ? "＋ Attach photo / GIF" : selected.fallback()+" selected · Remove / retry send"); }
        void clear() { selected=null; preview.setVisibility(GONE); Glide.with(preview).clear(preview); control.setText("＋ Attach photo / GIF"); }
    }
    static void bind(ImageView image, String bucket, String path) {
        String binding=ZeroChillSessionStore.currentUserId(image.getContext())+":"+bucket+":"+path;
        if(!path.isEmpty() && binding.equals(image.getTag())) return;
        Glide.with(image).clear(image); image.setImageDrawable(null);
        image.setTag(binding);
        image.setVisibility(path.isEmpty()?View.GONE:View.VISIBLE);
        if(path.isEmpty()) return;
        String owner=ZeroChillSessionStore.currentUserId(image.getContext());

        WORK.execute(() -> {
            try {
                String token=ZeroChillAccountRepository.hasStoredSession(image.getContext())
                        ? ZeroChillAccountRepository.accessTokenBlocking(image.getContext()) : "";
                LazyHeaders.Builder headers=new LazyHeaders.Builder().addHeader("apikey",BuildConfig.ACCOUNT_SUPABASE_PUBLISHABLE_KEY);
                if(!token.isEmpty()) headers.addHeader("Authorization","Bearer "+token);
                else if(BuildConfig.ACCOUNT_SUPABASE_PUBLISHABLE_KEY.startsWith("eyJ")) headers.addHeader("Authorization","Bearer "+BuildConfig.ACCOUNT_SUPABASE_PUBLISHABLE_KEY);
                GlideUrl url = new GlideUrl(BuildConfig.ACCOUNT_SUPABASE_URL+"/storage/v1/object/authenticated/"+bucket+"/"+path,
                        headers.build());
                image.post(() -> {
                    if (!binding.equals(image.getTag()) || !owner.equals(ZeroChillSessionStore.currentUserId(image.getContext()))) return;
                    Glide.with(image).load(url).diskCacheStrategy(DiskCacheStrategy.NONE).skipMemoryCache(true)
                            .error(R.drawable.ic_more_account).into(image);
                });
            } catch(Exception ignored) { }
        });
    }
    static ImageView inline(Activity activity,String bucket,String path) {
        ImageView image=new ImageView(activity); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription("Attachment. Tap to enlarge");
        image.setLayoutParams(new LinearLayout.LayoutParams(-1,Math.round(200*activity.getResources().getDisplayMetrics().density)));
        bind(image,bucket,path);
        image.setOnClickListener(v -> {
            ImageView large=new ImageView(activity); large.setAdjustViewBounds(true); large.setScaleType(ImageView.ScaleType.FIT_CENTER);
            bind(large,bucket,path);
            new ZeroChillDialog.Builder(activity).setView(large).setPositiveButton("Close",null).show();
        });
        return image;
    }
}
