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

package tv.danmaku.ijk.media.player;

import android.view.Surface;

interface IIjkMediaPlayer {
    void start();
    void pause();
    void stop();
    void release();
    void reset();
    void setSurface(in Surface surface);
    void setDataSource(String path);
    void setDataSourceBase64(String path);
    void setDataSourceKey(String path, in String[] keys, in String[] values);
    void setDataSourceFd(in ParcelFileDescriptor fd);
    void prepareAsync();
    void setStreamSelected(int stream, boolean select);
    boolean isPlaying();
    void seekTo(long msec);
    long getCurrentPosition();
    long getDuration();
    void setLoopCount(int loopCount);
    int getLoopCount();
    float getPropertyFloat(int property, float defaultValue);
    void  setPropertyFloat(int property, float value);
    long  getPropertyLong(int property, long defaultValue);
    void  setPropertyLong(int property, long value);
    void setVolume(float leftVolume, float rightVolume);
    int getAudioSessionId();
    String getVideoCodecInfo();
    String getAudioCodecInfo();
    void setOptionString(int category, String name, String value);
    void setOptionLong(int category, String name, long value);
    Bundle getMediaMeta();
    void nativeFinalize();
    String getColorFormatName(int mediaCodecColorFormat);
    void nativeProfileBegin(String libName);
    void nativeProfileEnd();
    void nativeSetLogLevel(int level);
    void setAndroidIOCallback();
    long getAndroidIOTrafficStatistic();
    void setFrameAtTime(String imgCachePath, long startTime, long endTime, int num, int imgDefinition);
}
