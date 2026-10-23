package io.github.blk185.xiaomicleanhome;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONObject;

/** Temporary, token-protected LAN upload endpoint, active only while the transfer screen is open. */
final class PhotoUploadServer implements AutoCloseable {
    private final Context context;
    private final ServerSocket server;
    final PhotoSecurity security;
    private String responseCookie="";
    private final java.util.function.Consumer<Boolean> preview;
    private volatile boolean closed;
    private volatile Socket active;
    PhotoUploadServer(Context context, PhotoSecurity.Approval approval, java.util.function.Consumer<Boolean> preview) throws Exception {
        this.context = context.getApplicationContext();
        this.preview=preview;
        security=new PhotoSecurity(approval); server=security.socket;
        Thread worker = new Thread(() -> {
            while (!closed) {
                try (Socket socket = server.accept()) { active=socket; handle(socket); }
                catch (Exception ignored) { }
                finally { active=null; }
            }
        }, "photo-upload");
        worker.setDaemon(true);
        worker.start();
    }
    String url() {
        try {
            for (NetworkInterface network : java.util.Collections.list(NetworkInterface.getNetworkInterfaces()))
                for (InetAddress ip : java.util.Collections.list(network.getInetAddresses()))
                    if (ip instanceof Inet4Address && ip.isSiteLocalAddress() && !ip.isLoopbackAddress())
                        return "https://" + ip.getHostAddress() + ":8765/";
        } catch (Exception ignored) { }
        return "Wi-Fi indisponible";
    }
    private String line(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int value; (value=input.read())!=-1;) {
            if (value=='\n') break;
            if (value!='\r') bytes.write(value);
            if (bytes.size()>8192) throw new IOException("Header too long");
        }
        return bytes.toString("ISO-8859-1");
    }
    private void handle(Socket socket) throws Exception {
        socket.setSoTimeout(15000);
        InputStream input = new BufferedInputStream(socket.getInputStream());
        String[] request = line(input).split(" ");
        if (request.length<2) return;
        String method=request[0], target=request[1];
        responseCookie="";
        int length=-1, total=0;
        java.util.Map<String,String> headers=new java.util.HashMap<>();
        String header;
        while (!(header=line(input)).isEmpty()) {
            int colon=header.indexOf(':');if(colon>0)headers.put(header.substring(0,colon).toLowerCase(java.util.Locale.ROOT),header.substring(colon+1).trim());
            total+=header.length();
            if (total>16384) return;
            if (header.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) length=Integer.parseInt(header.substring(15).trim());
        }
        String path=target.split("\\?",2)[0];
        Uri address=Uri.parse("http://box"+target);
        String remote=socket.getInetAddress().getHostAddress(), cookie="";
        for(String part:headers.getOrDefault("cookie","").split(";")){String v=part.trim();if(v.startsWith("__Host-PhotoSession="))cookie=v.substring(20);}
        String origin=headers.get("origin");
        if(method.equals("POST")&&(origin==null||!origin.equals(url().replaceAll("/$","")))){reply(socket,403,"text/plain","Origine refusée");return;}
        boolean authenticated=security.valid(cookie,remote);
        if(method.equals("POST")&&java.util.Arrays.asList("/api/pair","/api/pair/status","/api/logout","/api/preview","/api/preview/stop").contains(path)){
            if(length>4096){reply(socket,413,"text/plain","Requête trop grande");return;}
            for(int remaining=Math.max(0,length);remaining>0;){int n=input.read(new byte[Math.min(remaining,4096)]);if(n<0)throw new EOFException();remaining-=n;}
        }
        if(method.equals("GET")&&path.equals("/")){reply(socket,200,"text/html; charset=utf-8",page(authenticated?"photo-library.html":"photo-login.html"));return;}
        if(method.equals("POST")&&path.equals("/api/pair")){
            try{String[] pair=security.begin(remote).split(":");responseCookie=PhotoSecurity.cookie(pair[0]);reply(socket,200,"application/json",new JSONObject().put("code",pair[1]).toString());}
            catch(Exception e){reply(socket,409,"text/plain; charset=utf-8",e.getMessage());}return;
        }
        if(method.equals("POST")&&path.equals("/api/pair/status")){
            String status=security.poll(cookie,remote);if(!status.equals("pending")&&!status.equals("denied")){responseCookie=PhotoSecurity.cookie(status);status="approved";}
            reply(socket,200,"application/json",new JSONObject().put("status",status).toString());return;
        }
        if(!authenticated){reply(socket,401,"text/plain; charset=utf-8","Connexion non autorisée ou expirée");return;}
        if(method.equals("GET")&&path.equals("/api/session")){reply(socket,200,"application/json",new JSONObject().put("csrf",security.csrf()).toString());return;}
        if(method.equals("POST")&&!security.csrf(headers.get("x-csrf-token"))){reply(socket,403,"text/plain","Validation de requête manquante");return;}
        if(method.equals("POST")&&path.equals("/api/logout")){security.revoke();responseCookie=PhotoSecurity.cookie("")+"; Max-Age=0";reply(socket,200,"application/json","{}");return;}
        if(method.equals("POST")&&(path.equals("/api/preview")||path.equals("/api/preview/stop"))){preview.accept(path.equals("/api/preview"));reply(socket,200,"application/json","{}");return;}
        if(method.equals("GET")&&path.equals("/api/state")){
            reply(socket,200,"application/json; charset=utf-8",PhotoStore.state(context).toString());return;
        }
        if(method.equals("GET")&&path.equals("/photo")){
            try{
                File file=PhotoStore.resolve(context,address.getQueryParameter("id"));
                if("1".equals(address.getQueryParameter("thumb"))){
                    Bitmap bitmap=PhotoImages.decode(file,160000);if(bitmap==null)throw new IOException("Photo illisible");
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,80,bytes);bitmap.recycle();
                    replyBytes(socket,200,"image/jpeg",bytes.toByteArray());
                }else{
                    String name=file.getName().toLowerCase(java.util.Locale.ROOT);String mime=name.endsWith("png")?"image/png":name.endsWith("webp")?"image/webp":name.endsWith("heic")||name.endsWith("heif")?"image/heic":"image/jpeg";
                    header(socket,200,mime,file.length());try(InputStream fileInput=new FileInputStream(file)){byte[] buffer=new byte[32768];for(int n;(n=fileInput.read(buffer))!=-1;)socket.getOutputStream().write(buffer,0,n);}socket.getOutputStream().flush();
                }
            }catch(Exception e){reply(socket,404,"text/plain; charset=utf-8","Photo introuvable");}return;
        }
        if(method.equals("POST")&&path.startsWith("/api/")){
            if(length<1||length>256*1024){reply(socket,413,"text/plain","Requête trop grande");return;}
            try{
                byte[] bytes=new byte[length];for(int offset=0;offset<length;){int n=input.read(bytes,offset,length-offset);if(n<0)throw new EOFException();offset+=n;}
                JSONObject data=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
                if(path.endsWith("/api/settings"))PhotoStore.settings(context,data);
                else if(path.endsWith("/api/photos"))PhotoStore.photoAction(context,data);
                else if(path.endsWith("/api/album")){
                    String action=data.getString("action");
                    if(action.equals("create"))PhotoStore.createAlbum(context,data.getString("name"));
                    else if(action.equals("rename"))PhotoStore.renameAlbum(context,data.getString("id"),data.getString("name"));
                    else if(action.equals("delete"))PhotoStore.deleteAlbum(context,data.getString("id"));
                    else throw new IOException("Action inconnue");
                }else throw new IOException("Page introuvable");
                reply(socket,200,"application/json; charset=utf-8",PhotoStore.state(context).toString());
            }catch(Exception e){reply(socket,400,"text/plain; charset=utf-8",e.getMessage()==null?"Opération impossible":e.getMessage());}return;
        }
        if (!method.equals("POST") || !path.equals("/upload")) {
            reply(socket,404,"text/plain","Page introuvable"); return;
        }
        if (length<1 || length>20*1024*1024 || PhotoStore.directory(context).getUsableSpace()<length+30L*1024*1024) {
            reply(socket,413,"text/plain","Photo trop volumineuse ou stockage insuffisant (20 Mo maximum)."); return;
        }
        File temp=File.createTempFile("upload-", ".tmp",PhotoStore.directory(context));
        try {
            try (OutputStream output=new FileOutputStream(temp)) {
                byte[] buffer=new byte[32768];
                for (int remaining=length;remaining>0;) {
                    int count=input.read(buffer,0,Math.min(buffer.length,remaining));
                    if (count<0) throw new EOFException();
                    output.write(buffer,0,count); remaining-=count;
                }
            }
            BitmapFactory.Options info=new BitmapFactory.Options(); info.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(temp.getPath(),info);
            if (info.outWidth<=0 || info.outHeight<=0 || info.outWidth>30000 || info.outHeight>30000) {
                reply(socket,415,"text/plain","Format photo non reconnu par la Xiaomi."); return;
            }
            int photoWidth=info.outWidth,photoHeight=info.outHeight;
            try{int orientation=new android.media.ExifInterface(temp.getPath()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);
                if(orientation>=5&&orientation<=8){int swap=photoWidth;photoWidth=photoHeight;photoHeight=swap;}
            }catch(Exception ignored){}
            if(!PhotoAspectRatio.isLandscape169(photoWidth,photoHeight)){
                reply(socket,415,"text/plain; charset=utf-8","Image refusée : seuls les wallpapers 16:9 en paysage sont acceptés (par exemple 1920 × 1080). Dimensions de cette image : "+photoWidth+" × "+photoHeight+". Aucun fichier n’a été ajouté.");return;
            }
            String mime=info.outMimeType==null?"":info.outMimeType;
            String extension=mime.equals("image/jpeg")?"jpg":mime.equals("image/png")?"png":mime.equals("image/webp")?"webp":mime.equals("image/heif")||mime.equals("image/heic")?"heic":null;
            if (extension==null) { reply(socket,415,"text/plain","Utilisez JPEG, PNG, WebP ou HEIC."); return; }
            File duplicate=PhotoStore.duplicate(context,temp);
            if(duplicate!=null){reply(socket,200,"application/json; charset=utf-8",new JSONObject().put("duplicate",true).put("id",PhotoStore.id(context,duplicate)).put("name",PhotoStore.label(context,duplicate)).put("count",PhotoStore.photos(context).size()).toString());return;}
            String label=address.getQueryParameter("name");if(label==null||label.isEmpty())label="photo";if(label.length()>160)label=label.substring(0,160);
            String original=label.replaceAll("[^A-Za-z0-9._-]","_");
            if (original.length()>70) original=original.substring(0,70);
            String name=original+"-"+UUID.randomUUID().toString().substring(0,8)+"."+extension;
            String album=address.getQueryParameter("album");if(album==null)album="default";
            File destination=new File(PhotoStore.albumDirectory(context,album),name);
            if (!temp.renameTo(destination)) throw new IOException("Save failed");
            PhotoStore.name(context,destination,label);
            reply(socket,200,"application/json",new JSONObject().put("saved",name).put("id",PhotoStore.id(context,destination)).put("count",PhotoStore.photos(context).size()).toString());
        } finally { temp.delete(); }
    }
    private void reply(Socket socket,int status,String type,String body) throws IOException {
        replyBytes(socket,status,type,body.getBytes(StandardCharsets.UTF_8));
    }
    private void replyBytes(Socket socket,int status,String type,byte[] bytes)throws IOException{
        header(socket,status,type,bytes.length);socket.getOutputStream().write(bytes);socket.getOutputStream().flush();
    }
    private void header(Socket socket,int status,String type,long length)throws IOException{
        OutputStream output=socket.getOutputStream();
        output.write(("HTTP/1.1 "+status+" Result\r\nContent-Type: "+type+"\r\nContent-Length: "+length+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nX-Frame-Options: DENY\r\nContent-Security-Policy: default-src 'self'; img-src 'self' blob:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'\r\n"+(responseCookie.isEmpty()?"":"Set-Cookie: "+responseCookie+"\r\n")+"\r\n").getBytes(StandardCharsets.US_ASCII));
    }
    private String page(String asset)throws IOException{
        try(InputStream input=context.getAssets().open(asset);ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];for(int n;(n=input.read(buffer))!=-1;)output.write(buffer,0,n);return output.toString("UTF-8");
        }
    }
    @Override public void close() {
        closed=true;security.revoke();
        try { server.close(); } catch (IOException ignored) { }
        try { if (active!=null) active.close(); } catch (IOException ignored) { }
    }
}
