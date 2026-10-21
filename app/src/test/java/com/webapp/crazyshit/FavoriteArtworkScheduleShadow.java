package com.webapp.crazyshit;

import android.content.Context;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** UI fixtures exercise publication without making live creator requests. */
@Implements(value = FavoriteCreatorArtworkHydrator.class, isInAndroidSdk = false)
public class FavoriteArtworkScheduleShadow {
    static int calls;
    @Implementation protected static void schedule(Context context) { calls++; }
}
