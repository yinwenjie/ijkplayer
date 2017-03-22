package tv.danmaku.ijk.media.player.misc;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import tv.danmaku.ijk.media.player.services.IjkMediaPlayerService;

import static android.system.OsConstants.SEEK_CUR;
import static android.system.OsConstants.SEEK_SET;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * Created by raymond on 2017/3/9.
 */

public class SystemHttp implements IAndroidIO {
    private final static String TAG = SystemHttp.class.getName();
    private String mUrl;
    private InputStream mInputStream;
    private OkHttpClient mHttpClient;
    private long mCount = 0;
    private boolean mAbort;
    private long mCurPos = 0;
    private Response mResponse;
    private IjkMediaPlayerService.SystemApplication mCallback;

    private static final int IJKAVSEEK_SIZE = 0x10000;

    private String getHostAddr(String url) {
        if (url == null) {
            return null;
        }

        try {
            String urlSplitOne[] = url.split("//");
            String urlPartOne = urlSplitOne[1];
            String urlPartTwo = null;
            if (!TextUtils.isEmpty(urlPartOne)) {
                String urlSplitTwo[] = urlSplitOne[1].split("/");
                urlPartTwo = urlSplitTwo[0];
            }
            String hostName = urlPartTwo;

            if (!TextUtils.isEmpty(hostName)) {
                InetAddress addr = InetAddress.getByName(hostName);
                if (addr != null) {
                    return addr.getHostAddress();
                }
            }
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    private void executeRequest(String url, long offset , boolean isHttpSeek) throws IOException {
        boolean isSuccessful = false;
        int retryCounter = 0;

        if (url == null) {
            return;
        }

        while (!isSuccessful && !mAbort) {
            if (mCallback != null) {
                if (isHttpSeek) {
                    Bundle bundle = new Bundle();
                    bundle.putInt(IjkMediaPlayer.OnNativeInvokeListener.ARG_RETRY_COUNTER, retryCounter);
                    bundle.putString(IjkMediaPlayer.OnNativeInvokeListener.ARG_URL, mUrl);
                    mCallback.notifyNativeInvoke(IjkMediaPlayer.OnNativeInvokeListener.EVENT_WILL_HTTP_SEEK, bundle);
                }

                Bundle bundle = new Bundle();

                bundle.putInt(IjkMediaPlayer.OnNativeInvokeListener.ARG_RETRY_COUNTER, retryCounter);
                mCallback.notifyNativeInvoke(IjkMediaPlayer.OnNativeInvokeListener.CTRL_WILL_HTTP_OPEN, bundle);
                String newUrl = bundle.getString(IjkMediaPlayer.OnNativeInvokeListener.ARG_URL, null);
                if (newUrl != null) {
                    mUrl = newUrl;
                }

                bundle = new Bundle();
                bundle.putString(IjkMediaPlayer.OnNativeInvokeListener.ARG_URL, mUrl);
                mCallback.notifyNativeInvoke(IjkMediaPlayer.OnNativeInvokeListener.EVENT_WILL_HTTP_OPEN, bundle);

                bundle = new Bundle();
                mCallback.notifyNativeInvoke(IjkMediaPlayer.OnNativeInvokeListener.CTRL_WILL_TCP_OPEN, bundle);
            }

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", System.getProperty("http.agent"))
                    .header("Range", "bytes=" + Long.valueOf(offset).toString() + "-")
                    .build();
            mResponse = mHttpClient.newCall(request).execute();
            mInputStream = mResponse.body().byteStream();
            isSuccessful = mResponse.isSuccessful();
            retryCounter++;

            if (mCallback != null) {
                if (isHttpSeek) {
                    Bundle bundle = new Bundle();
                    bundle.putInt(IjkMediaPlayer.OnNativeInvokeListener.ARG_RETRY_COUNTER, retryCounter);
                    bundle.putString(IjkMediaPlayer.OnNativeInvokeListener.ARG_URL, mUrl);
                    bundle.putInt(IjkMediaPlayer.OnNativeInvokeListener.ARG_ERROR, 0);
                    bundle.putInt(IjkMediaPlayer.OnNativeInvokeListener.ARG_HTTP_CODE, mResponse.code());
                    mCallback.notifyNativeInvoke(IjkMediaPlayer.OnNativeInvokeListener.EVENT_DID_HTTP_SEEK, bundle);
                }

                Bundle bundle = new Bundle();
                String ip = getHostAddr(mUrl);
                if (ip != null) {
                    bundle.putString(IjkMediaPlayer.OnNativeInvokeListener.ARG_IP, ip);
                }
                mCallback.notifyNativeInvoke(IjkMediaPlayer.OnNativeInvokeListener.CTRL_DID_TCP_OPEN, bundle);

                bundle = new Bundle();
                bundle.putString(IjkMediaPlayer.OnNativeInvokeListener.ARG_URL, mUrl);
                bundle.putInt(IjkMediaPlayer.OnNativeInvokeListener.ARG_ERROR, 0);
                bundle.putInt(IjkMediaPlayer.OnNativeInvokeListener.ARG_HTTP_CODE, mResponse.code());
                mCallback.notifyNativeInvoke(IjkMediaPlayer.OnNativeInvokeListener.EVENT_DID_HTTP_OPEN, bundle);
            }
        }
    }

    @Override
    public int open(String url) throws IOException {
        if (TextUtils.isEmpty(url)) {
            return -1;
        }
        mUrl = url;
        mCurPos = 0;

        mHttpClient = new OkHttpClient.Builder()
                .readTimeout(2, TimeUnit.SECONDS)
                .connectTimeout(6, TimeUnit.SECONDS)
                .build();

        executeRequest(mUrl, mCurPos, false);
        return 0;
    }

    @Override
    public int read(byte[] buffer, int size) throws IOException {
        int ret =0;
        while (ret <= 0 && !mAbort) {
            try {
                if (mInputStream == null) {
                    if (mResponse != null) {
                        mResponse.close();
                    }
                    executeRequest(mUrl, mCurPos, false);
                } else {
                    ret = mInputStream.read(buffer, 0, size);
                    if (ret >= 0) {
                        mCurPos += ret;
                        mCount += ret;
                        return ret;
                    }
                }
            } catch (SocketTimeoutException e) {
                if (mResponse != null) {
                    mResponse.close();
                }
                executeRequest(mUrl, mCurPos, false);
                Log.w(TAG, "SocketTimeoutException");
            } catch (Exception e) {
                if (mResponse != null) {
                    mResponse.close();
                }
                executeRequest(mUrl, mCurPos, false);
                Log.w(TAG, "Exception");
            }
        }
        return ret;
    }

    @Override
    public long seek(long offset, int whence) throws IOException {
        long newPos;

        if (whence == IJKAVSEEK_SIZE) {
            if (mResponse != null && mResponse.body() != null) {
                return mResponse.body().contentLength();
            } else {
                return -1;
            }
        } else if (whence == SEEK_CUR) {
            newPos = mCurPos + offset;
        } else if (whence == SEEK_SET) {
            newPos = offset;
        } else {
            return -1;
        }

        if (mResponse != null) {
            mResponse.close();
        }

        executeRequest(mUrl, newPos, true);
        mCurPos = offset;
        return offset;
    }

    @Override
    public int close() throws IOException {
        if (mResponse != null) {
            mResponse.close();
        }
        return 0;
    }

    public void abort() {
        mAbort = true;
    }

    public void setUrl(String url) {
        mUrl = url;
    }

    public void injectCallback(IjkMediaPlayerService.SystemApplication callback) {
        mCallback = callback;
    }

    public long getAndroidIOTrafficStatistic() {
        return mCount;
    }
}
