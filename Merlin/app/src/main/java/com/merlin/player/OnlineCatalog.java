package com.merlin.player;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Descoberta online: resultados públicos da iTunes Search API.
 * Este módulo retorna somente metadados promocionais, sem baixar ou tocar
 * áudio protegido. O usuário escolhe o serviço de reprodução autorizado.
 */
public final class OnlineCatalog {
    private OnlineCatalog() {}

    public static final class Song {
        public final String title, artist, album, art, storeUrl;
        public Song(String title, String artist, String album, String art, String storeUrl) {
            this.title=title; this.artist=artist; this.album=album;
            this.art=art; this.storeUrl=storeUrl;
        }
    }
    public static List<Song> search(String term) throws Exception {
        ArrayList<Song> result=new ArrayList<>();
        if(term==null || term.trim().length()<2) return result;
        String query=URLEncoder.encode(term.trim(),"UTF-8");
        String endpoint="https://itunes.apple.com/search?country=BR&media=music&entity=song&limit=24&term="+query;
        HttpURLConnection conn=(HttpURLConnection)new URL(endpoint).openConnection();
        conn.setRequestProperty("User-Agent","MerlinMusicPlayer/1.2");
        conn.setConnectTimeout(7000);
        conn.setReadTimeout(7000);
        try {
            if(conn.getResponseCode()!=200) throw new Exception("Serviço de busca indisponível");
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try(BufferedInputStream input=new BufferedInputStream(conn.getInputStream())){
                byte[] bytes=new byte[4096];int n;
                while((n=input.read(bytes))!=-1 && buffer.size()<512000)buffer.write(bytes,0,n);
            }
            JSONObject json=new JSONObject(buffer.toString("UTF-8"));
            JSONArray items=json.optJSONArray("results");
            if(items!=null)for(int i=0;i<items.length();i++){
                JSONObject item=items.optJSONObject(i);
                if(item==null)continue;
                String title=item.optString("trackName","");
                String artist=item.optString("artistName","");
                if(title.isEmpty())continue;
                result.add(new Song(title,artist,item.optString("collectionName",""),
                    item.optString("artworkUrl100",""), item.optString("trackViewUrl",item.optString("collectionViewUrl",""))));
            }
        } finally {conn.disconnect();}
        return result;
    }
    public static Bitmap thumbnail(String url) {
        if(url==null || !url.startsWith("https://"))return null;
        HttpURLConnection conn=null;
        try {
            conn=(HttpURLConnection)new URL(url).openConnection();
            conn.setConnectTimeout(5000);conn.setReadTimeout(5000);
            if(conn.getResponseCode()!=200)return null;
            BitmapFactory.Options opts=new BitmapFactory.Options();
            opts.inSampleSize=1;
            try(BufferedInputStream input=new BufferedInputStream(conn.getInputStream())) {
                return BitmapFactory.decodeStream(input,null,opts);
            }
        }catch(Exception ignored){return null;}finally{if(conn!=null)conn.disconnect();}
    }
}
