package io.github.blk185.xiaomicleanhome;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class PhotoTransferActivity extends Activity {
    private PhotoUploadServer server;
    private TextView address,count,fingerprint;
    private android.app.AlertDialog pendingDialog;
    private android.app.Dialog previewDialog;
    private void preview(boolean show) {
        if(previewDialog!=null){previewDialog.dismiss();previewDialog=null;}
        if(!show||isFinishing()||server==null)return;
        PhotoScene scene=new PhotoScene(this);
        android.app.Dialog dialog=new android.app.Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(scene.view);dialog.setOnDismissListener(d->scene.stop());
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        previewDialog=dialog;dialog.show();scene.start();
        handler.postDelayed(()->{if(previewDialog==dialog){dialog.dismiss();previewDialog=null;}},90000);
    }
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable() {
        @Override public void run() { count.setText(PhotoStore.photos(PhotoTransferActivity.this).size()+" photo(s) · "+(server==null?"Fermé":server.security.status())); handler.postDelayed(this,1500); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setGravity(Gravity.CENTER); panel.setPadding(60,30,60,30); panel.setBackgroundColor(0xff101319);
        TextView title=new TextView(this); title.setText("Gestion sécurisée des photos et de la veille"); title.setTextSize(30); title.setTextColor(-1); panel.addView(title);
        TextView help=new TextView(this); help.setText("Ouvrez cette adresse sur votre PC ou téléphone connecté au même réseau :"); help.setTextSize(17); help.setTextColor(0xffcccccc); help.setPadding(0,35,0,20); panel.addView(help);
        address=new TextView(this); address.setTextSize(23); address.setTextColor(0xff93c5fd); panel.addView(address);
        fingerprint=new TextView(this);fingerprint.setTextSize(12);fingerprint.setTextColor(0xffbbbbbb);fingerprint.setPadding(0,15,0,0);panel.addView(fingerprint);
        android.widget.Button revoke=new android.widget.Button(this);revoke.setText("Déconnecter le navigateur / annuler la demande");revoke.setOnClickListener(v->{if(server!=null)server.security.revoke();if(pendingDialog!=null)pendingDialog.dismiss();});panel.addView(revoke);
        count=new TextView(this); count.setTextSize(18); count.setTextColor(-1); count.setPadding(0,30,0,20); panel.addView(count);
        TextView bottom=new TextView(this); bottom.setText("Vérifiez l’empreinte du certificat au premier accès.\nAutorisez uniquement le code affiché dans votre navigateur.\nRetour ferme le site et déconnecte tout le monde."); bottom.setTextSize(16); bottom.setTextColor(0xffbbbbbb); panel.addView(bottom);
        setContentView(panel);
    }
    @Override protected void onResume() {
        super.onResume();
        try { server=new PhotoUploadServer(this,(ip,code,allow,deny)->handler.post(()->{
            if(isFinishing()||server==null)return;
            pendingDialog=new android.app.AlertDialog.Builder(this).setTitle("Autoriser ce navigateur ?").setMessage("Adresse : "+ip+"\nCode : "+code+"\nVérifiez que ce code correspond à votre navigateur.")
                .setPositiveButton("Autoriser",(d,w)->allow.run()).setNegativeButton("Refuser",(d,w)->deny.run()).setOnCancelListener(d->deny.run()).create();pendingDialog.show();
            android.app.AlertDialog requestDialog=pendingDialog;
            handler.postDelayed(()->{if(pendingDialog==requestDialog)requestDialog.dismiss();},120000);
        }),show->handler.post(()->preview(show))); address.setText(server.url());fingerprint.setText("Certificat SHA-256 :\n"+server.security.fingerprint); }
        catch (Exception e) { address.setText("Transfert indisponible. Revenez en arrière et réessayez."); }
        handler.post(refresh);
    }
    @Override protected void onPause() {
        handler.removeCallbacksAndMessages(null);if(pendingDialog!=null)pendingDialog.dismiss();
        preview(false);
        if (server!=null) { server.close(); server=null; }
        super.onPause();
    }
}
