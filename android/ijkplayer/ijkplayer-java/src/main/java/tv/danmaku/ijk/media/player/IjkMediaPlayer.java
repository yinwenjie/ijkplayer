/*
 * Copyright (C) 2006 Bilibili
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (C) 2013 Zhang Rui <bbcallen@gmail.com>
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

package tv.danmaku.ijk.media.player;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.graphics.Rect;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.tencent.bugly.CrashModule;
import com.tencent.bugly.crashreport.CrashReport;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

import tv.danmaku.ijk.media.player.misc.IMediaDataSource;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;
import tv.danmaku.ijk.media.player.misc.IjkTrackInfo;
import tv.danmaku.ijk.media.player.pragma.DebugLog;
import tv.danmaku.ijk.media.player.IIjkMediaPlayer;
import tv.danmaku.ijk.media.player.IIjkMediaPlayerClient;
import tv.danmaku.ijk.media.player.services.IjkMediaPlayerService;

/**
 * @author bbcallen
 *
 *         Java wrapper of ffplay.
 */
public final class IjkMediaPlayer extends AbstractMediaPlayer {
    private final static String TAG = IjkMediaPlayer.class.getName();

    private static final int MEDIA_NOP = 0; // interface test message
    private static final int MEDIA_PREPARED = 1;
    private static final int MEDIA_PLAYBACK_COMPLETE = 2;
    private static final int MEDIA_BUFFERING_UPDATE = 3;
    private static final int MEDIA_SEEK_COMPLETE = 4;
    private static final int MEDIA_SET_VIDEO_SIZE = 5;
    private static final int MEDIA_TIMED_TEXT = 99;
    private static final int MEDIA_ERROR = 100;
    private static final int MEDIA_INFO = 200;
    private static final int MEDIA_SET_VIDEO_SAR = 10001;

    //----------------------------------------
    // options
    public static final int IJK_LOG_UNKNOWN = 0;
    public static final int IJK_LOG_DEFAULT = 1;

    public static final int IJK_LOG_VERBOSE = 2;
    public static final int IJK_LOG_DEBUG = 3;
    public static final int IJK_LOG_INFO = 4;
    public static final int IJK_LOG_WARN = 5;
    public static final int IJK_LOG_ERROR = 6;
    public static final int IJK_LOG_FATAL = 7;
    public static final int IJK_LOG_SILENT = 8;

    public static final int OPT_CATEGORY_FORMAT     = 1;
    public static final int OPT_CATEGORY_CODEC      = 2;
    public static final int OPT_CATEGORY_SWS        = 3;
    public static final int OPT_CATEGORY_PLAYER     = 4;

    public static final int SDL_FCC_YV12 = 0x32315659; // YV12
    public static final int SDL_FCC_RV16 = 0x36315652; // RGB565
    public static final int SDL_FCC_RV32 = 0x32335652; // RGBX8888
    //----------------------------------------

    //----------------------------------------
    // properties
    public static final int PROP_FLOAT_VIDEO_DECODE_FRAMES_PER_SECOND       = 10001;
    public static final int PROP_FLOAT_VIDEO_OUTPUT_FRAMES_PER_SECOND       = 10002;
    public static final int FFP_PROP_FLOAT_PLAYBACK_RATE                    = 10003;

    public static final int FFP_PROP_INT64_SELECTED_VIDEO_STREAM            = 20001;
    public static final int FFP_PROP_INT64_SELECTED_AUDIO_STREAM            = 20002;
    public static final int FFP_PROP_INT64_SELECTED_TIMEDTEXT_STREAM        = 20011;

    public static final int FFP_PROP_INT64_VIDEO_DECODER                    = 20003;
    public static final int FFP_PROP_INT64_AUDIO_DECODER                    = 20004;
    public static final int     FFP_PROPV_DECODER_UNKNOWN                   = 0;
    public static final int     FFP_PROPV_DECODER_AVCODEC                   = 1;
    public static final int     FFP_PROPV_DECODER_MEDIACODEC                = 2;
    public static final int     FFP_PROPV_DECODER_VIDEOTOOLBOX              = 3;
    public static final int FFP_PROP_INT64_VIDEO_CACHED_DURATION            = 20005;
    public static final int FFP_PROP_INT64_AUDIO_CACHED_DURATION            = 20006;
    public static final int FFP_PROP_INT64_VIDEO_CACHED_BYTES               = 20007;
    public static final int FFP_PROP_INT64_AUDIO_CACHED_BYTES               = 20008;
    public static final int FFP_PROP_INT64_VIDEO_CACHED_PACKETS             = 20009;
    public static final int FFP_PROP_INT64_AUDIO_CACHED_PACKETS             = 20010;
    public static final int FFP_PROP_INT64_ASYNC_STATISTIC_BUF_BACKWARDS    = 20201;
    public static final int FFP_PROP_INT64_ASYNC_STATISTIC_BUF_FORWARDS     = 20202;
    public static final int FFP_PROP_INT64_ASYNC_STATISTIC_BUF_CAPACITY     = 20203;
    public static final int FFP_PROP_INT64_TRAFFIC_STATISTIC_BYTE_COUNT     = 20204;
    public static final int FFP_PROP_INT64_CACHE_STATISTIC_PHYSICAL_POS     = 20205;
    public static final int FFP_PROP_INT64_CACHE_STATISTIC_BUF_FORWARDS     = 20206;
    public static final int FFP_PROP_INT64_CACHE_STATISTIC_FILE_POS         = 20207;
    public static final int FFP_PROP_INT64_CACHE_STATISTIC_COUNT_BYTES      = 20208;
    public static final int FFP_PROP_INT64_BIT_RATE                         = 20100;
    public static final int FFP_PROP_INT64_TCP_SPEED                        = 20200;
    public static final int FFP_PROP_INT64_LATEST_SEEK_LOAD_DURATION        = 20300;
    //----------------------------------------

    public static final int PLAYER_ACTION_IS_INIT    = 10001;
    public static final int PLAYER_ACTION_IS_RELEASE = 10002;


    //----------------------------------------
    // some work case
    private static final int DO_PREPAREASYNC         = 1;
    private static final int DO_START                = 2;
    private static final int DO_RELEASE              = 3;
    private static final int DO_PAUSE                = 4;
    private static final int DO_RESET                = 5;
    private static final int DO_STOP                 = 6;
    private static final int DO_SETSURFACE           = 7;
    private static final int DO_SETDATASOURCE        = 8;
    private static final int DO_SETDATASOURCEBASE64  = 9;
    private static final int DO_SETDATASOURCEKEY     = 10;
    private static final int DO_SETDATASOURCEFD      = 11;
    private static final int DO_SETSTREAMSELECTED    = 12;
    private static final int DO_SEEKTO               = 13;
    private static final int DO_SETLOOPCOUNT         = 14;
    private static final int DO_SETPROPERTYFLOAT     = 15;
    private static final int DO_SETPROPERTYLONG      = 16;
    private static final int DO_SETVOLUME            = 17;
    private static final int DO_SETOPTIONSTRING      = 18;
    private static final int DO_SETOPTIONLONG        = 19;
    private static final int DO_NATIVEFINALIZE       = 20;
    private static final int DO_NATIVEPROFILEBEGIN   = 21;
    private static final int DO_NATIVEPROFILEEND     = 22;
    private static final int DO_NATIVESETLOGLEVEL    = 23;
    private static final int DO_MSG_SAVE             = 24;
    private static final int SERVICE_CONNECTED       = 25;
    private static final int SERVICE_DISCONNECTED    = 26;
    private static final int DO_SETANDROIDIOCALLBACK = 27;
    private static final int NOTIFY_ONNATIVEINVOKE   = 28;

