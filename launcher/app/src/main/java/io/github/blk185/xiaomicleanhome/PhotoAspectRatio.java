package io.github.blk185.xiaomicleanhome;

final class PhotoAspectRatio {
    static boolean isLandscape169(int width,int height){
        // Allow one rounded pixel, e.g. 854x480 or 1332x749; never accept 16:10 or portrait.
        return width>=16&&height>=9&&width>height&&Math.abs(width*9L-height*16L)<=9;
    }
}
