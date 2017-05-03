/*
 * Copyright (C) 2015 Zhang Rui <bbcallen@gmail.com>
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

/*
 * https://github.com/Bilibili/jni4android
 * This file is automatically generated by jni4android, do not modify.
 */

#include "BLog.h"

typedef struct J4AC_tv_danmaku_android_log_BLog {
    jclass id;

    jmethodID method_v;
    jmethodID method_d;
    jmethodID method_i;
    jmethodID method_w;
    jmethodID method_e;
} J4AC_tv_danmaku_android_log_BLog;
static J4AC_tv_danmaku_android_log_BLog class_J4AC_tv_danmaku_android_log_BLog;

void J4AC_tv_danmaku_android_log_BLog__v(JNIEnv *env, jstring tag, jstring message)
{
    (*env)->CallStaticVoidMethod(env, class_J4AC_tv_danmaku_android_log_BLog.id, class_J4AC_tv_danmaku_android_log_BLog.method_v, tag, message);
}

void J4AC_tv_danmaku_android_log_BLog__v__catchAll(JNIEnv *env, jstring tag, jstring message)
{
    J4AC_tv_danmaku_android_log_BLog__v(env, tag, message);
    J4A_ExceptionCheck__catchAll(env);
}

void J4AC_tv_danmaku_android_log_BLog__v__withCString(JNIEnv *env, const char *tag_cstr__, const char *message_cstr__)
{
    jstring tag = NULL;
    jstring message = NULL;

    tag = (*env)->NewStringUTF(env, tag_cstr__);
    if (J4A_ExceptionCheck__throwAny(env) || !tag)
        goto fail;
    message = (*env)->NewStringUTF(env, message_cstr__);
    if (J4A_ExceptionCheck__throwAny(env) || !message)
        goto fail;

    J4AC_tv_danmaku_android_log_BLog__v(env, tag, message);

fail:
    J4A_DeleteLocalRef__p(env, &tag);
    J4A_DeleteLocalRef__p(env, &message);
}

void J4AC_tv_danmaku_android_log_BLog__v__withCString__catchAll(JNIEnv *env, const char *tag_cstr__, const char *message_cstr__)
{
    jstring tag = NULL;
    jstring message = NULL;

    tag = (*env)->NewStringUTF(env, tag_cstr__);
    if (J4A_ExceptionCheck__catchAll(env) || !tag)
        goto fail;
    message = (*env)->NewStringUTF(env, message_cstr__);
    if (J4A_ExceptionCheck__catchAll(env) || !message)
        goto fail;

    J4AC_tv_danmaku_android_log_BLog__v__catchAll(env, tag, message);

fail:
    J4A_DeleteLocalRef__p(env, &tag);
    J4A_DeleteLocalRef__p(env, &message);
}

void J4AC_tv_danmaku_android_log_BLog__d(JNIEnv *env, jstring tag, jstring message)
{
    (*env)->CallStaticVoidMethod(env, class_J4AC_tv_danmaku_android_log_BLog.id, class_J4AC_tv_danmaku_android_log_BLog.method_d, tag, message);
}

void J4AC_tv_danmaku_android_log_BLog__d__catchAll(JNIEnv *env, jstring tag, jstring message)
{
    J4AC_tv_danmaku_android_log_BLog__d(env, tag, message);
    J4A_ExceptionCheck__catchAll(env);
}

void J4AC_tv_danmaku_android_log_BLog__d__withCString(JNIEnv *env, const char *tag_cstr__, const char *message_cstr__)
{
    jstring tag = NULL;
    jstring message = NULL;

    tag = (*env)->NewStringUTF(env, tag_cstr__);
    if (J4A_ExceptionCheck__throwAny(env) || !tag)
        goto fail;
    message = (*env)->NewStringUTF(env, message_cstr__);
    if (J4A_ExceptionCheck__throwAny(env) || !message)
        goto fail;

    J4AC_tv_danmaku_android_log_BLog__d(env, tag, message);

fail:
    J4A_DeleteLocalRef__p(env, &tag);
    J4A_DeleteLocalRef__p(env, &message);
}

void J4AC_tv_danmaku_android_log_BLog__d__withCString__catchAll(JNIEnv *env, const char *tag_cstr__, const char *message_cstr__)
{
    jstring tag = NULL;
    jstring message = NULL;

    tag = (*env)->NewStringUTF(env, tag_cstr__);
    if (J4A_ExceptionCheck__catchAll(env) || !tag)
        goto fail;
    message = (*env)->NewStringUTF(env, message_cstr__);
    if (J4A_ExceptionCheck__catchAll(env) || !message)
        goto fail;

    J4AC_tv_danmaku_android_log_BLog__d__catchAll(env, tag, message);

fail:
    J4A_DeleteLocalRef__p(env, &tag);
    J4A_DeleteLocalRef__p(env, &message);
}

