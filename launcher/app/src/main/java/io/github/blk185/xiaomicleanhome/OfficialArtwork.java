package io.github.blk185.xiaomicleanhome;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses public publisher pages as data. Never executes scripts or alters signed image URLs. */
final class OfficialArtwork {
    private static final Pattern ASSET=Pattern.compile("^/[0-9]+/[0-9]+/(.+)-[a-f0-9]{6}-[0-9]+@[123]x\\.(?:jpg|jpeg|webp|avif)$");
    private static final Pattern TF1=Pattern.compile("https://photos\\.tf1\\.fr/([0-9]+)/([0-9]+)/[^\\s\\\"<>\\\\]+?@1x\\.(?:jpg|webp)");
    static String asset(String url) {
        try{URI uri=new URI(url);if(!"https".equals(uri.getScheme())||!"photos.tf1.fr".equals(uri.getHost()))return null;
            Matcher match=ASSET.matcher(uri.getPath());return match.matches()?match.group(1):null;
        }catch(Exception invalid){return null;}
    }
    static String tf1(String page,String original) {
        String key=asset(original);return key==null?null:tf1Images(page).get(key);
    }
    static java.util.Map<String,String> tf1Images(String page) {
        java.util.Map<String,String> images=new java.util.HashMap<>();
        java.util.Map<String,Long> qualities=new java.util.HashMap<>();
        Matcher urls=TF1.matcher(page);
        while(urls.find()){
            long width=Long.parseLong(urls.group(1)),height=Long.parseLong(urls.group(2));
            if(width<960||height<500||width>3840||height>2160||width<height*1.45||width>height*2.5)continue;
            String key=asset(urls.group());if(key==null)continue;
            long score=Math.min(width,1920)*Math.min(height,1080);
            if(score>qualities.getOrDefault(key,0L)){qualities.put(key,score);images.put(key,urls.group());}
        }
        return images;
    }
    static String netflixId(String intent) {
        if(intent==null)return null;
        // Only numeric IDs in explicit Netflix title/playback fields. Do not infer a title from tracking IDs.
        Matcher match=Pattern.compile("(?:/title/|/watch/|movieid=|video_id=|extra_movie_id=|jbv=)([0-9]{6,10})(?![0-9])",Pattern.CASE_INSENSITIVE).matcher(intent);
        return match.find()?match.group(1):null;
    }
    static String netflix(String page) {
        String normalized=page.replace("\\\"","\"");
        Matcher match=Pattern.compile("\"artwork\"\\s*:\\s*\\{\\s*\"billboard\"\\s*:\\s*\\{\\s*\"small\"\\s*:\\s*\"([^\"]+)\"").matcher(normalized);
        if(!match.find())return null;
        String image=decodeEscapes(match.group(1));
        try{
            URI uri=new URI(image);String host=uri.getHost();
            if("https".equals(uri.getScheme())&&host!=null&&host.endsWith(".nflxso.net")&&uri.getUserInfo()==null&&uri.getPort()==-1&&uri.getPath().startsWith("/dnm/")&&image.length()<8192)return image;
        }catch(Exception invalid){}
        return null;
    }
    private static String decodeEscapes(String value) {
        Matcher escape=Pattern.compile("\\\\(?:x([0-9a-fA-F]{2})|u([0-9a-fA-F]{4}))").matcher(value);
        StringBuffer decoded=new StringBuffer();
        while(escape.find())escape.appendReplacement(decoded,Matcher.quoteReplacement(String.valueOf((char)Integer.parseInt(escape.group(1)!=null?escape.group(1):escape.group(2),16))));
        escape.appendTail(decoded);return decoded.toString().replace("\\/","/").replace("&amp;","&");
    }
}
