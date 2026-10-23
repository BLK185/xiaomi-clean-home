package io.github.blk185.xiaomicleanhome;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.net.*;
import java.security.*;
import java.util.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
/** Device-local TLS key and in-memory, TV-approved single browser session. */
final class PhotoSecurity {
    interface Approval { void ask(String ip,String code,Runnable allow,Runnable deny); }
    final SSLServerSocket socket;
    final String fingerprint;
    final Approval approval;
    private String pending="", session="", csrf="", ip="";
    private long pendingUntil,lastUsed,started,lastPair;
    private boolean approved;
    private final SecureRandom random=new SecureRandom();
    PhotoSecurity(Approval approval)throws Exception {
        this.approval=approval;
        KeyStore keys=KeyStore.getInstance("AndroidKeyStore");keys.load(null);
        String alias="photo_https_ec_v1";
        if(!keys.containsAlias(alias)){
            KeyPairGenerator generator=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(new java.security.spec.ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_NONE,KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA384,KeyProperties.DIGEST_SHA512)
                .setCertificateSubject(new X500Principal("CN=Xiaomi Photo Local"))
                .setCertificateNotBefore(new Date(System.currentTimeMillis()-86400000L))
                .setCertificateNotAfter(new Date(System.currentTimeMillis()+315360000000L)).build());generator.generateKeyPair();
        }
        if(keys.containsAlias("photo_https_v1"))keys.deleteEntry("photo_https_v1");
        StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(keys.getCertificate(alias).getEncoded())){if(hash.length()>0)hash.append(':');hash.append(String.format(Locale.ROOT,"%02X",b&255));}fingerprint=hash.toString();
        KeyManagerFactory managers=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());managers.init(keys,null);
        SSLContext tls=SSLContext.getInstance("TLS");tls.init(managers.getKeyManagers(),null,random);
        socket=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket(8765,16,InetAddress.getByName("0.0.0.0"));
        List<String> protocols=new ArrayList<>();for(String p:socket.getSupportedProtocols())if(p.equals("TLSv1.2")||p.equals("TLSv1.3"))protocols.add(p);socket.setEnabledProtocols(protocols.toArray(new String[0]));
    }
    private String nonce(){byte[] b=new byte[32];random.nextBytes(b);return android.util.Base64.encodeToString(b,android.util.Base64.NO_WRAP|android.util.Base64.URL_SAFE|android.util.Base64.NO_PADDING);}
    private boolean same(String a,String b){return a!=null&&b!=null&&MessageDigest.isEqual(a.getBytes(java.nio.charset.StandardCharsets.UTF_8),b.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private void expire(){long now=android.os.SystemClock.elapsedRealtime();if(!session.isEmpty()&&(now-lastUsed>900000||now-started>7200000))revoke();if(now>pendingUntil){pending="";approved=false;}}
    synchronized boolean valid(String cookie,String remote){expire();if(!session.isEmpty()&&same(session,cookie)&&ip.equals(remote)){lastUsed=android.os.SystemClock.elapsedRealtime();return true;}return false;}
    synchronized boolean csrf(String value){return same(csrf,value)&&!csrf.isEmpty();}
    synchronized String csrf(){return csrf;}
    synchronized String begin(String remote)throws Exception {
        expire();long now=android.os.SystemClock.elapsedRealtime();if(!session.isEmpty()||!pending.isEmpty())throw new Exception("Une connexion est déjà active ou en attente. Déconnectez-la sur la TV.");
        if(now-lastPair<15000&&lastPair!=0)throw new Exception("Patientez 15 secondes avant une nouvelle demande.");lastPair=now;
        pending=nonce();ip=remote;pendingUntil=now+120000;approved=false;String ticket=pending;String code=String.format(Locale.ROOT,"%06d",random.nextInt(1000000));
        approval.ask(remote,code,()->approve(ticket),()->deny(ticket));return ticket+":"+code;
    }
    private synchronized void approve(String ticket){expire();if(same(pending,ticket)&&!pending.isEmpty())approved=true;}
    private synchronized void deny(String ticket){if(same(pending,ticket)){pending="";approved=false;}}
    synchronized String poll(String ticket,String remote){expire();if(!same(pending,ticket)||pending.isEmpty()||!ip.equals(remote))return "denied";if(!approved)return "pending";session=nonce();csrf=nonce();pending="";approved=false;started=lastUsed=android.os.SystemClock.elapsedRealtime();return session;}
    synchronized void revoke(){session="";pending="";csrf="";approved=false;}
    synchronized String status(){expire();return !session.isEmpty()?"Un navigateur connecté · "+ip:!pending.isEmpty()?"Validation en attente":"Aucun navigateur connecté";}
    static String cookie(String value){return "__Host-PhotoSession="+value+"; Path=/; Secure; HttpOnly; SameSite=Strict";}
}
