/*
 * ffmpeg_api_jni.c
 *
 * Copyright (c) 2014 Bilibili
 * Copyright (c) 2014 Zhang Rui <bbcallen@gmail.com>
 *
 * This file is part of ijkPlayer.
 *
 * ijkPlayer is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * ijkPlayer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with ijkPlayer; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

#include "ffmpeg_api_jni.h"

#include <assert.h>
#include <string.h>
#include <jni.h>
#include "../ff_ffinc.h"
#include "ijksdl/ijksdl_log.h"
#include "ijksdl/android/ijksdl_android_jni.h"

#define JNI_CLASS_FFMPEG_API "tv/danmaku/ijk/media/player/ffmpeg/FFmpegApi"

typedef struct ffmpeg_api_fields_t {
    jclass clazz;
} ffmpeg_api_fields_t;
static ffmpeg_api_fields_t g_clazz;

static jstring
FFmpegApi_av_base64_encode(JNIEnv *env, jclass clazz, jbyteArray in)
{
    jstring ret_string = NULL;
    char*   out_buffer = 0;
    int     out_size   = 0;
    jbyte*  in_buffer  = 0;
    jsize   in_size    = (*env)->GetArrayLength(env, in);
    if (in_size <= 0)
        goto fail;

    in_buffer = (*env)->GetByteArrayElements(env, in, NULL);
    if (!in_buffer)
        goto fail;

    out_size = AV_BASE64_SIZE(in_size);
    out_buffer = malloc(out_size + 1);
    if (!out_buffer)
        goto fail;
    out_buffer[out_size] = 0;

    if (!av_base64_encode(out_buffer, out_size, (const uint8_t *)in_buffer, in_size))
        goto fail;

    ret_string = (*env)->NewStringUTF(env, out_buffer);
fail:
    if (in_buffer) {
        (*env)->ReleaseByteArrayElements(env, in, in_buffer, JNI_ABORT);
        in_buffer = NULL;
    }
    if (out_buffer) {
        free(out_buffer);
        out_buffer = NULL;
    }
    return ret_string;
}



static jintArray
FFmpegApi_av_get_resolution(JNIEnv *env, jclass clazz, jstring in)
{
    H264ParamSets ps;
    const PPS *pps = NULL;
    const SPS *sps = NULL;
    memset(&ps, 0, sizeof(ps));
    int is_avc=0;
    int nal_length_size=0;
    int i;
    int res[2] = {0};
    jintArray jres = (*env)->NewIntArray(env, 2);

    char * extradata_enc = (*env)->GetStringUTFChars(env, in, NULL);

    if (!extradata_enc)
        goto fail;

    //Init Video Stream
    int extradata_size = AV_BASE64_DECODE_SIZE(strlen(extradata_enc)) + AV_INPUT_BUFFER_PADDING_SIZE;
    char * extradata = av_mallocz(extradata_size);
    int r_extradata_size = av_base64_decode(extradata, extradata_enc, extradata_size);

    if ((ff_h264_decode_extradata(extradata, r_extradata_size, &ps,
                     &is_avc, &nal_length_size,
                     0, NULL)) < 0)
        goto fail;


    for (i = 0; i < MAX_PPS_COUNT; i++) {
        if (ps.pps_list[i]) {
            pps = (const PPS*)ps.pps_list[i]->data;
            break;
        }
    }

    if (pps)
        if (ps.sps_list[pps->sps_id])
            sps = (const SPS*)ps.sps_list[pps->sps_id]->data;

    if (pps && sps) {
        res[0] = sps->mb_width  * 16 - (sps->crop_right + sps->crop_left);
        res[1] = sps->mb_height * 16 - (sps->crop_top   + sps->crop_bottom);
    } else
        goto fail;

    av_log(NULL, AV_LOG_INFO, "width = %d, height = %d\n", res[0], res[1]);
fail:
    if (extradata)
        av_freep(&extradata);
    (*env)->SetIntArrayRegion(env, jres, 0, 2, res);
    return jres;

}

static JNINativeMethod g_methods[] = {
    {"av_base64_encode", "([B)Ljava/lang/String;", (void *) FFmpegApi_av_base64_encode},
    {"av_get_resolution", "(Ljava/lang/String;)[I", (void*) FFmpegApi_av_get_resolution}
};

int FFmpegApi_global_init(JNIEnv *env)
{
    int ret = 0;

    IJK_FIND_JAVA_CLASS(env, g_clazz.clazz, JNI_CLASS_FFMPEG_API);
    (*env)->RegisterNatives(env, g_clazz.clazz, g_methods, NELEM(g_methods));

    return ret;
}
