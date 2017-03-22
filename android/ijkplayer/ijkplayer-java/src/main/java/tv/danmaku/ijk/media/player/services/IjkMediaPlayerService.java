/*
 * Copyright (C) 2006 Bilibili
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (C) 2016 Raymond Zheng <raymondzheng1412@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tv.danmaku.ijk.media.player.services;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import tv.danmaku.ijk.media.player.IIjkMediaPlayer;
import tv.danmaku.ijk.media.player.IIjkMediaPlayerClient;
import tv.danmaku.ijk.media.player.IjkLibLoader;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.annotations.AccessedByNative;
import tv.danmaku.ijk.media.player.annotations.CalledByNative;
import tv.danmaku.ijk.media.player.ffmpeg.FFmpegApi;
import tv.danmaku.ijk.media.player.misc.IMediaDataSource;
import tv.danmaku.ijk.media.player.misc.SystemHttp;
import tv.danmaku.ijk.media.player.pragma.DebugLog;
import tv.danmaku.ijk.media.player.misc.IAndroidIO;

public class IjkMediaPlayerService extends Service {
    private static final String TAG = "IjkMediaPlayerService";
    private IIjkMediaPlayerClient mClient = null;
    private EventHandler mEventHandler = null;
    private HandlerThread mHandlerThread = null;
    private Handler mProtectHandle = null;
    private Bundle mLibBundle;
    private SystemHttp mSystemHttp;
    private static final int MSG_NATIVE_PROTECT_CREATE               = 1;
    private static final int MSG_NATIVE_PROTECT_START                = 2;
    private static final int MSG_NATIVE_PROTECT_PAUSE                = 3;
    private static final int MSG_NATIVE_PROTECT_STOP                 = 4;
    private static final int MSG_NATIVE_PROTECT_RELEASE              = 5;
    private static final int MSG_NATIVE_PROTECT_RESET                = 6;
    private static final int MSG_NATIVE_PROTECT_SETSURFACE           = 7;
    private static final int MSG_NATIVE_PROTECT_SETDATASOURCE        = 8;
    private static final int MSG_NATIVE_PROTECT_SETDATASOURCEBASE64  = 9;
    private static final int MSG_NATIVE_PROTECT_SETDATASOURCEKEY     = 10;
    private static final int MSG_NATIVE_PROTECT_SETDATASOURCEFD      = 11;
    private static final int MSG_NATIVE_PROTECT_PREPAREASYNC         = 12;
    private static final int MSG_NATIVE_PROTECT_SETSTREAMSELECTED    = 13;
    private static final int MSG_NATIVE_PROTECT_ISPLAYING            = 14;
    private static final int MSG_NATIVE_PROTECT_SEEKTO               = 15;
    private static final int MSG_NATIVE_PROTECT_GETCURRENTPOSITION   = 16;
    private static final int MSG_NATIVE_PROTECT_GETDURATION          = 17;
    private static final int MSG_NATIVE_PROTECT_SETLOOPCOUNT         = 18;
    private static final int MSG_NATIVE_PROTECT_GETLOOPCOUNT         = 19;
    private static final int MSG_NATIVE_PROTECT_GETPROPERTYFLOAT     = 20;
    private static final int MSG_NATIVE_PROTECT_SETPROPERTYFLOAT     = 21;
    private static final int MSG_NATIVE_PROTECT_GETPROPERTYLOOG      = 22;
    private static final int MSG_NATIVE_PROTECT_SETPROPERTYLOOG      = 23;
    private static final int MSG_NATIVE_PROTECT_SETVOLUME            = 24;
    private static final int MSG_NATIVE_PROTECT_GETAUDIOSESSIONID    = 25;
    private static final int MSG_NATIVE_PROTECT_GETVIDEOCODECINFO    = 26;
    private static final int MSG_NATIVE_PROTECT_GETADUIOCODECINFO    = 27;
    private static final int MSG_NATIVE_PROTECT_SETOPTIONSTRING      = 28;
    private static final int MSG_NATIVE_PROTECT_SETOPTIONLONG        = 29;
    private static final int MSG_NATIVE_PROTECT_GETMEDIAMETA         = 30;
    private static final int MSG_NATIVE_PROTECT_NATIVEFINALIZE       = 31;
    private static final int MSG_NATIVE_PROTECT_GETCOLORFORMATNAME   = 32;
    private static final int MSG_NATIVE_PROTECT_NATIVEPROFILEBEGIN   = 33;
    private static final int MSG_NATIVE_PROTECT_NATIVEPROFILEEND     = 34;
    private static final int MSG_NATIVE_PROTECT_NATIVESETLOGLEVEL    = 35;
    private static final int MSG_NATIVE_PROTECT_SETANDROIDIOCALLBACK = 36;



    private static final int PROTECT_DELAY = 3 * 1000;

    @AccessedByNative
    private long mNativeMediaPlayer;
    @AccessedByNative
    private long mNativeMediaDataSource;

    @AccessedByNative
    private int mNativeSurfaceTexture;

    @AccessedByNative
    private int mListenerContext;

    @AccessedByNative
    private long mNativeAndroidIO;

    private static native void _native_init();

    private native void _native_setup(Object IjkMediaPlayerService_this);

    private native void _native_finalize();

    private native void _native_message_loop(Object IjkMediaPlayerService_this);

    /*
     * Update the IjkMediaPlayer SurfaceTexture. Call after setting a new
     * display surface.
     */
    private native void _setVideoSurface(Surface surface);

    private native void _setDataSource(String path, String[] keys, String[] values)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException;

    private native void _setDataSourceFd(int fd)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException;

    private native void _setDataSource(IMediaDataSource mediaDataSource)
            throws IllegalArgumentException, SecurityException, IllegalStateException;

    private native void _setAndroidIOCallback(IAndroidIO androidIO)
            throws IllegalArgumentException, SecurityException, IllegalStateException;

    public native void _prepareAsync() throws IllegalStateException;

    private native void _start() throws IllegalStateException;

    private native void _stop() throws IllegalStateException;

    private native void _pause() throws IllegalStateException;

    private native void _setStreamSelected(int stream, boolean select);

    public native boolean _isPlaying();

    public native void _seekTo(long msec) throws IllegalStateException;

    public native long _getCurrentPosition();

    public native long _getDuration();

    private native void _release();

    private native void _reset();

    private native void _setLoopCount(int loopCount);

    private native int _getLoopCount();

    private native float _getPropertyFloat(int property, float defaultValue);

    private native void _setPropertyFloat(int property, float value);

    private native long _getPropertyLong(int property, long defaultValue);

    private native void _setPropertyLong(int property, long value);

    public native void _setVolume(float leftVolume, float rightVolume);

    public native int _getAudioSessionId();

    private native String _getVideoCodecInfo();

    private native String _getAudioCodecInfo();

    private native void _setOption(int category, String name, String value);

    private native void _setOption(int category, String name, long value);

    private native Bundle _getMediaMeta();

    public static native String _getColorFormatName(int mediaCodecColorFormat);

    public static native void _native_profileBegin(String libName);

    public static native void _native_profileEnd();

    public static native void _native_setLogLevel(int level);

    @CalledByNative
    private static boolean onNativeInvoke(Object weakThiz, int what, Bundle args) {
        DebugLog.ifmt(TAG, "onNativeInvoke %d", what);
        if (weakThiz == null || !(weakThiz instanceof WeakReference<?>))
            throw new IllegalStateException("<null weakThiz>.onNativeInvoke()");

        @SuppressWarnings("unchecked")
        WeakReference<IjkMediaPlayerService> weakPlayer = (WeakReference<IjkMediaPlayerService>) weakThiz;
        IjkMediaPlayerService playerService = weakPlayer.get();
        if (playerService == null)
            throw new IllegalStateException("<null weakPlayer>.onNativeInvoke()");
        return playerService.onNativeInvokeForClient(what, args);
    }

    private boolean onNativeInvokeForClient(int what, Bundle args) {
        if (mClient != null) {
            try {
                return mClient.onNativeInvoke(what, args);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public class SystemApplication {
        public void notifyNativeInvoke(int what, Bundle args) {
            if (mClient != null) {
                try {
                    mClient.onNativeInvoke(what, args);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    @CalledByNative
    private static String onSelectCodec(Object weakThiz, String mimeType, int profile, int level) {
        if (weakThiz == null || !(weakThiz instanceof WeakReference<?>))
            return null;

        @SuppressWarnings("unchecked")
        WeakReference<IjkMediaPlayerService> weakPlayer = (WeakReference<IjkMediaPlayerService>) weakThiz;
        IjkMediaPlayerService playerService = weakPlayer.get();
        if (playerService == null)
            return null;
        return playerService.onSelectCodecForClient(mimeType, profile, level);
    }

    private String onSelectCodecForClient(String mimeType, int profile, int level) {
        if (mClient != null) {
            try {
                return mClient.onMediaCodecSelect(mimeType, profile, level);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /*
     * Called from native code when an interesting event happens. This method
     * just uses the EventHandler system to post the event back to the main app
    * thread. We use a weak reference to the original IjkMediaPlayer object so
    * that the native code is safe from the object disappearing from underneath
    * it. (This is the cookie passed to native_setup().)
    */
    @CalledByNative
    private static void postEventFromNative(Object weakThiz, int what,
                                            int arg1, int arg2, Object obj) {
        if (weakThiz == null)
            return;

        @SuppressWarnings("rawtypes")
        IjkMediaPlayerService ijkService = (IjkMediaPlayerService) ((WeakReference) weakThiz).get();
        if (ijkService == null) {
            return;
        }

        if (ijkService.mEventHandler != null) {
            Message m = ijkService.mEventHandler.obtainMessage(what, arg1, arg2, obj);
            ijkService.mEventHandler.sendMessage(m);
        }
    }

    private void eventHandlerForClient(int what, int arg1, int arg2) {
        if (mClient != null) {
            try {
                mClient.onEventHandler(what, arg1, arg2);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private static class EventHandler extends Handler {
        private final WeakReference<IjkMediaPlayerService> mWeakPlayer;

        public EventHandler(IjkMediaPlayerService mp, Looper looper) {
            super(looper);
            mWeakPlayer = new WeakReference<IjkMediaPlayerService>(mp);
        }

        @Override
        public void handleMessage(Message msg) {
            IjkMediaPlayerService player = mWeakPlayer.get();
            if (player == null || player.mNativeMediaPlayer == 0) {
                DebugLog.w(TAG,
                        "IjkMediaPlayerService went away with unhandled events");
                return;
            }
            player.eventHandlerForClient(msg.what, msg.arg1, msg.arg2);
        }
    }

    /**
     * Default library loader
     * Load them by yourself, if your libraries are not installed at default place.
     */
    private static final IjkLibLoader sLocalLibLoader = new IjkLibLoader() {
        @Override
        public void loadLibrary(String libName) throws UnsatisfiedLinkError, SecurityException {
            System.loadLibrary(libName);
        }

        @Override
        public File findLibrary(String libName) {
            return null;
        }
    };

    private static volatile boolean mIsLibLoaded = false;

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    public static void loadLibrariesOnce(Bundle bundle) {
        synchronized (IjkMediaPlayerService.class) {
            if (!mIsLibLoaded) {
                if (bundle != null && bundle.get("ijkffmpeg") != null &&
                        bundle.get("ijksdl") != null && bundle.get("ijkffmpeg") != null) {
                    System.load(bundle.get("ijkffmpeg").toString());
                    System.load(bundle.get("ijksdl").toString());
                    System.load(bundle.get("ijkplayer").toString());
                } else {
                    sLocalLibLoader.loadLibrary("ijkffmpeg");
                    sLocalLibLoader.loadLibrary("ijksdl");
                    sLocalLibLoader.loadLibrary("ijkplayer");
                }
                mIsLibLoaded = true;
            }
        }
    }

    private static volatile boolean mIsNativeInitialized = false;

    private static void initNativeOnce() {
        synchronized (IjkMediaPlayer.class) {
            if (!mIsNativeInitialized) {
                _native_init();
                mIsNativeInitialized = true;
            }
        }
    }

    private void initPlayer() {
        loadLibrariesOnce(mLibBundle);
        initNativeOnce();
        if (mEventHandler == null) {
            Looper looper;
            if ((looper = Looper.myLooper()) != null) {
                mEventHandler = new EventHandler(this, looper);
            } else if ((looper = Looper.getMainLooper()) != null) {
                mEventHandler = new EventHandler(this, looper);
            } else {
                mEventHandler = null;
            }
        }
        _release();
        _native_setup(new WeakReference<IjkMediaPlayerService>(this));
    }

    IIjkMediaPlayer.Stub mBinder = new IIjkMediaPlayer.Stub() {

        @Override
        public void registerClient(IIjkMediaPlayerClient client) {
            mClient = client;
        }

        @Override
        public void unregisterClient() {
            mClient = null;
        }

        @Override
        public void create() {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_CREATE, PROTECT_DELAY);
            initPlayer();
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_CREATE);
        }

        @Override
        public void start() {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_START, PROTECT_DELAY);
            try {
                _start();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_START);
        }

        @Override
        public void pause() {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_PAUSE, PROTECT_DELAY);
            try {
                _pause();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_PAUSE);
        }

        @Override
        public void stop() {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_STOP, PROTECT_DELAY);
            try {
                _stop();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_STOP);
        }

        @Override
        public void release() {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_RELEASE, PROTECT_DELAY);
            if (mSystemHttp != null) {
                mSystemHttp.abort();
            }
            _release();
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_RELEASE);
        }

        @Override
        public void reset() {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_RESET, PROTECT_DELAY);
            _reset();
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_RESET);
        }

        @Override
        public void setSurface(Surface surface) {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_SETSURFACE, PROTECT_DELAY);
            _setVideoSurface(surface);
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_SETSURFACE);
        }

        @Override
        public long getAndroidIOTrafficStatistic() {
            if (mSystemHttp != null) {
                return mSystemHttp.getAndroidIOTrafficStatistic();
            }
            return 0;
        }

        @Override
        public void setAndroidIOCallback() {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_SETANDROIDIOCALLBACK, PROTECT_DELAY);
            try {
                mSystemHttp = new SystemHttp();
                mSystemHttp.injectCallback(new SystemApplication());
                _setAndroidIOCallback(mSystemHttp);
            } catch (IllegalArgumentException ex) {
                ex.printStackTrace();
            } catch (SecurityException ex) {
                ex.printStackTrace();
            } catch (IllegalStateException ex) {
                ex.printStackTrace();
            }
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_SETANDROIDIOCALLBACK);
        }

        @Override
        public void setDataSource(String path) {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_SETDATASOURCE, PROTECT_DELAY);
            try {
                _setDataSource(path, null, null);
            } catch (IOException ex) {
                ex.printStackTrace();
            } catch (IllegalArgumentException ex) {
                ex.printStackTrace();
            } catch (SecurityException ex) {
                ex.printStackTrace();
            } catch (IllegalStateException ex) {
                ex.printStackTrace();
            }
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_SETDATASOURCE);
        }

        @Override
        public void setDataSourceBase64(String path) {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_SETDATASOURCEBASE64, PROTECT_DELAY);
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("data:content/type;base64,");
                sb.append(FFmpegApi.av_base64_encode(path.getBytes()));
                _setDataSource(sb.toString(), null, null);
            } catch (IOException ex) {
                ex.printStackTrace();
            } catch (IllegalArgumentException ex) {
                ex.printStackTrace();
            } catch (SecurityException ex) {
                ex.printStackTrace();
            } catch (IllegalStateException ex) {
                ex.printStackTrace();
            }
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_SETDATASOURCEBASE64);
        }

        @Override
        public void setDataSourceKey(String path, String[] keys, String[] values) {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_SETDATASOURCEKEY, PROTECT_DELAY);
            try {
                _setDataSource(path, keys, values);
            } catch (IOException ex) {
                ex.printStackTrace();
            } catch (IllegalArgumentException ex) {
                ex.printStackTrace();
            } catch (SecurityException ex) {
                ex.printStackTrace();
            } catch (IllegalStateException ex) {
                ex.printStackTrace();
            }
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_SETDATASOURCEKEY);
        }

        @Override
        public void setDataSourceFd(int fd) {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_SETDATASOURCEFD, PROTECT_DELAY);
            try {
                _setDataSourceFd(fd);
            } catch (IOException ex) {
                ex.printStackTrace();
            } catch (IllegalArgumentException ex) {
                ex.printStackTrace();
            } catch (SecurityException ex) {
                ex.printStackTrace();
            } catch (IllegalStateException ex) {
                ex.printStackTrace();
            }
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_SETDATASOURCEFD);
        }

        @Override
        public void prepareAsync() {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_PREPAREASYNC, PROTECT_DELAY);
            try {
                _prepareAsync();
            } catch (IllegalStateException ex) {
                ex.printStackTrace();
            }
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_PREPAREASYNC);
        }

        @Override
        public void setStreamSelected(int stream, boolean select) {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_SETSTREAMSELECTED, PROTECT_DELAY);
            _setStreamSelected(stream, select);
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_SETSTREAMSELECTED);
        }

        @Override
        public boolean isPlaying() {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_ISPLAYING, PROTECT_DELAY);
            boolean ret = _isPlaying();
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_ISPLAYING);
            return ret;
        }

        @Override
        public void seekTo(long msec) {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_SEEKTO, PROTECT_DELAY);
            try {
                _seekTo(msec);
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_SEEKTO);
        }

        @Override
        public long getCurrentPosition() {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_GETCURRENTPOSITION, PROTECT_DELAY);
            long ret = _getCurrentPosition();
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_GETCURRENTPOSITION);
            return ret;
        }

        @Override
        public long getDuration() {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_GETDURATION, PROTECT_DELAY);
            long ret = _getDuration();
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_GETDURATION);
            return ret;
        }

        @Override
        public void setLoopCount(int loopCount) {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_SETLOOPCOUNT, PROTECT_DELAY);
            _setLoopCount(loopCount);
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_SETLOOPCOUNT);
        }

        @Override
        public int getLoopCount() {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_GETLOOPCOUNT, PROTECT_DELAY);
            int ret = _getLoopCount();
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_GETLOOPCOUNT);
            return ret;
        }

        @Override
        public float getPropertyFloat(int property, float defaultValue) {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_GETPROPERTYFLOAT, PROTECT_DELAY);
            float ret = _getPropertyFloat(property, defaultValue);
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_GETPROPERTYFLOAT);
            return ret;
        }

        @Override
        public void setPropertyFloat(int property, float value) {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_SETPROPERTYFLOAT, PROTECT_DELAY);
            _setPropertyFloat(property, value);
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_SETPROPERTYFLOAT);
        }

        @Override
        public long getPropertyLong(int property, long defaultValue) {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_GETPROPERTYLOOG, PROTECT_DELAY);
            long ret = _getPropertyLong(property, defaultValue);
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_GETPROPERTYLOOG);
            return ret;
        }

        @Override
        public void setPropertyLong(int property, long value) {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_SETPROPERTYLOOG, PROTECT_DELAY);
            _setPropertyLong(property, value);
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_SETPROPERTYLOOG);
        }

        @Override
        public void setVolume(float leftVolume, float rightVolume) {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_SETVOLUME, PROTECT_DELAY);
            _setVolume(leftVolume, rightVolume);
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_SETVOLUME);
        }

        @Override
        public int getAudioSessionId() {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_GETAUDIOSESSIONID, PROTECT_DELAY);
            int ret = _getAudioSessionId();
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_GETAUDIOSESSIONID);
            return ret;
        }

        @Override
        public String getVideoCodecInfo() {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_GETVIDEOCODECINFO, PROTECT_DELAY);
            String ret = _getVideoCodecInfo();
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_GETVIDEOCODECINFO);
            return ret;
        }

        @Override
        public String getAudioCodecInfo() {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_GETADUIOCODECINFO, PROTECT_DELAY);
            String ret = _getAudioCodecInfo();
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_GETADUIOCODECINFO);
            return ret;
        }

        @Override
        public void setOptionString(int category, String name, String value) {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_SETOPTIONSTRING, PROTECT_DELAY);
            _setOption(category, name, value);
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_SETOPTIONSTRING);
        }

        @Override
        public void setOptionLong(int category, String name, long value) {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_SETOPTIONLONG, PROTECT_DELAY);
            _setOption(category, name, value);
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_SETOPTIONLONG);
        }

        @Override
        public Bundle getMediaMeta() {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_GETMEDIAMETA, PROTECT_DELAY);
            Bundle ret = _getMediaMeta();
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_GETMEDIAMETA);
            return ret;
        }

        @Override
        public void nativeFinalize() {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_NATIVEFINALIZE, PROTECT_DELAY);
            _native_finalize();
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_NATIVEFINALIZE);
            System.exit(0);
        }

        @Override
        public String getColorFormatName(int mediaCodecColorFormat) {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_GETCOLORFORMATNAME, PROTECT_DELAY);
            String ret = _getColorFormatName(mediaCodecColorFormat);
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_GETCOLORFORMATNAME);
            return ret;
        }

        @Override
        public void nativeProfileBegin(String libName) {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_NATIVEPROFILEBEGIN, PROTECT_DELAY);
            _native_profileBegin(libName);
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_NATIVEPROFILEBEGIN);
        }

        @Override
        public void nativeProfileEnd() {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_NATIVEPROFILEEND, PROTECT_DELAY);
            _native_profileEnd();
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_NATIVEPROFILEEND);
        }

        @Override
        public void nativeSetLogLevel(int level) {
            mProtectHandle.sendEmptyMessageDelayed(MSG_NATIVE_PROTECT_NATIVESETLOGLEVEL, PROTECT_DELAY);
            _native_setLogLevel(level);
            mProtectHandle.removeMessages(MSG_NATIVE_PROTECT_NATIVESETLOGLEVEL);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mHandlerThread = new HandlerThread("blockHandle");
        mHandlerThread.start();
        mProtectHandle = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                int what = msg.what;
                switch (what) {
                    default:
                        Log.e(TAG, "ANR happened, IjkMediaPlayerService will reboot");
                        if (mClient != null) {
                            try {
                                mClient.onReportAnr(what);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                        System.exit(0);
                        break;
                }
            }
        };
    }

    @Override
    public IBinder onBind(Intent intent) {
        mLibBundle = intent.getExtras();
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mLibBundle = intent.getExtras();
        return START_NOT_STICKY;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mProtectHandle.removeCallbacksAndMessages(null);
        mHandlerThread.quit();
        mClient = null;
        if (mIsLibLoaded) {
            _release();
        }
    }
}
