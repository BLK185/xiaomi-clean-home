package io.github.blk185.xiaomicleanhome;

import java.util.*;

/** Shared, platform-independent composition and timing for the sliding-panel renderer. */
final class SlidingPanelsLayout {
    static final class Cell {
        final int image;
        final float left,top,right,bottom;
        Cell(int image,float l,float t,float r,float b){this.image=image;left=l;top=t;right=r;bottom=b;}
    }
    static final class Plan {
        final List<Cell> cells;
        final int used,family;
        final double cost;
        Plan(List<Cell> cells,int used,int family,double cost){this.cells=cells;this.used=used;this.family=family;this.cost=cost;}
    }
    private static Plan best;
    private static Plan[] families;
    static synchronized Plan choose(float[] ratios,float screenRatio,int previousFamily){
        if(ratios.length==0)throw new IllegalArgumentException("No photographs");
        if(ratios.length==1)return new Plan(Collections.singletonList(new Cell(0,0,0,1,1)),1,0,0);
        best=null;families=new Plan[6];
        for(int count=2;count<=Math.min(4,ratios.length);count++){
            int[] order=new int[count];for(int i=0;i<count;i++)order[i]=i;
            permute(order,0,ratios,screenRatio,previousFamily);
        }
        int preferred=previousFamily<1?1:previousFamily%5+1;
        Plan varied=families[preferred];
        // A slideshow is also a composition: all-landscape libraries would otherwise remain
        // an unchanging 2x2 grid forever. Alternate the large panel within a bounded crop budget.
        return varied!=null&&varied.cost<=best.cost+.42?varied:best;
    }
    private static void permute(int[] order,int position,float[] ratios,float screenRatio,int previous){
        if(position==order.length){templates(order,ratios,screenRatio,previous);return;}
        for(int i=position;i<order.length;i++){int save=order[position];order[position]=order[i];order[i]=save;permute(order,position+1,ratios,screenRatio,previous);save=order[position];order[position]=order[i];order[i]=save;}
    }
    private static void templates(int[] order,float[] ratios,float screen,int previous){
        int n=order.length;
        // A large photograph plus a stack. Its mirror and rotation are separate compositions.
        for(int rotation=0;rotation<2;rotation++)for(int mirror=0;mirror<2;mirror++){
            float effective=rotation==0?screen:1/screen;
            for(int step=0;step<=20;step++){
                float split=.36f+step*.018f;
                List<Cell> cells=new ArrayList<>();cells.add(new Cell(order[0],0,0,split,1));
                // Distribute the stack height using each photo's aspect ratio, reducing cropping.
                float total=0;for(int i=1;i<n;i++)total+=rotation==0?1/ratios[order[i]]:ratios[order[i]];
                float y=0;
                for(int i=1;i<n;i++){float weight=rotation==0?1/ratios[order[i]]:ratios[order[i]];float next=i==n-1?1:y+weight/total;cells.add(new Cell(order[i],split,y,1,next));y=next;}
                List<Cell> transformed=new ArrayList<>();
                for(Cell c:cells){float l=mirror==0?c.left:1-c.right,r=mirror==0?c.right:1-c.left;
                    transformed.add(rotation==0?new Cell(c.image,l,c.top,r,c.bottom):new Cell(c.image,c.top,l,c.bottom,r));}
                score(transformed,ratios,screen,rotation*2+mirror+1,previous);
            }
        }
        if(n==4){
            // Two columns, each with its own split: never four arbitrarily cropped equal boxes.
            for(int step=0;step<=12;step++){
                float x=.35f+step*.025f;
                float y1=ratios[order[1]]/(ratios[order[0]]+ratios[order[1]]);
                float y2=ratios[order[3]]/(ratios[order[2]]+ratios[order[3]]);
                score(Arrays.asList(new Cell(order[0],0,0,x,y1),new Cell(order[1],0,y1,x,1),new Cell(order[2],x,0,1,y2),new Cell(order[3],x,y2,1,1)),ratios,screen,5,previous);
            }
        }
    }
    private static void score(List<Cell> cells,float[] ratios,float screen,int family,int previous){
        double cost=0;
        for(Cell c:cells){float width=c.right-c.left,height=c.bottom-c.top,area=width*height;
            if(width<.18f||height<.18f)return;
            float ratio=screen*width/height,retained=Math.min(ratio/ratios[c.image],ratios[c.image]/ratio);
            // Losing a subject to an extreme crop costs much more than showing fewer photographs.
            cost+=area*(1-retained);if(retained<.7f)cost+=area*(.7f-retained)*3;
        }
        cost+=(4-cells.size())*.045;
        Plan candidate=new Plan(cells,cells.size(),family,cost);
        if(families[family]==null||cost<families[family].cost)families[family]=candidate;
        if(best==null||cost<best.cost)best=candidate;
    }
    static float progress(boolean incoming,int index,int count,long elapsed,long duration){
        float t=elapsed/(float)Math.max(1,duration);
        float delay=index*.035f;
        float p=Math.max(0,Math.min(1,(t-(incoming?.48f:0)-delay)/.40f));
        return incoming?1-(1-p)*(1-p)*(1-p):p*p*p;
    }
    static int direction(int family,int index,boolean incoming){
        int[] directions={0,2,1,3};return directions[(index+family+(incoming?1:0))%4];
    }
}