    private SurfaceHolder mSurfaceHolder;
    private PowerManager.WakeLock mWakeLock = null;
    private boolean mScreenOnWhilePlaying;
    private boolean mStayAwake;

    private int mVideoWidth;
    private int mVideoHeight;
    private int mVideoSarNum;
    private int mVideoSarDen;

    private String mDataSource;
    private Context mContext;
    private IIjkMediaPlayer mIjkplayerService;
    private boolean mServiceIsConnected;
    private IjkMediaPlayerBinder mClient;
    private EventHandler mEventHandler;
    private IjkMediaPlayerServiceConnection mIjkMediaPlayerServiceConnection;
    private int mPlayerAction;
    private HandlerThread mHandleThread;
    private SomeWorkHandler mSomeWorkHandle;
    private ArrayList<Message> mWaitList;
    private boolean mHappenAnr = false;

    private static class SomeWorkHandler extends Handler {
        private final WeakReference<IjkMediaPlayer> mWeakPlayer;

        public SomeWorkHandler(IjkMediaPlayer mp, Looper looper) {
            super(looper);
            mWeakPlayer = new WeakReference<IjkMediaPlayer>(mp);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void handleMessage(Message msg) {
            IjkMediaPlayer player = mWeakPlayer.get();
            if (player == null) {
                DebugLog.w(TAG,
                        "IjkMediaPlayer went away with unhandled events");
                return;
            }
            switch (msg.what) {
                case DO_PREPAREASYNC:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            player.mIjkplayerService.prepareAsync();
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_START:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            player.mIjkplayerService.start();
                            player.stayAwake(true);
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_RELEASE:
                    // player.handleRelease();
                    break;
                case DO_PAUSE:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            player.mIjkplayerService.pause();
                            player.stayAwake(false);
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_RESET:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            player.mIjkplayerService.reset();
                            player.stayAwake(false);
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    player.mVideoWidth  = 0;
                    player.mVideoHeight = 0;
                    break;
                case DO_STOP:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            player.mIjkplayerService.stop();
                            player.stayAwake(false);
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_SETSURFACE:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            player.mIjkplayerService.setSurface((Surface) msg.obj);
                            player.updateSurfaceScreenOn();
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_SETDATASOURCE:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            player.mIjkplayerService.setDataSource((String) msg.obj);
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_SETDATASOURCEBASE64:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            player.mIjkplayerService.setDataSourceBase64((String) msg.obj);
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_SETDATASOURCEKEY:
                    break;
                case DO_SETDATASOURCEFD:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            player.mIjkplayerService.setDataSourceFd(msg.arg1);
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_SETSTREAMSELECTED:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            player.mIjkplayerService.setStreamSelected(msg.arg1, msg.arg2 > 0);
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_SEEKTO:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            player.mIjkplayerService.seekTo((Long) msg.obj);
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_SETLOOPCOUNT:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            player.mIjkplayerService.setLoopCount(msg.arg1);
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_SETPROPERTYFLOAT:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            player.mIjkplayerService.setPropertyFloat(msg.arg1, (Float) msg.obj);
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_SETPROPERTYLONG:
                    break;
                case DO_SETVOLUME:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            Pair<Float, Float> volume = (Pair<Float, Float>) msg.obj;
                            if (volume != null) {
                                player.mIjkplayerService.setVolume(volume.first, volume.second);
                            }
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_SETOPTIONSTRING:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            Pair<String, String> option = (Pair<String, String>) msg.obj;
                            if (option != null) {
                                player.mIjkplayerService.setOptionString(msg.arg1, option.first, option.second);
                            }
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_SETOPTIONLONG:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            Pair<String, Long> option = (Pair<String, Long>) msg.obj;
                            if (option != null) {
                                player.mIjkplayerService.setOptionLong(msg.arg1, option.first, option.second);
                            }
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_NATIVEFINALIZE:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            player.mIjkplayerService.nativeFinalize();
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_NATIVEPROFILEBEGIN:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            player.mIjkplayerService.nativeProfileBegin((String) msg.obj);
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_NATIVEPROFILEEND:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            player.mIjkplayerService.nativeProfileEnd();
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_NATIVESETLOGLEVEL:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            player.mIjkplayerService.nativeSetLogLevel(msg.arg1);
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_MSG_SAVE:
                    player.mWaitList.add((Message) msg.obj);
                    break;
                case SERVICE_CONNECTED:
                    int listSize = player.mWaitList.size();
                    if (listSize > 0 && player.mServiceIsConnected) {
                        for (int i = 0; i < listSize; i++) {
                            this.sendMessage(player.mWaitList.get(i));
                        }
                        player.mWaitList.clear();
                    }
                    break;
                case SERVICE_DISCONNECTED:
                    break;
                case DO_SETANDROIDIOCALLBACK:
                    try {
                        if (player.mIjkplayerService != null && player.mServiceIsConnected) {
                            player.mIjkplayerService.setAndroidIOCallback();
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case NOTIFY_ONNATIVEINVOKE:
                    player.mOnNativeInvokeListener.onNativeInvoke(msg.arg1, (Bundle)msg.obj);
                    break;
                default:
                    DebugLog.e(TAG, "SomeWorkHandler Unknown message type " + msg.what);
                    break;
            }
        }
    }

    private static class EventHandler extends Handler {
        private final WeakReference<IjkMediaPlayer> mWeakPlayer;

        public EventHandler(IjkMediaPlayer mp, Looper looper) {
            super(looper);
            mWeakPlayer = new WeakReference<IjkMediaPlayer>(mp);
        }

        @Override
        public void handleMessage(Message msg) {
            IjkMediaPlayer player = mWeakPlayer.get();
            if (player == null) {
                DebugLog.w(TAG,
                        "IjkMediaPlayer went away with unhandled events");
                return;
            }
            switch (msg.what) {
                case MEDIA_PREPARED:
                    player.notifyOnPrepared();
                    return;

                case MEDIA_PLAYBACK_COMPLETE:
                    player.stayAwake(false);
                    player.notifyOnCompletion();
                    return;

                case MEDIA_BUFFERING_UPDATE:
                    long bufferPosition = msg.arg1;
                    if (bufferPosition < 0) {
                        bufferPosition = 0;
                    }

                    long percent = 0;
                    long duration = player.getDuration();
                    if (duration > 0) {
                        percent = bufferPosition * 100 / duration;
                    }
                    if (percent >= 100) {
                        percent = 100;
                    }

                    // DebugLog.efmt(TAG, "Buffer (%d%%) %d/%d",  percent, bufferPosition, duration);
                    player.notifyOnBufferingUpdate((int)percent);
                    return;

                case MEDIA_SEEK_COMPLETE:
                    player.notifyOnSeekComplete();
                    return;

                case MEDIA_SET_VIDEO_SIZE:
                    player.mVideoWidth = msg.arg1;
                    player.mVideoHeight = msg.arg2;
                    player.notifyOnVideoSizeChanged(player.mVideoWidth, player.mVideoHeight,
                            player.mVideoSarNum, player.mVideoSarDen);
                    return;

                case MEDIA_ERROR:
                    DebugLog.e(TAG, "Error (" + msg.arg1 + "," + msg.arg2 + ")");
                    if (!player.notifyOnError(msg.arg1, msg.arg2)) {
                        player.notifyOnCompletion();
                    }
                    player.stayAwake(false);
                    return;

                case MEDIA_INFO:
                    switch (msg.arg1) {
                        case MEDIA_INFO_VIDEO_RENDERING_START:
                            DebugLog.i(TAG, "Info: MEDIA_INFO_VIDEO_RENDERING_START\n");
                            break;
                    }
                    player.notifyOnInfo(msg.arg1, msg.arg2);
                    // No real default action so far.
                    return;
                case MEDIA_TIMED_TEXT:
                    // do nothing
                    break;

                case MEDIA_NOP: // interface test message - ignore
                    break;

                case MEDIA_SET_VIDEO_SAR:
                    player.mVideoSarNum = msg.arg1;
                    player.mVideoSarDen = msg.arg2;
                    player.notifyOnVideoSizeChanged(player.mVideoWidth, player.mVideoHeight,
                            player.mVideoSarNum, player.mVideoSarDen);
                    break;

                default:
                    DebugLog.e(TAG, "EventHandler Unknown message type " + msg.what);
            }
        }
    }

    private class IjkMediaPlayerBinder extends IIjkMediaPlayerClient.Stub {
        private final WeakReference<IjkMediaPlayer> mWeakPlayer;

        public IjkMediaPlayerBinder(IjkMediaPlayer player) {
            mWeakPlayer = new WeakReference<IjkMediaPlayer>(player);
        }

        @Override
        public String onMediaCodecSelect(String mimeType, int profile, int level) throws RemoteException {
            if (mOnMediaCodecSelectListener == null)
                mOnMediaCodecSelectListener = IjkMediaPlayer.DefaultMediaCodecSelector.sInstance;

            IjkMediaPlayer player = mWeakPlayer.get();
            if (player == null) {
                return null;
            }
            return mOnMediaCodecSelectListener.onMediaCodecSelect(player, mimeType, profile, level);
        }

        @Override
        public boolean onNativeInvoke(int what, Bundle args) {
            if (mOnNativeInvokeListener != null) {
                switch (what) {
                    case OnNativeInvokeListener.EVENT_WILL_HTTP_OPEN:
                    case OnNativeInvokeListener.EVENT_WILL_HTTP_SEEK:
                    case OnNativeInvokeListener.EVENT_DID_HTTP_SEEK:
                    case OnNativeInvokeListener.EVENT_DID_HTTP_OPEN:
                        mSomeWorkHandle.obtainMessage(NOTIFY_ONNATIVEINVOKE, what, 0, args).sendToTarget();
                        return true;
                    default: {
                        if (mOnNativeInvokeListener.onNativeInvoke(what, args))
                            return true;
                    }
                }
            }

            IjkMediaPlayer player = mWeakPlayer.get();
            if (player == null) {
                return false;
            }
            switch (what) {
                case IjkMediaPlayer.OnNativeInvokeListener.CTRL_WILL_CONCAT_RESOLVE_SEGMENT: {
                    IjkMediaPlayer.OnControlMessageListener onControlMessageListener = player.mOnControlMessageListener;
                    if (onControlMessageListener == null)
                        return false;

                    int segmentIndex = args.getInt(IjkMediaPlayer.OnNativeInvokeListener.ARG_SEGMENT_INDEX, -1);
                    if (segmentIndex < 0)
                        throw new InvalidParameterException("onNativeInvoke(invalid segment index)");

                    String newUrl = onControlMessageListener.onControlResolveSegmentUrl(segmentIndex);
                    if (newUrl == null)
                        throw new RuntimeException(new IOException("onNativeInvoke() = <NULL newUrl>"));

                    args.putString(IjkMediaPlayer.OnNativeInvokeListener.ARG_URL, newUrl);
                    return true;
                }
                default:
                    return false;
            }
        }

        @Override
        public void onEventHandler(int what, int arg1, int arg2) throws RemoteException {
            IjkMediaPlayer player = mWeakPlayer.get();
            if (player == null) {
                return;
            }
            if (what == MEDIA_INFO && arg1 == MEDIA_INFO_STARTED_AS_NEXT) {
                // this acquires the wakelock if needed, and sets the client side
                // state
                player.start();
            }
            if (player.mEventHandler != null) {
                Message m = player.mEventHandler.obtainMessage(what, arg1, arg2);
                player.mEventHandler.sendMessage(m);
            }
        }

        @Override
        public void onReportAnr(int what) {
            DebugLog.w(TAG, "IjkMediaPlayerService happen anr in what =" + what);
            mHappenAnr = true;
        }

    }

    private class IjkMediaPlayerServiceConnection implements ServiceConnection {
        private final WeakReference<IjkMediaPlayer> mWeakPlayer;

        public IjkMediaPlayerServiceConnection(IjkMediaPlayer player) {
            super();
            mWeakPlayer = new WeakReference<IjkMediaPlayer>(player);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mIjkplayerService = IIjkMediaPlayer.Stub.asInterface(service);
            try {
                DebugLog.i(TAG, "IjkMediaPlayer onServiceConnected\n");
                mIjkplayerService.registerClient(mClient);
                mIjkplayerService.create();
                mServiceIsConnected = true;
                IjkMediaPlayer player = mWeakPlayer.get();
                if (player != null) {
                    player.mSomeWorkHandle.obtainMessage(SERVICE_CONNECTED).sendToTarget();
                }
                if (mOnServiceIsConnectedListener != null)
                    mOnServiceIsConnectedListener.onServiceIsConnected(mServiceIsConnected);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            DebugLog.w(TAG, "IjkMediaPlayer onServiceDisconnected\n");
            mServiceIsConnected = false;
            IjkMediaPlayer player = mWeakPlayer.get();
            if (mOnServiceIsConnectedListener != null)
                mOnServiceIsConnectedListener.onServiceIsConnected(mServiceIsConnected);
            mIjkplayerService = null;
            if (mPlayerAction != PLAYER_ACTION_IS_RELEASE)
                serviceDisConnectedHandle();
        }
    }

    private class ServiceException extends Exception {
        public ServiceException() {
        }

        public ServiceException(String detailMessage) {
            super(detailMessage);
        }

        public ServiceException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }

        public ServiceException(Throwable throwable) {
            super(throwable);
        }
    }

    /**
     * Default constructor. Consider using one of the create() methods for
     * synchronously instantiating a IjkMediaPlayer from a Uri or resource.
     * <p>
     * When done with the IjkMediaPlayer, you should call {@link #release()}, to
     * free the resources. If not released, too many IjkMediaPlayer instances
     * may result in an exception.
     * </p>
     */
    public IjkMediaPlayer(IjkLibLoader libLoader, Context context) {
        mServiceIsConnected = false;
        mPlayerAction = PLAYER_ACTION_IS_INIT;
        mContext = context;

        mClient = new IjkMediaPlayerBinder(this);
        mIjkMediaPlayerServiceConnection = new IjkMediaPlayerServiceConnection(this);

        Intent intent = new Intent(mContext, IjkMediaPlayerService.class);
        Bundle bundle = new Bundle();

        File file;
        file = libLoader.findLibrary("ijkffmpeg");
        if (file != null && file.exists()) {
            bundle.putString("ijkffmpeg", file.getAbsolutePath());
        }
        file = libLoader.findLibrary("ijksdl");
        if (file != null && file.exists()) {
            bundle.putString("ijksdl", file.getAbsolutePath());
        }
        file = libLoader.findLibrary("ijksoundtouch");
        if (file != null && file.exists()) {
            bundle.putString("ijksoundtouch", file.getAbsolutePath());
        }
        file = libLoader.findLibrary("ijkplayer");
        if (file != null && file.exists()) {
            bundle.putString("ijkplayer", file.getAbsolutePath());
        }
        intent.putExtras(bundle);
        mContext.bindService(intent, mIjkMediaPlayerServiceConnection, Context.BIND_AUTO_CREATE);

        Looper looper;
        if ((looper = Looper.myLooper()) != null) {
            mEventHandler = new IjkMediaPlayer.EventHandler(this, looper);
        } else if ((looper = Looper.getMainLooper()) != null) {
            mEventHandler = new IjkMediaPlayer.EventHandler(this, looper);
        } else {
            mEventHandler = null;
        }
        mWaitList = new ArrayList<>();
        mHandleThread = new HandlerThread("IjkMediaPlayer:Handler");
        mHandleThread.start();
        mSomeWorkHandle = new SomeWorkHandler(this, mHandleThread.getLooper());
        DebugLog.w(TAG, "IjkMediaPlayer create\n");
    }

    /**
     * Default constructor. Consider using one of the create() methods for
     * synchronously instantiating a IjkMediaPlayer from a Uri or resource.
     * <p>
     * When done with the IjkMediaPlayer, you should call {@link #release()}, to
     * free the resources. If not released, too many IjkMediaPlayer instances
     * may result in an exception.
     * </p>
     */
    public IjkMediaPlayer(Context context) {
        mServiceIsConnected = false;
        mPlayerAction = PLAYER_ACTION_IS_INIT;
        mContext = context;

        mClient = new IjkMediaPlayerBinder(this);
        mIjkMediaPlayerServiceConnection = new IjkMediaPlayerServiceConnection(this);

        Intent intent = new Intent(mContext, IjkMediaPlayerService.class);
        mContext.bindService(intent, mIjkMediaPlayerServiceConnection, Context.BIND_AUTO_CREATE);

        Looper looper;
        if ((looper = Looper.myLooper()) != null) {
            mEventHandler = new IjkMediaPlayer.EventHandler(this, looper);
        } else if ((looper = Looper.getMainLooper()) != null) {
            mEventHandler = new IjkMediaPlayer.EventHandler(this, looper);
        } else {
            mEventHandler = null;
        }
        mWaitList = new ArrayList<>();
        mHandleThread = new HandlerThread("IjkMediaPlayer:Handler");
        mHandleThread.start();
        mSomeWorkHandle = new SomeWorkHandler(this, mHandleThread.getLooper());
        DebugLog.w(TAG, "IjkMediaPlayer create\n");
    }

    private void onBuglyReport(Exception e) {
        if (CrashModule.hasInitialized()) {
            if (mHappenAnr) {
                CrashReport.postCatchedException(new ServiceException("Service ANR", e.getCause()));
            } else {
                CrashReport.postCatchedException(new ServiceException("Call Service Api Fail", e.getCause()));
            }

            mHappenAnr = false;
        }
    }

    private void serviceDisConnectedHandle() {
        if (!notifyOnError(MSG_ERROR_SERVICE_DISCONNECTED, 0)) {
            notifyOnCompletion();
        }
        stayAwake(false);
    }

    public boolean serviceIsConnected() {
        return mServiceIsConnected;
    }

    /**
     * Sets the {@link SurfaceHolder} to use for displaying the video portion of
     * the media.
     *
     * Either a surface holder or surface must be set if a display or video sink
     * is needed. Not calling this method or {@link #setSurface(Surface)} when
     * playing back a video will result in only the audio track being played. A
     * null surface holder or surface will result in only the audio track being
     * played.
     *
     * @param sh
     *            the SurfaceHolder to use for video display
     */
    @Override
    public void setDisplay(SurfaceHolder sh) {
        mSurfaceHolder = sh;
        Surface surface;
        if (sh != null) {
            surface = sh.getSurface();
        } else {
            surface = null;
        }

        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_SETSURFACE, surface).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_SETSURFACE, surface)).sendToTarget();
        }
    }

    /**
     * Sets the {@link Surface} to be used as the sink for the video portion of
     * the media. This is similar to {@link #setDisplay(SurfaceHolder)}, but
     * does not support {@link #setScreenOnWhilePlaying(boolean)}. Setting a
     * Surface will un-set any Surface or SurfaceHolder that was previously set.
     * A null surface will result in only the audio track being played.
     *
     * If the Surface sends frames to a {@link SurfaceTexture}, the timestamps
     * returned from {@link SurfaceTexture#getTimestamp()} will have an
     * unspecified zero point. These timestamps cannot be directly compared
     * between different media sources, different instances of the same media
     * source, or multiple runs of the same program. The timestamp is normally
     * monotonically increasing and is unaffected by time-of-day adjustments,
     * but it is reset when the position is set.
     *
     * @param surface
     *            The {@link Surface} to be used for the video portion of the
     *            media.
     */
    @Override
    public void setSurface(Surface surface) {
        if (mScreenOnWhilePlaying && surface != null) {
            DebugLog.w(TAG,
                    "setScreenOnWhilePlaying(true) is ineffective for Surface");
        }
        mSurfaceHolder = null;
        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_SETSURFACE, surface).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_SETSURFACE, surface)).sendToTarget();
        }
    }

    public void setAndroidIOCallback() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_SETANDROIDIOCALLBACK).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_SETANDROIDIOCALLBACK)).sendToTarget();
        }
    }
    /**
     * Sets the data source as a content Uri.
     *
     * @param context the Context to use when resolving the Uri
     * @param uri the Content URI of the data you want to play
     * @throws IllegalStateException if it is called in an invalid state
     */
    @Override
    public void setDataSource(Context context, Uri uri) throws IOException {
        setDataSource(context, uri, null);
    }

    /**
     * Sets the data source as a content Uri.
     *
     * @param context the Context to use when resolving the Uri
     * @param uri the Content URI of the data you want to play
     * @param headers the headers to be sent together with the request for the data
     *                Note that the cross domain redirection is allowed by default, but that can be
     *                changed with key/value pairs through the headers parameter with
     *                "android-allow-cross-domain-redirect" as the key and "0" or "1" as the value
     *                to disallow or allow cross domain redirection.
     * @throws IllegalStateException if it is called in an invalid state
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void setDataSource(Context context, Uri uri, Map<String, String> headers) throws IOException {
        final String scheme = uri.getScheme();
        if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            setDataSource(uri.getPath());
            return;
        } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)
                && Settings.AUTHORITY.equals(uri.getAuthority())) {
            // Redirect ringtones to go directly to underlying provider
            uri = RingtoneManager.getActualDefaultRingtoneUri(context,
                    RingtoneManager.getDefaultType(uri));
            if (uri == null) {
                throw new FileNotFoundException("Failed to resolve default ringtone");
            }
        }

        AssetFileDescriptor fd = null;
        try {
            ContentResolver resolver = context.getContentResolver();
            fd = resolver.openAssetFileDescriptor(uri, "r");
            if (fd == null) {
                return;
            }
            // Note: using getDeclaredLength so that our behavior is the same
            // as previous versions when the content provider is returning
            // a full file.
            if (fd.getDeclaredLength() < 0) {
                setDataSource(fd.getFileDescriptor());
            } else {
                setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getDeclaredLength());
            }
            return;
        } catch (SecurityException ignored) {
        } catch (IOException ignored) {
        } finally {
            if (fd != null) {
                fd.close();
            }
        }

        Log.d(TAG, "Couldn't open file on client side, trying server side");

        setDataSource(uri.toString(), headers);
    }

    /**
     * Sets the data source (file-path or http/rtsp URL) to use.
     *
     * @param path
     *            the path of the file, or the http/rtsp URL of the stream you
     *            want to play
     * @throws IllegalStateException
     *             if it is called in an invalid state
     *
     *             <p>
     *             When <code>path</code> refers to a local file, the file may
     *             actually be opened by a process other than the calling
     *             application. This implies that the pathname should be an
     *             absolute path (as any other process runs with unspecified
     *             current working directory), and that the pathname should
     *             reference a world-readable file.
     */
    @Override
    public void setDataSource(String path) {
        mDataSource = path;
        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_SETDATASOURCE, path).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_SETDATASOURCE, path)).sendToTarget();
        }
    }

    public void setDataSourceBase64(String path) {
        mDataSource = path;
        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_SETDATASOURCEBASE64, path).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_SETDATASOURCEBASE64, path)).sendToTarget();
        }
    }

    /**
     * Sets the data source (file-path or http/rtsp URL) to use.
     *
     * @param path the path of the file, or the http/rtsp URL of the stream you want to play
     * @param headers the headers associated with the http request for the stream you want to play
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void setDataSource(String path, Map<String, String> headers) throws IOException
    {
        if (headers != null && !headers.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for(Map.Entry<String, String> entry: headers.entrySet()) {
                sb.append(entry.getKey());
                sb.append(":");
                String value = entry.getValue();
                if (!TextUtils.isEmpty(value))
                    sb.append(entry.getValue());
                sb.append("\r\n");
                setOption(OPT_CATEGORY_FORMAT, "headers", sb.toString());
                setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "protocol_whitelist", "async,cache,crypto,file,http,https,ijkhttphook,ijkinject,ijklivehook,ijklongurl,ijksegment,ijktcphook,pipe,rtp,tcp,tls,udp,ijkurlhook,data");
            }
        }
        setDataSource(path);
    }

    /**
     * Sets the data source (FileDescriptor) to use. It is the caller's responsibility
     * to close the file descriptor. It is safe to do so as soon as this call returns.
     *
     * @param fd the FileDescriptor for the file you want to play
     * @throws IllegalStateException if it is called in an invalid state
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    @Override
    public void setDataSource(FileDescriptor fd) throws IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR1) {
            int native_fd = -1;
            try {
                Field f = fd.getClass().getDeclaredField("descriptor"); //NoSuchFieldException
                f.setAccessible(true);
                native_fd = f.getInt(fd); //IllegalAccessException
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }

            if (mIjkplayerService != null && mServiceIsConnected) {
                mSomeWorkHandle.obtainMessage(DO_SETDATASOURCEFD, native_fd, 0).sendToTarget();
            } else {
                mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_SETDATASOURCEFD, native_fd, 0)).sendToTarget();
            }
        } else {
            ParcelFileDescriptor pfd = ParcelFileDescriptor.dup(fd);
            if (mIjkplayerService != null && mServiceIsConnected) {
                mSomeWorkHandle.obtainMessage(DO_SETDATASOURCEFD, pfd.getFd(), 0).sendToTarget();
            } else {
                mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_SETDATASOURCEFD, pfd.getFd(), 0)).sendToTarget();
            }
        }
    }

    /**
     * Sets the data source (FileDescriptor) to use.  The FileDescriptor must be
     * seekable (N.B. a LocalSocket is not seekable). It is the caller's responsibility
     * to close the file descriptor. It is safe to do so as soon as this call returns.
     *
     * @param fd the FileDescriptor for the file you want to play
     * @param offset the offset into the file where the data to be played starts, in bytes
     * @param length the length in bytes of the data to be played
     * @throws IllegalStateException if it is called in an invalid state
     */
    private void setDataSource(FileDescriptor fd, long offset, long length)
            throws IOException {
        // FIXME: handle offset, length
        setDataSource(fd);
    }

