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
import android.os.IBinder;
import android.util.SparseArray;
import java.io.File;
import java.lang.ref.WeakReference;

import tv.danmaku.ijk.media.player.IIjkMediaPlayer;
import tv.danmaku.ijk.media.player.IIjkMediaPlayerClient;
import tv.danmaku.ijk.media.player.IIjkMediaPlayerService;
import tv.danmaku.ijk.media.player.IjkLibLoader;

public class IjkMediaPlayerService extends Service {
    private static final String TAG = "IjkMediaPlayerService";
    private Bundle mLibBundle;
    private final SparseArray<WeakReference<IIjkMediaPlayer>> mClients = new SparseArray<>();

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
                        bundle.get("ijksdl") != null && bundle.get("ijkplayer") != null) {
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

    @Override
    public void onCreate() {
        super.onCreate();
        loadLibrariesOnce(mLibBundle);
    }

    IIjkMediaPlayerService.Stub mBinder = new IIjkMediaPlayerService.Stub() {
        @Override
        public IIjkMediaPlayer create(int connId, IIjkMediaPlayerClient client) {
            synchronized (mClients) {
                if (mClients != null) {
                    IjkMediaPlayerClient c = new IjkMediaPlayerClient(client);
                    mClients.append(connId, new WeakReference<IIjkMediaPlayer>(c));
                    return c;
                }
            }
            return null;
        }

        @Override
        public void removeClient(int connId) {
            synchronized (mClients) {
                mClients.remove(connId);
            }
        }
    };

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
    }
}
