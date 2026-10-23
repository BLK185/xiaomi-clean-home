package io.github.blk185.xiaomicleanhome;

/** Android-independent quality decisions: never invent pixels or crop a portrait into a backdrop. */
final class PreviewArtwork {
    static boolean landscape(int aspect) { return aspect == 0 || aspect == 1 || aspect == 2; }
    static boolean fullBackdrop(int width, int height) {
        return width >= 960 && height >= 500 && width >= height * 1.45 && width <= height * 2.5;
    }
    static int sample(int width, int height) {
        int value = 1;
        while ((long)(width / value) * (height / value) > 2200000L) value *= 2;
        return value;
    }
    static String youtubeHighResolution(String value) {
        if (value == null) return null;
        // Only public YouTube thumbnail URLs, never signed provider or arbitrary CDN URLs.
        if (!value.matches("https://(?:i|i[0-9])\\.ytimg\\.com/vi/[A-Za-z0-9_-]{11}/(?:default|mqdefault|hqdefault|sddefault|hq720)\\.jpg(?:\\?.*)?")) return value;
        return value.split("\\?",2)[0].replaceFirst("/(?:default|mqdefault|hqdefault|sddefault|hq720)\\.jpg", "/maxresdefault.jpg");
    }
    static String highResolution(String value) {
        return youtubeHighResolution(value);
    }
    static int contentPriority(String pkg,String title,String uri) {
        String text=title==null?"":title.toLowerCase(java.util.Locale.ROOT);
        if(pkg.equals("com.netflix.ninja")&&(text.startsWith("collection netflix")||text.startsWith("netflix collection")))return -1000;
        if(pkg.equals("com.amazon.amazonvideo.livingroom")&&uri!=null&&uri.contains("/le-target-images-prod/"))return -500;
        return 0;
    }
}
