package io.github.blk185.xiaomicleanhome;
import org.json.JSONArray;
import org.json.JSONObject;
final class PhotoModes {
    static final String[] STYLES={"classic","kenburns","panels","mosaic","tiles","wall","floating","mobile","reflections","prints","origami","flip"};
    static final String[] STYLE_NAMES={"Classique","Ken Burns — panoramique et zoom","Panneaux coulissants","Mosaïque","Mosaïque glissante","Mur de photos","Photos flottantes","Mobile de photos","Reflets","Tirages photo","Pliage — inspiration Origami","Retournement"};
    static final String[] TRANSITIONS={"fade","slide","slide_right","slide_up","slide_down","zoom","flip","curtain","none"};
    static final String[] TRANSITION_NAMES={"Fondu","Glissement vers la gauche","Glissement vers la droite","Glissement vers le haut","Glissement vers le bas","Zoom","Retournement","Rideau","Aucune"};
    static JSONArray json(String[] values,String[] names)throws Exception{JSONArray array=new JSONArray();for(int i=0;i<values.length;i++)array.put(new JSONObject().put("id",values[i]).put("name",names[i]));return array;}
}