void J4AC_tv_danmaku_android_log_BLog__i(JNIEnv *env, jstring tag, jstring message)
{
    (*env)->CallStaticVoidMethod(env, class_J4AC_tv_danmaku_android_log_BLog.id, class_J4AC_tv_danmaku_android_log_BLog.method_i, tag, message);
}

void J4AC_tv_danmaku_android_log_BLog__i__catchAll(JNIEnv *env, jstring tag, jstring message)
{
    J4AC_tv_danmaku_android_log_BLog__i(env, tag, message);
    J4A_ExceptionCheck__catchAll(env);
}

void J4AC_tv_danmaku_android_log_BLog__i__withCString(JNIEnv *env, const char *tag_cstr__, const char *message_cstr__)
{
    jstring tag = NULL;
    jstring message = NULL;

    tag = (*env)->NewStringUTF(env, tag_cstr__);
    if (J4A_ExceptionCheck__throwAny(env) || !tag)
        goto fail;
    message = (*env)->NewStringUTF(env, message_cstr__);
    if (J4A_ExceptionCheck__throwAny(env) || !message)
        goto fail;

    J4AC_tv_danmaku_android_log_BLog__i(env, tag, message);

fail:
    J4A_DeleteLocalRef__p(env, &tag);
    J4A_DeleteLocalRef__p(env, &message);
}

void J4AC_tv_danmaku_android_log_BLog__i__withCString__catchAll(JNIEnv *env, const char *tag_cstr__, const char *message_cstr__)
{
    jstring tag = NULL;
    jstring message = NULL;

    tag = (*env)->NewStringUTF(env, tag_cstr__);
    if (J4A_ExceptionCheck__catchAll(env) || !tag)
        goto fail;
    message = (*env)->NewStringUTF(env, message_cstr__);
    if (J4A_ExceptionCheck__catchAll(env) || !message)
        goto fail;

    J4AC_tv_danmaku_android_log_BLog__i__catchAll(env, tag, message);

fail:
    J4A_DeleteLocalRef__p(env, &tag);
    J4A_DeleteLocalRef__p(env, &message);
}

void J4AC_tv_danmaku_android_log_BLog__w(JNIEnv *env, jstring tag, jstring message)
{
    (*env)->CallStaticVoidMethod(env, class_J4AC_tv_danmaku_android_log_BLog.id, class_J4AC_tv_danmaku_android_log_BLog.method_w, tag, message);
}

void J4AC_tv_danmaku_android_log_BLog__w__catchAll(JNIEnv *env, jstring tag, jstring message)
{
    J4AC_tv_danmaku_android_log_BLog__w(env, tag, message);
    J4A_ExceptionCheck__catchAll(env);
}

void J4AC_tv_danmaku_android_log_BLog__w__withCString(JNIEnv *env, const char *tag_cstr__, const char *message_cstr__)
{
    jstring tag = NULL;
    jstring message = NULL;

    tag = (*env)->NewStringUTF(env, tag_cstr__);
    if (J4A_ExceptionCheck__throwAny(env) || !tag)
        goto fail;
    message = (*env)->NewStringUTF(env, message_cstr__);
    if (J4A_ExceptionCheck__throwAny(env) || !message)
        goto fail;

    J4AC_tv_danmaku_android_log_BLog__w(env, tag, message);

fail:
    J4A_DeleteLocalRef__p(env, &tag);
    J4A_DeleteLocalRef__p(env, &message);
}

void J4AC_tv_danmaku_android_log_BLog__w__withCString__catchAll(JNIEnv *env, const char *tag_cstr__, const char *message_cstr__)
{
    jstring tag = NULL;
    jstring message = NULL;

    tag = (*env)->NewStringUTF(env, tag_cstr__);
    if (J4A_ExceptionCheck__catchAll(env) || !tag)
        goto fail;
    message = (*env)->NewStringUTF(env, message_cstr__);
    if (J4A_ExceptionCheck__catchAll(env) || !message)
        goto fail;

    J4AC_tv_danmaku_android_log_BLog__w__catchAll(env, tag, message);

fail:
    J4A_DeleteLocalRef__p(env, &tag);
    J4A_DeleteLocalRef__p(env, &message);
}

void J4AC_tv_danmaku_android_log_BLog__e(JNIEnv *env, jstring tag, jstring message)
{
    (*env)->CallStaticVoidMethod(env, class_J4AC_tv_danmaku_android_log_BLog.id, class_J4AC_tv_danmaku_android_log_BLog.method_e, tag, message);
}

