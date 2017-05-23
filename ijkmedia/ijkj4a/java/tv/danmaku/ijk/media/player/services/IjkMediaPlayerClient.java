package tv.danmaku.ijk.media.player.services;

import android.os.Bundle;

@SimpleCClassName
public class IjkMediaPlayerClient {
    private long mNativeMediaPlayerClient;
    private long mNativeMediaDataSource;
    private long mNativeAndroidIO;

    private static void postEventFromNative(Object weakThiz, int what, int arg1, int arg2, Object obj);
    private static String onSelectCodec(Object weakThiz, String mimeType, int profile, int level);
    private static boolean onNativeInvoke(Object weakThiz, int what, Bundle args);
}
