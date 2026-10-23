package io.github.blk185.xiaomicleanhome;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.io.IOException;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
final class PhotoStore {
    static SharedPreferences prefs(Context context) { return context.getSharedPreferences("photo_saver",Context.MODE_PRIVATE); }
    static File directory(Context context) {
        File base = context.getExternalFilesDir("Pictures");
        if (base == null) base = context.getFilesDir();
        File folder = new File(base,"Veille");
        folder.mkdirs();
        return folder;
    }
    static boolean isPhoto(File file) { return file.isFile()&&file.getName().matches("(?i).+\\.(jpg|jpeg|png|webp|heic|heif)$"); }
    static synchronized LinkedHashMap<String,String> albums(Context c) {
        LinkedHashMap<String,String> result=new LinkedHashMap<>(); result.put("default","Sans dossier");
        try { JSONObject data=new JSONObject(prefs(c).getString("albums","{}")); Iterator<String> keys=data.keys();
            while(keys.hasNext()){String id=keys.next();if(id.matches("[a-zA-Z0-9_-]+"))result.put(id,data.getString(id));}
        }catch(Exception ignored){} return result;
    }
    private static void saveAlbums(Context c,Map<String,String> map) {prefs(c).edit().putString("albums",new JSONObject(map).toString()).apply();}
    static File albumDirectory(Context c,String id) throws IOException {
        if(!albums(c).containsKey(id))throw new IOException("Dossier introuvable");
        File folder=id.equals("default")?directory(c):new File(new File(directory(c),"Albums"),id);folder.mkdirs();return folder;
    }
    static synchronized String createAlbum(Context c,String label) throws Exception {
        label=label.trim();if(label.isEmpty()||label.length()>80)throw new IOException("Nom de dossier invalide");
        LinkedHashMap<String,String> map=albums(c);String id="album_"+UUID.randomUUID().toString().replace("-","");map.put(id,label);saveAlbums(c,map);albumDirectory(c,id);return id;
    }
    static synchronized void renameAlbum(Context c,String id,String label) throws Exception {
        LinkedHashMap<String,String> map=albums(c);if(!map.containsKey(id)||id.equals("default"))throw new IOException("Dossier non modifiable");
        label=label.trim();if(label.isEmpty()||label.length()>80)throw new IOException("Nom invalide");map.put(id,label);saveAlbums(c,map);
    }
    static synchronized void deleteAlbum(Context c,String id) throws Exception {
        if(id.equals("default"))throw new IOException("Le dossier par défaut est conservé");File folder=albumDirectory(c,id);
        File[] files=folder.listFiles();if(files!=null&&files.length>0)throw new IOException("Déplacez ou supprimez les photos de ce dossier avant de le retirer");
        if(!folder.delete())throw new IOException("Suppression impossible");LinkedHashMap<String,String> map=albums(c);map.remove(id);saveAlbums(c,map);
        if(prefs(c).getString("active_album","all").equals(id))prefs(c).edit().putString("active_album","all").apply();
    }
    static synchronized List<File> photos(Context c) {
        List<File> result=new ArrayList<>();for(String id:albums(c).keySet())try{File[] files=albumDirectory(c,id).listFiles(PhotoStore::isPhoto);if(files!=null)result.addAll(Arrays.asList(files));}catch(Exception ignored){}
        result.sort(Comparator.comparing(File::getName));return result;
    }
    static String id(Context c,File file) {try{return directory(c).getCanonicalFile().toPath().relativize(file.getCanonicalFile().toPath()).toString().replace('\\','/');}catch(IOException e){throw new IllegalArgumentException("Chemin photo invalide",e);}}
    static String album(Context c,File file) {String id=id(c,file);if(!id.startsWith("Albums/"))return "default";String[] parts=id.split("/");return parts.length==3?parts[1]:"default";}
    static File resolve(Context c,String id) throws IOException {
        File root=directory(c).getCanonicalFile(),file=new File(root,id).getCanonicalFile();
        if(!file.getPath().startsWith(root.getPath()+"/")||!isPhoto(file))throw new IOException("Photo introuvable");
        if(!albums(c).containsKey(album(c,file)))throw new IOException("Photo introuvable");return file;
    }
    static List<File> playlist(Context c) {
        String chosen=prefs(c).getString("active_album","all");Set<String> excluded=prefs(c).getStringSet("excluded_photos",Collections.emptySet());
        List<File> result=photos(c);result.removeIf(file->(!chosen.equals("all")&&!album(c,file).equals(chosen))||excluded.contains(id(c,file)));
        Set<String> seen=new HashSet<>();result.removeIf(file->{try{return !seen.add(PhotoFingerprint.of(file));}catch(IOException e){return false;}});return result;
    }
    static File duplicate(Context c,File candidate)throws IOException{
        String hash=PhotoFingerprint.of(candidate);for(File existing:photos(c))if(PhotoFingerprint.of(existing).equals(hash))return existing;return null;
    }
    private static JSONObject names(Context c){try{return new JSONObject(prefs(c).getString("photo_names","{}"));}catch(Exception e){return new JSONObject();}}
    static synchronized String label(Context c,File file){return names(c).optString(id(c,file),file.getName());}
    static synchronized void name(Context c,File file,String label)throws Exception{
        if(label.trim().isEmpty()||label.length()>160)throw new IOException("Nom de photo invalide");JSONObject names=names(c);names.put(id(c,file),label.trim());prefs(c).edit().putString("photo_names",names.toString()).apply();
    }
    static synchronized void photoAction(Context c,JSONObject request)throws Exception{
        String action=request.getString("action");JSONArray ids=request.getJSONArray("ids");if(ids.length()>1000)throw new IOException("Sélection trop grande");
        List<File> files=new ArrayList<>();for(int i=0;i<ids.length();i++)files.add(resolve(c,ids.getString(i)));
        Set<String> excluded=new HashSet<>(prefs(c).getStringSet("excluded_photos",Collections.emptySet()));JSONObject labels=names(c);
        File destination=action.equals("move")?albumDirectory(c,request.getString("album")):null;
        if(!Arrays.asList("move","delete","include","exclude","rename").contains(action))throw new IOException("Action inconnue");
        if(action.equals("rename")&&files.size()!=1)throw new IOException("Sélectionnez une seule photo");
        for(File file:files){String old=id(c,file);
            if(action.equals("include"))excluded.remove(old);
            else if(action.equals("exclude"))excluded.add(old);
            else if(action.equals("rename")){name(c,file,request.getString("name"));labels=names(c);}
            else if(action.equals("delete")){if(!file.delete())throw new IOException("Suppression impossible");excluded.remove(old);labels.remove(old);}
            else if(!file.getParentFile().equals(destination)){
                File moved=new File(destination,file.getName());if(moved.exists())moved=new File(destination,UUID.randomUUID().toString()+"-"+file.getName());
                if(!file.renameTo(moved))throw new IOException("Déplacement impossible");String next=id(c,moved);
                if(excluded.remove(old))excluded.add(next);if(labels.has(old)){labels.put(next,labels.getString(old));labels.remove(old);}
            }
        }prefs(c).edit().putStringSet("excluded_photos",excluded).putString("photo_names",labels.toString()).apply();
    }
    static synchronized JSONObject state(Context c)throws Exception{
        JSONObject result=new JSONObject();JSONArray folders=new JSONArray(),images=new JSONArray();List<File> files=photos(c);Set<String> excluded=prefs(c).getStringSet("excluded_photos",Collections.emptySet());
        for(Map.Entry<String,String> entry:albums(c).entrySet()){int count=0;for(File file:files)if(album(c,file).equals(entry.getKey()))count++;folders.put(new JSONObject().put("id",entry.getKey()).put("name",entry.getValue()).put("count",count));}
        Map<String,String> originals=new HashMap<>();int duplicates=0;
        for(File file:files){String hash=PhotoFingerprint.of(file),original=originals.get(hash);if(original==null)originals.put(hash,id(c,file));else duplicates++;
            images.put(new JSONObject().put("id",id(c,file)).put("name",label(c,file)).put("album",album(c,file)).put("enabled",!excluded.contains(id(c,file))).put("size",file.length()).put("modified",file.lastModified()).put("content_key",hash).put("duplicate_of",original==null?"":original));}
        JSONObject settings=new JSONObject();SharedPreferences p=prefs(c);
        settings.put("active_album",p.getString("active_album","all")).put("style",p.getString("style","panels")).put("idle_minutes",p.getInt("idle_minutes",5))
            .put("interval_seconds",p.getInt("interval_seconds",5)).put("fit",p.getString("fit","fill")).put("transition",p.getString("transition","fade"))
            .put("transition_ms",p.getInt("transition_ms",1000)).put("clock",p.getBoolean("clock",true)).put("shuffle",p.getBoolean("shuffle",false))
            .put("clock_size",p.getInt("clock_size",52)).put("clock_position",p.getString("clock_position","moving"));
        return result.put("albums",folders).put("photos",images).put("settings",settings).put("duplicate_count",duplicates).put("playlist_count",playlist(c).size())
            .put("styles",PhotoModes.json(PhotoModes.STYLES,PhotoModes.STYLE_NAMES)).put("transitions",PhotoModes.json(PhotoModes.TRANSITIONS,PhotoModes.TRANSITION_NAMES));
    }
    static synchronized void settings(Context c,JSONObject values)throws Exception{
        SharedPreferences.Editor edit=prefs(c).edit();for(String key:new String[]{"active_album","style","fit","transition","clock_position"})if(values.has(key)){
            String value=values.getString(key);boolean valid=key.equals("active_album")?(value.equals("all")||albums(c).containsKey(value)):
                key.equals("style")?Arrays.asList(PhotoModes.STYLES).contains(value):key.equals("transition")?Arrays.asList(PhotoModes.TRANSITIONS).contains(value):
                key.equals("fit")?Arrays.asList("fit","fill").contains(value):Arrays.asList("moving","top_left","top_right","bottom_left","bottom_right").contains(value);
            if(!valid)throw new IOException("Réglage invalide : "+key);edit.putString(key,value);
        }
        String[] ints={"idle_minutes","interval_seconds","transition_ms","clock_size"};int[] min={0,5,250,24},max={60,600,5000,90};
        for(int i=0;i<ints.length;i++)if(values.has(ints[i])){int n=values.getInt(ints[i]);if(n<min[i]||n>max[i])throw new IOException("Valeur hors limites");edit.putInt(ints[i],n);}
        for(String key:new String[]{"clock","shuffle"})if(values.has(key))edit.putBoolean(key,values.getBoolean(key));edit.apply();
    }
}
