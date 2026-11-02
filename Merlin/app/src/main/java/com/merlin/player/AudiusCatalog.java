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

/**
 * Catálogo Audius: descoberta e streaming legal no player Android.
 * Não hospeda nem extrai faixas de serviços fechados.
 * Read-only: app_name; registrar chave de API se o provedor exigir.
 */
public final class AudiusCatalog {
    private AudiusCatalog(){}
    public static final String BASE="https://api.audius.co/v1";
    public static final String APP_NAME="Merlin";
    public static boolean canPlay(Track track) {
        return track!=null && track.uri.startsWith(BASE+"/tracks/")
            && track.uri.contains("/stream?");
    }
    public static List<Track> search(String term)throws Exception {
        ArrayList<Track> result=new ArrayList<>();
        if(term==null||term.trim().length()<2)return result;
        String url=BASE+"/tracks/search?query="+URLEncoder.encode(term.trim(),"UTF-8")
            +"&limit=25&app_name="+APP_NAME;
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(9000);conn.setReadTimeout(11000);
        conn.setRequestProperty("Accept","application/json");
        try{
            int status=conn.getResponseCode();
            if(status==401||status==403)throw new Exception("Audius requer chave de API para esta consulta.");
            if(status==429)throw new Exception("Muitas pesquisas; tente novamente.");
            if(status!=200)throw new Exception("Audius indisponível (HTTP "+status+").");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(BufferedInputStream input=new BufferedInputStream(conn.getInputStream())){
                byte[] b=new byte[8192];int n;
                while((n=input.read(b))!=-1){
                    bytes.write(b,0,n);
                    if(bytes.size()>700000)throw new Exception("Resposta muito grande.");
                }
            }
            JSONObject root=new JSONObject(bytes.toString("UTF-8"));
            JSONArray tracks=root.optJSONArray("data");
            if(tracks==null)return result;
            for(int i=0;i<tracks.length();i++){
                JSONObject song=tracks.optJSONObject(i);
                if(song==null)continue;
                String id=song.optString("id","");
                if(!id.matches("[A-Za-z0-9_-]{3,90}"))continue;
                if(song.has("is_streamable")&&!song.optBoolean("is_streamable",false))continue;
                JSONObject access=song.optJSONObject("access");
                if(access!=null && access.has("stream") && !access.optBoolean("stream",false))continue;
                String title=song.optString("title","Música");
                JSONObject artist=song.optJSONObject("user");
                String artistName=artist==null?"Artista Audius":artist.optString("name","Artista Audius");
                long duration=Math.max(0,song.optLong("duration",0))*1000;
                String artwork="";
                JSONObject image=song.optJSONObject("artwork");
                if(image!=null) artwork=image.optString("480x480",image.optString("150x150",""));
                if(!artwork.startsWith("https://"))artwork="";
                String stream=BASE+"/tracks/"+id+"/stream?app_name="+APP_NAME;
                result.add(new Track(stream,title,artistName,"Audius",duration,artwork));
            }
        } finally{conn.disconnect();}
        return result;
    }
}