void J4AC_tv_danmaku_android_log_BLog__e__catchAll(JNIEnv *env, jstring tag, jstring message)
{
    J4AC_tv_danmaku_android_log_BLog__e(env, tag, message);
    J4A_ExceptionCheck__catchAll(env);
}

void J4AC_tv_danmaku_android_log_BLog__e__withCString(JNIEnv *env, const char *tag_cstr__, const char *message_cstr__)
{
    jstring tag = NULL;
    jstring message = NULL;

    tag = (*env)->NewStringUTF(env, tag_cstr__);
    if (J4A_ExceptionCheck__throwAny(env) || !tag)
        goto fail;
    message = (*env)->NewStringUTF(env, message_cstr__);
    if (J4A_ExceptionCheck__throwAny(env) || !message)
        goto fail;

    J4AC_tv_danmaku_android_log_BLog__e(env, tag, message);

fail:
    J4A_DeleteLocalRef__p(env, &tag);
    J4A_DeleteLocalRef__p(env, &message);
}

void J4AC_tv_danmaku_android_log_BLog__e__withCString__catchAll(JNIEnv *env, const char *tag_cstr__, const char *message_cstr__)
{
    jstring tag = NULL;
    jstring message = NULL;

    tag = (*env)->NewStringUTF(env, tag_cstr__);
    if (J4A_ExceptionCheck__catchAll(env) || !tag)
        goto fail;
    message = (*env)->NewStringUTF(env, message_cstr__);
    if (J4A_ExceptionCheck__catchAll(env) || !message)
        goto fail;

    J4AC_tv_danmaku_android_log_BLog__e__catchAll(env, tag, message);

fail:
    J4A_DeleteLocalRef__p(env, &tag);
    J4A_DeleteLocalRef__p(env, &message);
}

int J4A_loadClass__J4AC_tv_danmaku_android_log_BLog(JNIEnv *env)
{
    int         ret                   = -1;
    const char *J4A_UNUSED(name)      = NULL;
    const char *J4A_UNUSED(sign)      = NULL;
    jclass      J4A_UNUSED(class_id)  = NULL;
    int         J4A_UNUSED(api_level) = 0;

    if (class_J4AC_tv_danmaku_android_log_BLog.id != NULL)
        return 0;

    sign = "tv/danmaku/android/log/BLog";
    class_J4AC_tv_danmaku_android_log_BLog.id = J4A_FindClass__asGlobalRef__catchAll(env, sign);
    if (class_J4AC_tv_danmaku_android_log_BLog.id == NULL)
        goto fail;

    class_id = class_J4AC_tv_danmaku_android_log_BLog.id;
    name     = "v";
    sign     = "(Ljava/lang/String;Ljava/lang/String;)V";
    class_J4AC_tv_danmaku_android_log_BLog.method_v = J4A_GetStaticMethodID__catchAll(env, class_id, name, sign);
    if (class_J4AC_tv_danmaku_android_log_BLog.method_v == NULL)
        goto fail;

    class_id = class_J4AC_tv_danmaku_android_log_BLog.id;
    name     = "d";
    sign     = "(Ljava/lang/String;Ljava/lang/String;)V";
    class_J4AC_tv_danmaku_android_log_BLog.method_d = J4A_GetStaticMethodID__catchAll(env, class_id, name, sign);
    if (class_J4AC_tv_danmaku_android_log_BLog.method_d == NULL)
        goto fail;

    class_id = class_J4AC_tv_danmaku_android_log_BLog.id;
    name     = "i";
    sign     = "(Ljava/lang/String;Ljava/lang/String;)V";
    class_J4AC_tv_danmaku_android_log_BLog.method_i = J4A_GetStaticMethodID__catchAll(env, class_id, name, sign);
    if (class_J4AC_tv_danmaku_android_log_BLog.method_i == NULL)
        goto fail;

    class_id = class_J4AC_tv_danmaku_android_log_BLog.id;
    name     = "w";
    sign     = "(Ljava/lang/String;Ljava/lang/String;)V";
    class_J4AC_tv_danmaku_android_log_BLog.method_w = J4A_GetStaticMethodID__catchAll(env, class_id, name, sign);
    if (class_J4AC_tv_danmaku_android_log_BLog.method_w == NULL)
        goto fail;

    class_id = class_J4AC_tv_danmaku_android_log_BLog.id;
    name     = "e";
    sign     = "(Ljava/lang/String;Ljava/lang/String;)V";
    class_J4AC_tv_danmaku_android_log_BLog.method_e = J4A_GetStaticMethodID__catchAll(env, class_id, name, sign);
    if (class_J4AC_tv_danmaku_android_log_BLog.method_e == NULL)
        goto fail;

    J4A_ALOGD("J4ALoader: OK: '%s' loaded\n", "tv.danmaku.android.log.BLog");
    ret = 0;
fail:
    return ret;
}
