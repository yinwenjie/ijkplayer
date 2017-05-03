package tv.danmaku.android.log;

@SimpleCClassName
public class BLog {
    public static void v(String tag, String message);
    public static void d(String tag, String message);
    public static void i(String tag, String message);
    public static void w(String tag, String message);
    public static void e(String tag, String message);
}
