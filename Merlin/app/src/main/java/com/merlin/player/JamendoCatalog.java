package com.merlin.player;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Busca e streams autorizados pela API oficial Jamendo. */
public final class JamendoCatalog {
    private JamendoCatalog(){}
    private static final String API="https://api.jamendo.com/v3.0/tracks/";
    private static final String STREAM_PREFIX="https://api.jamendo.com/v3.0/tracks/file/";
    public static boolean configured(){
        return !BuildConfig.JAMENDO_CLIENT_ID.trim().isEmpty();
    }
    public static boolean canPlay(Track t){
        return t!=null && t.uri.startsWith(STREAM_PREFIX) &&
            t.uri.contains("action=stream") && configured();
    }
    public static List<Track> search(String term)throws Exception{
        ArrayList<Track> out=new ArrayList<>();
        if(!configured()||term==null||term.trim().length()<2)return out;
        String url=API+"?client_id="+URLEncoder.encode(BuildConfig.JAMENDO_CLIENT_ID,"UTF-8")+
            "&format=json&limit=25&type=single+albumtrack&audioformat=mp32&imagesize=300"+
            "&search="+URLEncoder.encode(term.trim(),"UTF-8");
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(9000);c.setReadTimeout(13000);
        try{
            if(c.getResponseCode()!=200)throw new Exception("Jamendo indisponível (HTTP "+c.getResponseCode()+")");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(BufferedInputStream in=new BufferedInputStream(c.getInputStream())){
                byte[] b=new byte[8192];int n;
                while((n=in.read(b))!=-1){
                    bytes.write(b,0,n);
                    if(bytes.size()>900000)throw new Exception("Resposta de busca excessiva");
                }
            }
            JSONObject response=new JSONObject(bytes.toString("UTF-8"));
            JSONObject head=response.optJSONObject("headers");
            if(head!=null&&!"success".equalsIgnoreCase(head.optString("status","success")))
                throw new Exception(head.optString("error_message","Erro na busca Jamendo"));
            JSONArray songs=response.optJSONArray("results");
            if(songs==null)return out;
            for(int i=0;i<songs.length();i++){
                JSONObject song=songs.optJSONObject(i);if(song==null)continue;
                String id=song.optString("id","");
                if(!id.matches("[0-9]{1,15}"))continue;
                String title=song.optString("name",""),artist=song.optString("artist_name","");
                if(title.isEmpty())continue;
                String stream=STREAM_PREFIX+"?client_id="+
                   URLEncoder.encode(BuildConfig.JAMENDO_CLIENT_ID,"UTF-8")+
                   "&id="+id+"&action=stream&audioformat=mp32";
                String art=song.optString("image","");
                if(!art.startsWith("https://"))art="";
                out.add(new Track(stream,title,artist,song.optString("album_name","Jamendo"),
                    Math.max(0,song.optLong("duration",0))*1000,art));
            }
        } finally{c.disconnect();}
        return out;
    }
}
