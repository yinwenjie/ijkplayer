package tv.danmaku.ijk.media.player.ffmpeg;

public class FFmpegApi {
    public static native String av_base64_encode(byte in[]);
    /*
     * av_get_resolution：
     * 从Base64编码的extradata 获取分辨率宽高
     * 返回长度为2的数组， 0-> width , 1-> height
     */
    public static native int[] av_get_resolution(String in);
}
