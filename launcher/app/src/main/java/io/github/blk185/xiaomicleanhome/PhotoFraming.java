package io.github.blk185.xiaomicleanhome;

/** One fixed centre for every renderer, in both contain and cover modes. */
final class PhotoFraming {
    static void place(float imageWidth,float imageHeight,float left,float top,float right,float bottom,boolean fill,float zoom,float[] out){
        float sx=(right-left)/imageWidth,sy=(bottom-top)/imageHeight;
        float scale=(fill?Math.max(sx,sy):Math.min(sx,sy))*zoom;
        float width=imageWidth*scale,height=imageHeight*scale;
        float cx=(left+right)/2,cy=(top+bottom)/2;
        out[0]=cx-width/2;out[1]=cy-height/2;out[2]=cx+width/2;out[3]=cy+height/2;
    }
}
