/*
 * Copyright (C) 2006 Bilibili
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (C) 2017 Raymond Zheng <raymondzheng1412@gmail.com>
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

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetFileDescriptor;
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
import android.os.RemoteException;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.tencent.bugly.CrashModule;
import com.tencent.bugly.crashreport.CrashReport;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

import tv.danmaku.android.log.BLog;
import tv.danmaku.ijk.media.player.pragma.DebugLog;
import tv.danmaku.ijk.media.player.services.IjkMediaPlayerService;

public final class IjkMediaMetadataRetriever {
    private final static String TAG = IjkMediaMetadataRetriever.class.getName();

    private static final int MEDIA_PREPARED = 1;
    private static final int MEDIA_GET_IMG_STATE = 6;
    private static final int MEDIA_ERROR = 100;


    public static final int OPT_CATEGORY_FORMAT     = 1;
    public static final int OPT_CATEGORY_CODEC      = 2;
    public static final int OPT_CATEGORY_SWS        = 3;
    public static final int OPT_CATEGORY_PLAYER     = 4;
    //----------------------------------------

    public static final int PLAYER_ACTION_IS_INIT    = 10001;
    public static final int PLAYER_ACTION_IS_RELEASE = 10002;


    //----------------------------------------
    // some work case
    private static final int DO_CREATE               = 0;
    private static final int DO_PREPAREASYNC         = 1;
    private static final int DO_START                = 2;
    private static final int DO_RELEASE              = 3;
    private static final int DO_SETDATASOURCE        = 4;
    private static final int DO_SETDATASOURCEBASE64  = 5;
    private static final int DO_SETDATASOURCEFD      = 6;
    private static final int DO_SEEKTO               = 7;
    private static final int DO_SETOPTIONSTRING      = 8;
    private static final int DO_SETOPTIONLONG        = 9;
    private static final int SERVICE_CONNECTED       = 10;
    private static final int SERVICE_DISCONNECTED    = 11;
    private static final int NOTIFY_ONNATIVEINVOKE   = 12;
    private static final int DO_SETFRAMEATTIME       = 14;
    public static final int HD_IMAGE = 2;
    public static final int SD_IMAGE = 1;
    public static final int LD_IMAGE = 0;

    private String mDataSource;
    private Context mContext;
    private IIjkMediaPlayer mPlayer;
    private IIjkMediaPlayerService mService;
    private boolean mServiceIsConnected;
    private IjkMediaPlayerBinder mClient;
    private IjkMediaPlayerServiceConnection mIjkMediaPlayerServiceConnection;
    private int mPlayerAction;
    private HandlerThread mHandleThread;
    private SomeWorkHandler mSomeWorkHandle;
    private final ArrayList<Message> mWaitList = new ArrayList<>();
    private boolean mHappenAnr = false;

    private long      mStartTime = 0;
    private long        mEndTime = 0;
    private int             mNum = 0;
    private int   mImgDefinition = 0;
    private String mImgCachePath = null;

    private static class SomeWorkHandler extends Handler {
        private final WeakReference<IjkMediaMetadataRetriever> mWeakPlayer;

        public SomeWorkHandler(IjkMediaMetadataRetriever mp, Looper looper) {
            super(looper);
            mWeakPlayer = new WeakReference<IjkMediaMetadataRetriever>(mp);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void handleMessage(Message msg) {
            IjkMediaMetadataRetriever player = mWeakPlayer.get();
            if (player == null) {
                DebugLog.w(TAG,
                        "IjkMediaMetadataRetriever went away with unhandled events");
                return;
            }
            switch (msg.what) {
                case DO_CREATE:
                    try {
                        if (player.mService != null && player.mClient != null) {
                            player.mPlayer = player.mService.create(player.mClient.hashCode(), player.mClient);
                            player.mPlayer.setOptionLong(OPT_CATEGORY_FORMAT, "timeout", 2000000);
                            player.mPlayer.setOptionLong(OPT_CATEGORY_FORMAT, "connect_timeout", 15000000);
                            player.mPlayer.setOptionLong(OPT_CATEGORY_FORMAT, "reconnect", 1);
                            player.mPlayer.setOptionLong(OPT_CATEGORY_FORMAT, "dns_cache_timeout", 2 * 60 * 60 * 1000);
                            player.mPlayer.setOptionLong(OPT_CATEGORY_FORMAT, "safe", 0);
                            player.mPlayer.setOptionLong(OPT_CATEGORY_PLAYER, "skip-calc-frame-rate", 1);
                            player.mPlayer.setOptionLong(OPT_CATEGORY_PLAYER, "min-frames", 480);
                            player.mPlayer.setOptionLong(OPT_CATEGORY_PLAYER, "start-on-prepared", 1);
                            player.mPlayer.setOptionLong(OPT_CATEGORY_PLAYER, "an", 1);
                            player.mPlayer.setOptionLong(OPT_CATEGORY_PLAYER, "mediacodec", 0);
                            player.mPlayer.setOptionLong(OPT_CATEGORY_PLAYER, "get-frame-mode", 1);
                            player.mPlayer.setOptionString(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "protocol_whitelist", "ijkio,async,cache,crypto,file,http,https,ijkhttphook,ijkinject,ijklivehook,ijklongurl,ijksegment,ijktcphook,pipe,rtp,tcp,tls,udp,ijkurlhook,data");
                        } else {
                            this.removeCallbacksAndMessages(null);
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                        this.removeCallbacksAndMessages(null);
                    }
                    break;
                case DO_PREPAREASYNC:
                    try {
                        if (player.mPlayer != null && player.mServiceIsConnected) {
                            player.mPlayer.prepareAsync();
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_START:
                    try {
                        if (player.mPlayer != null && player.mServiceIsConnected) {
                            player.mPlayer.start();
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_RELEASE:
                    player.handleRelease();
                    break;
                case DO_SETDATASOURCE:
                    try {
                        if (player.mPlayer != null && player.mServiceIsConnected) {
                            player.mPlayer.setDataSource((String) msg.obj);
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_SETDATASOURCEBASE64:
                    try {
                        if (player.mPlayer != null && player.mServiceIsConnected) {
                            player.mPlayer.setDataSourceBase64((String) msg.obj);
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_SETDATASOURCEFD:
                    try {
                        if (player.mPlayer != null && player.mServiceIsConnected) {
                            player.mPlayer.setDataSourceFd((ParcelFileDescriptor) msg.obj);
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_SEEKTO:
                    try {
                        if (player.mPlayer != null && player.mServiceIsConnected) {
                            player.mPlayer.seekTo((Long) msg.obj);
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_SETOPTIONSTRING:
                    try {
                        if (player.mPlayer != null && player.mServiceIsConnected) {
                            Pair<String, String> option = (Pair<String, String>) msg.obj;
                            if (option != null) {
                                player.mPlayer.setOptionString(msg.arg1, option.first, option.second);
                            }
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_SETOPTIONLONG:
                    try {
                        if (player.mPlayer != null && player.mServiceIsConnected) {
                            Pair<String, Long> option = (Pair<String, Long>) msg.obj;
                            if (option != null) {
                                player.mPlayer.setOptionLong(msg.arg1, option.first, option.second);
                            }
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case DO_SETFRAMEATTIME:
                    try {
                        if (player.mPlayer != null && player.mServiceIsConnected) {
                            player.mPlayer.setFrameAtTime(player.mImgCachePath, player.mStartTime, player.mEndTime, player.mNum, player.mImgDefinition);
                        }
                    } catch (RemoteException e) {
                        player.onBuglyReport(e);
                    }
                    break;
                case SERVICE_CONNECTED:
                    synchronized (player.mWaitList) {
                        int listSize = player.mWaitList.size();
                        if (listSize > 0) {
                            for (int i = 0; i < listSize; i++) {
                                this.sendMessage(player.mWaitList.get(i));
                            }
                            player.mWaitList.clear();
                        }
                        player.mServiceIsConnected = true;
                        if (player.mOnServiceIsConnectedListener != null)
                            player.mOnServiceIsConnectedListener.onServiceIsConnected(true);
                    }
                    break;
                case SERVICE_DISCONNECTED:
                    break;
                case NOTIFY_ONNATIVEINVOKE:
                    if (player.mOnNativeInvokeListener != null) {
                        player.mOnNativeInvokeListener.onNativeInvoke(msg.arg1, (Bundle)msg.obj);
                    }
                    break;
                default:
                    DebugLog.e(TAG, "SomeWorkHandler Unknown message type " + msg.what);
                    break;
            }
        }
    }

    private class IjkMediaPlayerBinder extends IIjkMediaPlayerClient.Stub {
        private final WeakReference<IjkMediaMetadataRetriever> mWeakPlayer;

        public IjkMediaPlayerBinder(IjkMediaMetadataRetriever player) {
            mWeakPlayer = new WeakReference<IjkMediaMetadataRetriever>(player);
        }

        @Override
        public String onMediaCodecSelect(String mimeType, int profile, int level) throws RemoteException {
            if (mOnMediaCodecSelectListener == null)
                mOnMediaCodecSelectListener = IjkMediaMetadataRetriever.DefaultMediaCodecSelector.sInstance;

            IjkMediaMetadataRetriever player = mWeakPlayer.get();
            if (player == null) {
                return null;
            }
            return mOnMediaCodecSelectListener.onMediaCodecSelect(player, mimeType, profile, level);
        }

        @Override
        public boolean onNativeInvoke(int what, Bundle args) {
            switch (what) {
                case OnNativeInvokeListener.EVENT_WILL_HTTP_OPEN:
                case OnNativeInvokeListener.EVENT_WILL_HTTP_SEEK:
                case OnNativeInvokeListener.EVENT_DID_HTTP_SEEK:
                case OnNativeInvokeListener.EVENT_DID_HTTP_OPEN:
                    mSomeWorkHandle.obtainMessage(NOTIFY_ONNATIVEINVOKE, what, 0, args).sendToTarget();
                    break;
                default: {
                    if (mOnNativeInvokeListener != null) {
                        mOnNativeInvokeListener.onNativeInvoke(what, args);
                    }
                    break;
                }
            }
            return true;
        }

        @Override
        public void onEventHandler(int what, int arg1, int arg2, String str) throws RemoteException {
            IjkMediaMetadataRetriever player = mWeakPlayer.get();
            if (player == null) {
                return;
            }
            switch (what) {
                case MEDIA_PREPARED:
                    return;
                case MEDIA_ERROR:
                    if (mOnFrameGenerateListener != null)
                        mOnFrameGenerateListener.onFrameGenerate(0, -1, null);
                    return;
                case MEDIA_GET_IMG_STATE:
                    if (mOnFrameGenerateListener != null)
                        mOnFrameGenerateListener.onFrameGenerate(arg1, arg2, str);
                    return;
                default:
                    break;
            }
        }

        @Override
        public void onReportAnr(int what) {
            DebugLog.w(TAG, "IjkMediaMetadataRetriever happen anr in what =" + what);
            mHappenAnr = true;
        }
    }

    private class IjkMediaPlayerServiceConnection implements ServiceConnection {
        private final WeakReference<IjkMediaMetadataRetriever> mWeakPlayer;

        public IjkMediaPlayerServiceConnection(IjkMediaMetadataRetriever player) {
            super();
            mWeakPlayer = new WeakReference<IjkMediaMetadataRetriever>(player);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BLog.i(TAG, "IjkMediaMetadataRetriever onServiceConnected\n");
            if (mServiceIsConnected || mPlayerAction == PLAYER_ACTION_IS_RELEASE) {
                return;
            }

            mService = IIjkMediaPlayerService.Stub.asInterface(service);
            IjkMediaMetadataRetriever player = mWeakPlayer.get();
            mSomeWorkHandle.obtainMessage(DO_CREATE).sendToTarget();
            if (player != null) {
                player.mSomeWorkHandle.obtainMessage(SERVICE_CONNECTED).sendToTarget();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            BLog.i(TAG, "IjkMediaMetadataRetriever onServiceDisconnected\n");
            mServiceIsConnected = false;
            if (mOnServiceIsConnectedListener != null)
                mOnServiceIsConnectedListener.onServiceIsConnected(false);
            if (mPlayerAction != PLAYER_ACTION_IS_RELEASE) {
                serviceDisConnectedHandle();
            }
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
    public IjkMediaMetadataRetriever(IjkLibLoader libLoader, Context context) {
        BLog.i(TAG, "IjkMediaPlayer create\n");
        mServiceIsConnected = false;
        mPlayerAction = PLAYER_ACTION_IS_INIT;
        mContext = context;

        mClient = new IjkMediaPlayerBinder(this);
        mIjkMediaPlayerServiceConnection = new IjkMediaPlayerServiceConnection(this);

        mHandleThread = new HandlerThread("IjkMediaMetadataRetriever:Handler");
        mHandleThread.start();
        mSomeWorkHandle = new SomeWorkHandler(this, mHandleThread.getLooper());

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
        file = libLoader.findLibrary("ijkplayer");
        if (file != null && file.exists()) {
            bundle.putString("ijkplayer", file.getAbsolutePath());
        }
        intent.putExtras(bundle);
        mContext.bindService(intent, mIjkMediaPlayerServiceConnection, Context.BIND_AUTO_CREATE);
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
    public IjkMediaMetadataRetriever(Context context) {
        BLog.i(TAG, "IjkMediaPlayer create\n");
        mServiceIsConnected = false;
        mPlayerAction = PLAYER_ACTION_IS_INIT;
        mContext = context;

        mClient = new IjkMediaPlayerBinder(this);
        mIjkMediaPlayerServiceConnection = new IjkMediaPlayerServiceConnection(this);

        mHandleThread = new HandlerThread("IjkMediaPlayer:Handler");
        mHandleThread.start();

        mSomeWorkHandle = new SomeWorkHandler(this, mHandleThread.getLooper());
        Intent intent = new Intent(mContext, IjkMediaPlayerService.class);
        mContext.bindService(intent, mIjkMediaPlayerServiceConnection, Context.BIND_AUTO_CREATE);
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
    }

    public boolean serviceIsConnected() {
        return mServiceIsConnected;
    }

    /**
     * Sets the data source as a content Uri.
     *
     * @param context the Context to use when resolving the Uri
     * @param uri the Content URI of the data you want to play
     * @throws IllegalStateException if it is called in an invalid state
     */
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
    public void setDataSource(String path) {
        mDataSource = path;
        if (mPlayer != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_SETDATASOURCE, path).sendToTarget();
        } else {
            synchronized (mWaitList) {
                if (mPlayer != null && mServiceIsConnected) {
                    mSomeWorkHandle.obtainMessage(DO_SETDATASOURCE, path).sendToTarget();
                } else {
                    mWaitList.add(mSomeWorkHandle.obtainMessage(DO_SETDATASOURCE, path));
                }
            }
        }
    }

    public void setDataSourceBase64(String path) {
        mDataSource = path;
        if (mPlayer != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_SETDATASOURCEBASE64, path).sendToTarget();
        } else {
            synchronized (mWaitList) {
                if (mPlayer != null && mServiceIsConnected) {
                    mSomeWorkHandle.obtainMessage(DO_SETDATASOURCEBASE64, path).sendToTarget();
                } else {
                    mWaitList.add(mSomeWorkHandle.obtainMessage(DO_SETDATASOURCEBASE64, path));
                }
            }
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
    public void setDataSource(FileDescriptor fd) throws IOException {
        ParcelFileDescriptor pfd = ParcelFileDescriptor.dup(fd);
        if (mPlayer != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_SETDATASOURCEFD, pfd).sendToTarget();
        } else {
            synchronized (mWaitList) {
                if (mPlayer != null && mServiceIsConnected) {
                    mSomeWorkHandle.obtainMessage(DO_SETDATASOURCEFD, pfd).sendToTarget();
                } else {
                    mWaitList.add(mSomeWorkHandle.obtainMessage(DO_SETDATASOURCEFD, pfd));
                }
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

    public String getDataSource() {
        return mDataSource;
    }

    public void prepareAsync() {
        if (mPlayer != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_PREPAREASYNC).sendToTarget();
        } else {
            synchronized (mWaitList) {
                if (mPlayer != null && mServiceIsConnected) {
                    mSomeWorkHandle.obtainMessage(DO_PREPAREASYNC).sendToTarget();
                } else {
                    mWaitList.add(mSomeWorkHandle.obtainMessage(DO_PREPAREASYNC));
                }
            }
        }
    }

    public void seekTo(long msec) {
        if (mPlayer != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_SEEKTO, msec).sendToTarget();
        } else {
            synchronized (mWaitList) {
                if (mPlayer != null && mServiceIsConnected) {
                    mSomeWorkHandle.obtainMessage(DO_SEEKTO, msec).sendToTarget();
                } else {
                    mWaitList.add(mSomeWorkHandle.obtainMessage(DO_SEEKTO, msec));
                }
            }
        }
    }

    public void handleRelease() {
        mServiceIsConnected = false;
        resetListeners();

        mSomeWorkHandle.removeCallbacksAndMessages(null);
        synchronized (mWaitList) {
            mWaitList.clear();
        }
        if (mPlayer != null) {
            try {
                mPlayer.release();
                if (mService != null && mClient != null) {
                    mService.removeClient(mClient.hashCode());
                }
            } catch (RemoteException e) {
                onBuglyReport(e);
            }
        }

        mHandleThread.quit();

        mContext.unbindService(mIjkMediaPlayerServiceConnection);
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
    public void release() {
        mPlayerAction = PLAYER_ACTION_IS_RELEASE;
        if (mPlayer != null && mServiceIsConnected) {
            try {
                mPlayer.pause();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            mSomeWorkHandle.obtainMessage(DO_RELEASE).sendToTarget();
        } else {
            synchronized (mWaitList) {
                if (mPlayer != null && mServiceIsConnected) {
                    try {
                        mPlayer.pause();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    mSomeWorkHandle.obtainMessage(DO_RELEASE).sendToTarget();
                    return;
                }
                synchronized (mWaitList) {
                    mWaitList.clear();
                }
                if (mIjkMediaPlayerServiceConnection != null) {
                    mContext.unbindService(mIjkMediaPlayerServiceConnection);
                    mIjkMediaPlayerServiceConnection = null;
                }
                mSomeWorkHandle.removeCallbacksAndMessages(null);
            }
            mHandleThread.quit();
            try {
                mHandleThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void syncReset() {
        synchronized (mWaitList) {
            mSomeWorkHandle.removeCallbacksAndMessages(null);
            mWaitList.clear();
        }
        try {
            if (mPlayer != null && mServiceIsConnected) {
                mPlayer.pause();
                mPlayer.reset();
            }
        } catch (RemoteException e) {
            onBuglyReport(e);
        }
    }

    public void start() {
        setOption(OPT_CATEGORY_PLAYER, "seek-at-start", mStartTime);
        prepareAsync();
    }

    public void setOption(int category, String name, String value)
    {
        if (mPlayer != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_SETOPTIONSTRING, category, 0, new Pair<>(name, value)).sendToTarget();
        } else {
            synchronized (mWaitList) {
                if (mPlayer != null && mServiceIsConnected) {
                    mSomeWorkHandle.obtainMessage(DO_SETOPTIONSTRING, category, 0, new Pair<>(name, value)).sendToTarget();
                } else {
                    mWaitList.add(mSomeWorkHandle.obtainMessage(DO_SETOPTIONSTRING, category, 0, new Pair<>(name, value)));
                }
            }
        }
    }

    public void setOption(int category, String name, long value)
    {
        if (mPlayer != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_SETOPTIONLONG, category, 0, new Pair<>(name, value)).sendToTarget();
        } else {
            synchronized (mWaitList) {
                if (mPlayer != null && mServiceIsConnected) {
                    mSomeWorkHandle.obtainMessage(DO_SETOPTIONLONG, category, 0, new Pair<>(name, value)).sendToTarget();
                } else {
                    mWaitList.add(mSomeWorkHandle.obtainMessage(DO_SETOPTIONLONG, category, 0, new Pair<>(name, value)));
                }
            }
        }
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
        String onMediaCodecSelect(IjkMediaMetadataRetriever mp, String mimeType, int profile, int level);
    }
    private OnMediaCodecSelectListener mOnMediaCodecSelectListener;
    public void setOnMediaCodecSelectListener(OnMediaCodecSelectListener listener) {
        mOnMediaCodecSelectListener = listener;
    }

    public void resetListeners() {
        mOnNativeInvokeListener = null;
        mOnServiceIsConnectedListener = null;
        mOnMediaCodecSelectListener = null;
        mOnFrameGenerateListener = null;
    }

    public static class DefaultMediaCodecSelector implements OnMediaCodecSelectListener {
        public static final DefaultMediaCodecSelector sInstance = new DefaultMediaCodecSelector();

        @SuppressWarnings("deprecation")
        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        public String onMediaCodecSelect(IjkMediaMetadataRetriever mp, String mimeType, int profile, int level) {
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

    private OnFrameGenerateListener mOnFrameGenerateListener = null;
    public void setFrameTimeCallback(OnFrameGenerateListener listener) {
        mOnFrameGenerateListener = listener;
    }

    public boolean init(String imgCachePath, long startTime, long endTime, int num, int imgDefinition) {
        if (TextUtils.isEmpty(imgCachePath)) {
            return false;
        }

        if (startTime < 0 || endTime < 0 || num <= 0 || endTime < startTime) {
            return false;
        }

        mStartTime     = startTime;
        mEndTime       = endTime;
        mNum           = num;
        mImgDefinition = imgDefinition;
        mImgCachePath  = imgCachePath;

        if (mPlayer != null && mServiceIsConnected) {
            mSomeWorkHandle.obtainMessage(DO_SETFRAMEATTIME).sendToTarget();
        } else {
            synchronized (mWaitList) {
                if (mPlayer != null && mServiceIsConnected) {
                    mSomeWorkHandle.obtainMessage(DO_SETFRAMEATTIME).sendToTarget();
                } else {
                    mWaitList.add(mSomeWorkHandle.obtainMessage(DO_SETFRAMEATTIME));
                }
            }
        }
        return true;
    }

    public interface OnFrameGenerateListener {
        /**
         * listen frame generate
         * timestamp : pts
         * resultCode : > 0 complete 0 processing < 0 error
         * fileName : png file name
         */
        boolean onFrameGenerate(int timestamp, int resultCode, String fileName);
    }
}