//    public void setDataSource(IMediaDataSource mediaDataSource)
//            throws IllegalArgumentException, SecurityException, IllegalStateException {
//        _setDataSource(mediaDataSource);
//    }

    @Override
    public String getDataSource() {
        return mDataSource;
    }

    @Override
    public void prepareAsync() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_PREPAREASYNC).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_PREPAREASYNC)).sendToTarget();
        }
    }

    @Override
    public void start() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_START).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_START)).sendToTarget();
        }
    }

    @Override
    public void stop() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_STOP).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_STOP)).sendToTarget();
        }
    }

    @Override
    public void pause() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_PAUSE).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_PAUSE)).sendToTarget();
        }
    }

    @SuppressLint("Wakelock")
    @Override
    public void setWakeMode(Context context, int mode) {
        boolean washeld = false;
        if (mWakeLock != null) {
            if (mWakeLock.isHeld()) {
                washeld = true;
                mWakeLock.release();
            }
            mWakeLock = null;
        }

        PowerManager pm = (PowerManager) context
                .getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(mode | PowerManager.ON_AFTER_RELEASE,
                IjkMediaPlayer.class.getName());
        mWakeLock.setReferenceCounted(false);
        if (washeld) {
            mWakeLock.acquire();
        }
    }

    @Override
    public void setScreenOnWhilePlaying(boolean screenOn) {
        if (mScreenOnWhilePlaying != screenOn) {
            if (screenOn && mSurfaceHolder == null) {
                DebugLog.w(TAG,
                        "setScreenOnWhilePlaying(true) is ineffective without a SurfaceHolder");
            }
            mScreenOnWhilePlaying = screenOn;
            updateSurfaceScreenOn();
        }
    }

    @SuppressLint("Wakelock")
    private void stayAwake(boolean awake) {
        if (mWakeLock != null) {
            if (awake && !mWakeLock.isHeld()) {
                mWakeLock.acquire();
            } else if (!awake && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
        mStayAwake = awake;
        updateSurfaceScreenOn();
    }

    private void updateSurfaceScreenOn() {
        if (mSurfaceHolder != null) {
            mSurfaceHolder.setKeepScreenOn(mScreenOnWhilePlaying && mStayAwake);
        }
    }

    @Override
    public IjkTrackInfo[] getTrackInfo() {
        Bundle bundle = getMediaMeta();
        if (bundle == null)
            return null;

        IjkMediaMeta mediaMeta = IjkMediaMeta.parse(bundle);
        if (mediaMeta == null || mediaMeta.mStreams == null)
            return null;

        ArrayList<IjkTrackInfo> trackInfos = new ArrayList<IjkTrackInfo>();
        for (IjkMediaMeta.IjkStreamMeta streamMeta: mediaMeta.mStreams) {
            IjkTrackInfo trackInfo = new IjkTrackInfo(streamMeta);
            if (streamMeta.mType.equalsIgnoreCase(IjkMediaMeta.IJKM_VAL_TYPE__VIDEO)) {
                trackInfo.setTrackType(ITrackInfo.MEDIA_TRACK_TYPE_VIDEO);
            } else if (streamMeta.mType.equalsIgnoreCase(IjkMediaMeta.IJKM_VAL_TYPE__AUDIO)) {
                trackInfo.setTrackType(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO);
            } else if (streamMeta.mType.equalsIgnoreCase(IjkMediaMeta.IJKM_VAL_TYPE__TIMEDTEXT)) {
                trackInfo.setTrackType(ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT);
            }
            trackInfos.add(trackInfo);
        }

        return trackInfos.toArray(new IjkTrackInfo[trackInfos.size()]);
    }

    // TODO: @Override
    public int getSelectedTrack(int trackType) {
        switch (trackType) {
            case ITrackInfo.MEDIA_TRACK_TYPE_VIDEO:
                if (mIjkplayerService != null && mServiceIsConnected) {
                    try {
                        return (int) mIjkplayerService.getPropertyLong(FFP_PROP_INT64_SELECTED_VIDEO_STREAM, -1);
                    } catch (RemoteException e) {
                        onBuglyReport(e);
                        return -1;
                    }
                } else {
                    return -1;
                }
            case ITrackInfo.MEDIA_TRACK_TYPE_AUDIO:
                if (mIjkplayerService != null && mServiceIsConnected) {
                    try {
                        return (int) mIjkplayerService.getPropertyLong(FFP_PROP_INT64_SELECTED_AUDIO_STREAM, -1);
                    } catch (RemoteException e) {
                        onBuglyReport(e);
                    }
                    return -1;
                } else {
                    return -1;
                }
            case ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT:
                if (mIjkplayerService != null && mServiceIsConnected) {
                    try {
                        return (int) mIjkplayerService.getPropertyLong(FFP_PROP_INT64_SELECTED_TIMEDTEXT_STREAM, -1);
                    } catch (RemoteException e) {
                        onBuglyReport(e);
                    }
                    return -1;
                } else {
                    return -1;
                }
            default:
                return -1;
        }
    }

    // experimental, should set DEFAULT_MIN_FRAMES and MAX_MIN_FRAMES to 25
    // TODO: @Override
    public void selectTrack(int track) {
        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_SETSTREAMSELECTED, track, 1).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_SETSTREAMSELECTED, track, 1)).sendToTarget();
        }
    }

    // experimental, should set DEFAULT_MIN_FRAMES and MAX_MIN_FRAMES to 25
    // TODO: @Override
    public void deselectTrack(int track) {
        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_SETSTREAMSELECTED, track, 0).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_SETSTREAMSELECTED, track, 0)).sendToTarget();
        }
    }

    @Override
    public int getVideoWidth() {
        return mVideoWidth;
    }

    @Override
    public int getVideoHeight() {
        return mVideoHeight;
    }

    @Override
    public int getVideoSarNum() {
        return mVideoSarNum;
    }

    @Override
    public int getVideoSarDen() {
        return mVideoSarDen;
    }

    @Override
    public boolean isPlaying() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.isPlaying();
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return false;
    }

    @Override
    public void seekTo(long msec) {
        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_SEEKTO, msec).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_SEEKTO, msec)).sendToTarget();
        }
    }

    @Override
    public long getCurrentPosition() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getCurrentPosition();
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    @Override
    public long getDuration() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getDuration();
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    /**
     * Releases resources associated with this IjkMediaPlayer object. It is
     * considered good practice to call this method when you're done using the
     * IjkMediaPlayer. In particular, whenever an Activity of an application is
     * paused (its onPause() method is called), or stopped (its onStop() method
     * is called), this method should be invoked to release the IjkMediaPlayer
     * object, unless the application has a special need to keep the object
     * around. In addition to unnecessary resources (such as memory and
     * instances of codecs) being held, failure to call this method immediately
     * if a IjkMediaPlayer object is no longer needed may also lead to
     * continuous battery consumption for mobile devices, and playback failure
     * for other applications if no multiple instances of the same codec are
     * supported on a device. Even if multiple instances of the same codec are
     * supported, some performance degradation may be expected when unnecessary
     * multiple instances are used at the same time.
     */
    @Override
    public void release() {
//        if (mIjkplayerService != null && mServiceIsConnected) {
//            mSomeWorkHandle.obtainMessage(DO_RELEASE).sendToTarget();
//        } else {
//            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_RELEASE)).sendToTarget();
//        }
        handleRelease();
    }

    private void handleRelease() {
        stayAwake(false);
        updateSurfaceScreenOn();
        mPlayerAction = PLAYER_ACTION_IS_RELEASE;
        mServiceIsConnected = false;
        resetListeners();

        mSomeWorkHandle.removeCallbacksAndMessages(null);
        mHandleThread.quit();
        try {
            mHandleThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        mWaitList.clear();
        if (mIjkplayerService != null) {
            try {
                mIjkplayerService.release();
                mIjkplayerService.unregisterClient();
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        mContext.unbindService(mIjkMediaPlayerServiceConnection);
        mIjkplayerService = null;
        mIjkMediaPlayerServiceConnection = null;
        mClient = null;
    }

    @Override
    public void reset() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_RESET).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_RESET)).sendToTarget();
        }
    }

    /**
     * Sets the player to be looping or non-looping.
     *
     * @param looping whether to loop or not
     */
    @Override
    public void setLooping(boolean looping) {
        int loopCount = looping ? 0 : 1;
        setOption(OPT_CATEGORY_PLAYER, "loop", loopCount);

        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_SETLOOPCOUNT, loopCount, 0).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_SETLOOPCOUNT, loopCount, 0)).sendToTarget();
        }
    }

    /**
     * Checks whether the MediaPlayer is looping or non-looping.
     *
     * @return true if the MediaPlayer is currently looping, false otherwise
     */
    @Override
    public boolean isLooping() {
        int loopCount = 0;
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                loopCount = mIjkplayerService.getLoopCount();
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return loopCount != 1;
    }

    public void setSpeed(float speed) {
        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_SETPROPERTYFLOAT, FFP_PROP_FLOAT_PLAYBACK_RATE, 0, speed).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_SETPROPERTYFLOAT, FFP_PROP_FLOAT_PLAYBACK_RATE, 0, speed)).sendToTarget();
        }
    }

    public float getSpeed(float speed) {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getPropertyFloat(FFP_PROP_FLOAT_PLAYBACK_RATE, .0f);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    public int getVideoDecoder() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return (int) mIjkplayerService.getPropertyLong(FFP_PROP_INT64_VIDEO_DECODER, FFP_PROPV_DECODER_UNKNOWN);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return FFP_PROPV_DECODER_UNKNOWN;
    }

    public float getVideoOutputFramesPerSecond() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getPropertyFloat(PROP_FLOAT_VIDEO_OUTPUT_FRAMES_PER_SECOND, 0.0f);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    public float getVideoDecodeFramesPerSecond() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getPropertyFloat(PROP_FLOAT_VIDEO_DECODE_FRAMES_PER_SECOND, 0.0f);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    public long getVideoCachedDuration() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getPropertyLong(FFP_PROP_INT64_VIDEO_CACHED_DURATION, 0);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    public long getAudioCachedDuration() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getPropertyLong(FFP_PROP_INT64_AUDIO_CACHED_DURATION, 0);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    public long getVideoCachedBytes() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getPropertyLong(FFP_PROP_INT64_VIDEO_CACHED_BYTES, 0);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    public long getAudioCachedBytes() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getPropertyLong(FFP_PROP_INT64_AUDIO_CACHED_BYTES, 0);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    public long getVideoCachedPackets() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getPropertyLong(FFP_PROP_INT64_VIDEO_CACHED_PACKETS, 0);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    public long getAudioCachedPackets() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getPropertyLong(FFP_PROP_INT64_AUDIO_CACHED_PACKETS, 0);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    public long getAsyncStatisticBufBackwards() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getPropertyLong(FFP_PROP_INT64_ASYNC_STATISTIC_BUF_BACKWARDS, 0);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    public long getAsyncStatisticBufForwards() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getPropertyLong(FFP_PROP_INT64_ASYNC_STATISTIC_BUF_FORWARDS, 0);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    public long getAsyncStatisticBufCapacity() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getPropertyLong(FFP_PROP_INT64_ASYNC_STATISTIC_BUF_CAPACITY, 0);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    public long getAndroidIOTrafficStatistic() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getAndroidIOTrafficStatistic();
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    public long getTrafficStatisticByteCount() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getPropertyLong(FFP_PROP_INT64_TRAFFIC_STATISTIC_BYTE_COUNT, 0);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    public long getCacheStatisticPhysicalPos() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getPropertyLong(FFP_PROP_INT64_CACHE_STATISTIC_PHYSICAL_POS, 0);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    public long getCacheStatisticBufForwards() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getPropertyLong(FFP_PROP_INT64_CACHE_STATISTIC_BUF_FORWARDS, 0);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    public long getCacheStatisticFilePos() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getPropertyLong(FFP_PROP_INT64_CACHE_STATISTIC_FILE_POS, 0);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    public long getCacheStatisticCountBytes() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getPropertyLong(FFP_PROP_INT64_CACHE_STATISTIC_COUNT_BYTES, 0);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    public long getBitRate() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getPropertyLong(FFP_PROP_INT64_BIT_RATE, 0);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    public long getTcpSpeed() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getPropertyLong(FFP_PROP_INT64_TCP_SPEED, 0);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    public long getSeekLoadDuration() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getPropertyLong(FFP_PROP_INT64_LATEST_SEEK_LOAD_DURATION, 0);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_SETVOLUME, new Pair<>(leftVolume, rightVolume)).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_SETVOLUME, new Pair<>(leftVolume, rightVolume))).sendToTarget();
        }
    }

    @Override
    public int getAudioSessionId() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getAudioSessionId();
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return 0;
    }

    @Override
    public MediaInfo getMediaInfo() {
        MediaInfo mediaInfo = new MediaInfo();
        mediaInfo.mMediaPlayerName = "ijkplayer";

        String videoCodecInfo = null;
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                videoCodecInfo = mIjkplayerService.getVideoCodecInfo();
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        if (!TextUtils.isEmpty(videoCodecInfo)) {
            String nodes[] = videoCodecInfo.split(",");
            if (nodes.length >= 2) {
                mediaInfo.mVideoDecoder = nodes[0];
                mediaInfo.mVideoDecoderImpl = nodes[1];
            } else if (nodes.length >= 1) {
                mediaInfo.mVideoDecoder = nodes[0];
                mediaInfo.mVideoDecoderImpl = "";
            }
        }

        String audioCodecInfo = null;
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                audioCodecInfo = mIjkplayerService.getAudioCodecInfo();
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        if (!TextUtils.isEmpty(audioCodecInfo)) {
            String nodes[] = audioCodecInfo.split(",");
            if (nodes.length >= 2) {
                mediaInfo.mAudioDecoder = nodes[0];
                mediaInfo.mAudioDecoderImpl = nodes[1];
            } else if (nodes.length >= 1) {
                mediaInfo.mAudioDecoder = nodes[0];
                mediaInfo.mAudioDecoderImpl = "";
            }
        }
        Bundle bundle = null;
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                bundle = mIjkplayerService.getMediaMeta();
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        try {
            mediaInfo.mMeta = IjkMediaMeta.parse(bundle);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return mediaInfo;
    }

    @Override
    public void setLogEnabled(boolean enable) {
        // do nothing
    }

    @Override
    public boolean isPlayable() {
        return true;
    }

    public void setOption(int category, String name, String value)
    {
        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_SETOPTIONSTRING, category, 0, new Pair<>(name, value)).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_SETOPTIONSTRING, category, 0, new Pair<>(name, value))).sendToTarget();
        }
    }

    public void setOption(int category, String name, long value)
    {
        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_SETOPTIONLONG, category, 0, new Pair<>(name, value)).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_SETOPTIONLONG, category, 0, new Pair<>(name, value))).sendToTarget();
        }
    }

    public Bundle getMediaMeta() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getMediaMeta();
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return null;
    }

    public String getColorFormatName(int mediaCodecColorFormat) {
        if (mIjkplayerService != null && mServiceIsConnected) {
            try {
                return mIjkplayerService.getColorFormatName(mediaCodecColorFormat);
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }
        return null;
    }

    @Override
    public void setAudioStreamType(int streamtype) {
        // do nothing
    }

    @Override
    public void setKeepInBackground(boolean keepInBackground) {
        // do nothing
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        stayAwake(false);
        updateSurfaceScreenOn();
        mPlayerAction = PLAYER_ACTION_IS_RELEASE;
        resetListeners();
    }

    /*
     * ControlMessage
     */

    private OnControlMessageListener mOnControlMessageListener;
    public void setOnControlMessageListener(OnControlMessageListener listener) {
        mOnControlMessageListener = listener;
    }

    public interface OnControlMessageListener {
        String onControlResolveSegmentUrl(int segment);
    }

    /*
     * NativeInvoke
     */

    private OnNativeInvokeListener mOnNativeInvokeListener;
    public void setOnNativeInvokeListener(OnNativeInvokeListener listener) {
        mOnNativeInvokeListener = listener;
    }

    public interface OnNativeInvokeListener {

        int CTRL_WILL_TCP_OPEN = 0x20001;               // NO ARGS
        int CTRL_DID_TCP_OPEN = 0x20002;                // ARG_ERROR, ARG_FAMILIY, ARG_IP, ARG_PORT, ARG_FD

        int CTRL_WILL_HTTP_OPEN = 0x20003;              // ARG_URL, ARG_SEGMENT_INDEX, ARG_RETRY_COUNTER
        int CTRL_WILL_LIVE_OPEN = 0x20005;              // ARG_URL, ARG_RETRY_COUNTER
        int CTRL_WILL_CONCAT_RESOLVE_SEGMENT = 0x20007; // ARG_URL, ARG_SEGMENT_INDEX, ARG_RETRY_COUNTER

        int EVENT_WILL_HTTP_OPEN = 0x1;                 // ARG_URL
        int EVENT_DID_HTTP_OPEN = 0x2;                  // ARG_URL, ARG_ERROR, ARG_HTTP_CODE
        int EVENT_WILL_HTTP_SEEK = 0x3;                 // ARG_URL, ARG_OFFSET
        int EVENT_DID_HTTP_SEEK = 0x4;                  // ARG_URL, ARG_OFFSET, ARG_ERROR, ARG_HTTP_CODE

        String ARG_URL = "url";
        String ARG_SEGMENT_INDEX = "segment_index";
        String ARG_RETRY_COUNTER = "retry_counter";

        String ARG_ERROR = "error";
        String ARG_FAMILIY = "family";
        String ARG_IP = "ip";
        String ARG_PORT = "port";
        String ARG_FD = "fd";

        String ARG_OFFSET = "offset";
        String ARG_HTTP_CODE = "http_code";

        /*
         * @return true if invoke is handled
         * @throws Exception on any error
         */
        boolean onNativeInvoke(int what, Bundle args);
    }



    private OnServiceIsConnectedListener mOnServiceIsConnectedListener;
    public void setOnServiceIsConnectedListener(OnServiceIsConnectedListener listener) {
        mOnServiceIsConnectedListener = listener;
    }

    public interface OnServiceIsConnectedListener {
        void onServiceIsConnected(boolean isConnected);
    }

    /*
     * MediaCodec select
     */

    public interface OnMediaCodecSelectListener {
        String onMediaCodecSelect(IMediaPlayer mp, String mimeType, int profile, int level);
    }
    private OnMediaCodecSelectListener mOnMediaCodecSelectListener;
    public void setOnMediaCodecSelectListener(OnMediaCodecSelectListener listener) {
        mOnMediaCodecSelectListener = listener;
    }

    public void resetListeners() {
        super.resetListeners();
        mOnMediaCodecSelectListener = null;
    }

    public static class DefaultMediaCodecSelector implements OnMediaCodecSelectListener {
        public static final DefaultMediaCodecSelector sInstance = new DefaultMediaCodecSelector();

        @SuppressWarnings("deprecation")
        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        public String onMediaCodecSelect(IMediaPlayer mp, String mimeType, int profile, int level) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN)
                return null;

            if (TextUtils.isEmpty(mimeType))
                return null;

            Log.i(TAG, String.format(Locale.US, "onSelectCodec: mime=%s, profile=%d, level=%d", mimeType, profile, level));
            ArrayList<IjkMediaCodecInfo> candidateCodecList = new ArrayList<IjkMediaCodecInfo>();
            int numCodecs = MediaCodecList.getCodecCount();
            for (int i = 0; i < numCodecs; i++) {
                MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
                Log.d(TAG, String.format(Locale.US, "  found codec: %s", codecInfo.getName()));
                if (codecInfo.isEncoder())
                    continue;

                String[] types = codecInfo.getSupportedTypes();
                if (types == null)
                    continue;

                for(String type: types) {
                    if (TextUtils.isEmpty(type))
                        continue;

                    Log.d(TAG, String.format(Locale.US, "    mime: %s", type));
                    if (!type.equalsIgnoreCase(mimeType))
                        continue;

                    IjkMediaCodecInfo candidate = IjkMediaCodecInfo.setupCandidate(codecInfo, mimeType);
                    if (candidate == null)
                        continue;

                    candidateCodecList.add(candidate);
                    Log.i(TAG, String.format(Locale.US, "candidate codec: %s rank=%d", codecInfo.getName(), candidate.mRank));
                    candidate.dumpProfileLevels(mimeType);
                }
            }

            if (candidateCodecList.isEmpty()) {
                return null;
            }

            IjkMediaCodecInfo bestCodec = candidateCodecList.get(0);

            for (IjkMediaCodecInfo codec : candidateCodecList) {
                if (codec.mRank > bestCodec.mRank) {
                    bestCodec = codec;
                }
            }

            if (bestCodec.mRank < IjkMediaCodecInfo.RANK_LAST_CHANCE) {
                Log.w(TAG, String.format(Locale.US, "unaccetable codec: %s", bestCodec.mCodecInfo.getName()));
                return null;
            }

            Log.i(TAG, String.format(Locale.US, "selected codec: %s rank=%d", bestCodec.mCodecInfo.getName(), bestCodec.mRank));
            return bestCodec.mCodecInfo.getName();
        }
    }

    public void nativeProfileBegin(String libName) {
        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_NATIVEPROFILEBEGIN, libName).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_NATIVEPROFILEBEGIN, libName)).sendToTarget();
        }
    }

    public void nativeProfileEnd() {
        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_NATIVEPROFILEEND).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_NATIVEPROFILEEND)).sendToTarget();
        }
    }

    public void nativeSetLogLevel(int level) {
        if (mIjkplayerService != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_NATIVESETLOGLEVEL, level, 0).sendToTarget();
        } else {
            mSomeWorkHandle.obtainMessage(DO_MSG_SAVE, mSomeWorkHandle.obtainMessage(DO_NATIVESETLOGLEVEL, level, 0)).sendToTarget();
        }
    }
}
