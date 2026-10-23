package io.github.blk185.xiaomicleanhome;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import java.io.File;
final class PhotoImages {
    static Bitmap decode(File file,int maxPixels)throws Exception{
        BitmapFactory.Options info=new BitmapFactory.Options();info.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),info);if(info.outWidth<1||info.outHeight<1)return null;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while((long)(info.outWidth/options.inSampleSize)*(info.outHeight/options.inSampleSize)>maxPixels)options.inSampleSize*=2;
        Bitmap image=BitmapFactory.decodeFile(file.getPath(),options);if(image==null)return null;Matrix matrix=new Matrix();
        try{int orientation=new ExifInterface(file.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);switch(orientation){case 2:matrix.setScale(-1,1);break;case 3:matrix.setRotate(180);break;case 4:matrix.setScale(1,-1);break;case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;case 6:matrix.setRotate(90);break;case 7:matrix.setRotate(270);matrix.postScale(-1,1);break;case 8:matrix.setRotate(270);break;default:break;}}catch(Exception ignored){}
        if(!matrix.isIdentity()){Bitmap rotated=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),matrix,true);if(rotated!=image)image.recycle();image=rotated;}return image;
    }
}
