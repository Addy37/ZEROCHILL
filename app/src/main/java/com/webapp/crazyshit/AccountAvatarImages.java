package com.webapp.crazyshit;

import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import java.util.WeakHashMap;

/** Rebind already displayed account avatars after a successful local profile update. */
final class AccountAvatarImages {
    private static final WeakHashMap<ImageView, String> displayed = new WeakHashMap<>();

    static void bind(ImageView view, String userId, String path) {
        bindUrl(view, userId, ZeroChillAccountRepository.avatarUrl(path));
    }

    static void track(ImageView view, String userId) {
        if (userId == null || userId.isEmpty()) displayed.remove(view);
        else displayed.put(view, userId);
    }

    static void bindUrl(ImageView view, String userId, String url) {
        track(view, userId);
        Glide.with(view).clear(view);
        if (url.isEmpty()) {
            view.setImageResource(R.drawable.ic_more_account);
        } else {
            view.clearColorFilter();
            view.setPadding(0, 0, 0, 0);
            Glide.with(view).load(url).circleCrop().transition(ThumbnailFades.avatar())
                    .placeholder(R.drawable.ic_more_account).error(R.drawable.ic_more_account).into(view);
        }
    }

    static void changed(String userId, String path) {
        Runnable update = () -> {
            for (java.util.Map.Entry<ImageView, String> item : new java.util.ArrayList<>(displayed.entrySet())) {
                ImageView view = item.getKey();
                if (view == null || !userId.equals(item.getValue())) continue;
                android.content.Context context = view.getContext();
                if (context instanceof android.app.Activity
                        && (((android.app.Activity) context).isDestroyed()
                        || ((android.app.Activity) context).isFinishing())) continue;
                bind(view, userId, path);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) update.run();
        else new Handler(Looper.getMainLooper()).post(update);
    }
}
