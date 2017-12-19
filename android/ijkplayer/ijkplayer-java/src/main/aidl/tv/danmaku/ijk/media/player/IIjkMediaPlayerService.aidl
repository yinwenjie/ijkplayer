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

import tv.danmaku.ijk.media.player.IIjkMediaPlayer;
import tv.danmaku.ijk.media.player.IIjkMediaPlayerClient;

interface IIjkMediaPlayerService {
    IIjkMediaPlayer create(int connId, IIjkMediaPlayerClient client);
    void removeClient(int connId);
}