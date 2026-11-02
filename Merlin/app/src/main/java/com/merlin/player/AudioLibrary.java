package com.merlin.player;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public class AudioLibrary {
    public static final String PREFS = "merlin_library";
    private static final String IMPORTS = "imports";
    private static final String QUEUE = "queue";

    public static List<Track> getTracks(Context ctx) {
        ArrayList<Track> tracks = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        ContentResolver r = ctx.getContentResolver();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] cols = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION};
        try (Cursor c = r.query(collection, cols, null, null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (c != null) {
                while (c.moveToNext()) {
                    Uri uri = ContentUris.withAppendedId(collection, c.getLong(0));
                    String id = uri.toString();
                    if (seen.add(id)) tracks.add(new Track(id, c.getString(1), c.getString(2), c.getString(3), c.getLong(4)));
                }
            }
        } catch (Exception ignored) { }
        try {
            JSONArray arr = new JSONArray(ctx.getSharedPreferences(PREFS, 0).getString(IMPORTS, "[]"));
            for (int i=0; i<arr.length(); i++) {
                Track track = Track.fromJson(arr.getJSONObject(i));
                if (track.uri.length() > 0 && seen.add(track.uri)) tracks.add(track);
            }
        } catch (Exception ignored) { }
        // Faixas reproduzidas no Audius ficam disponíveis na biblioteca, sem arquivos pirateados.
        try{
            JSONArray online=new JSONArray(ctx.getSharedPreferences(PREFS,0).getString("online_library","[]"));
            for(int i=0;i<online.length();i++){
                Track remote=Track.fromJson(online.getJSONObject(i));
                if((AudiusCatalog.canPlay(remote)||JamendoCatalog.canPlay(remote))&&seen.add(remote.uri))tracks.add(remote);
            }
        }catch(Exception ignored){}
        tracks.sort(Comparator.comparing(t -> t.title.toLowerCase(java.util.Locale.getDefault())));
        return tracks;
    }

    public static void addImport(Context ctx, Uri uri) {
        String name = "Música importada";
        try (Cursor c = ctx.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) { }
        if (name != null) name = name.replaceFirst("(?i)\\.(mp3|m4a|aac|ogg|opus|flac|wav|wma)$", "");
        String artist = "Artista desconhecido";
        String title = name;
        long duration = 0;
        MediaMetadataRetriever md = new MediaMetadataRetriever();
        try {
            md.setDataSource(ctx, uri);
            String t = md.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String a = md.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String d = md.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (t != null && !t.isEmpty()) title=t;
            if (a != null && !a.isEmpty()) artist=a;
            if (d != null) duration=Long.parseLong(d);
        } catch (Exception ignored) { } finally { try { md.release(); } catch(Exception ignored){} }
        Track track = new Track(uri.toString(), title, artist, "Importadas", duration);
        SharedPreferences p = ctx.getSharedPreferences(PREFS, 0);
        try {
            JSONArray old = new JSONArray(p.getString(IMPORTS,"[]"));
            JSONArray next = new JSONArray();
            boolean exists = false;
            for (int i=0;i<old.length();i++) {
                JSONObject item=old.getJSONObject(i);
                if (track.uri.equals(item.optString("uri"))) exists=true;
                next.put(item);
            }
            if (!exists) next.put(track.toJson());
            p.edit().putString(IMPORTS,next.toString()).apply();
        } catch (Exception ignored) { }
    }

    public static void saveQueue(Context ctx, List<Track> queue) {
        JSONArray arr = new JSONArray();
        for(Track track : queue) arr.put(track.toJson());
        ctx.getSharedPreferences(PREFS, 0).edit().putString(QUEUE,arr.toString()).apply();
    }

    public static List<Track> loadQueue(Context ctx) {
        ArrayList<Track> tracks = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(ctx.getSharedPreferences(PREFS,0).getString(QUEUE,"[]"));
            for(int i=0;i<arr.length();i++) tracks.add(Track.fromJson(arr.getJSONObject(i)));
        } catch(Exception ignored) {}
        return tracks;
    }

    public static HashSet<String> favorites(Context ctx) {
        return new HashSet<>(ctx.getSharedPreferences(PREFS,0).getStringSet("favorites",new HashSet<>()));
    }

    public static boolean toggleFavorite(Context ctx, String uri) {
        HashSet<String> favorites = favorites(ctx);
        boolean added;
        if(favorites.contains(uri)) { favorites.remove(uri); added = false; }
        else {favorites.add(uri); added = true; }
        ctx.getSharedPreferences(PREFS,0).edit().putStringSet("favorites", favorites).apply();
        return added;
    }

    public static ArrayList<String> playlistNames(Context ctx) {
        ArrayList<String> names = new ArrayList<>();
        try {
            JSONObject obj = new JSONObject(ctx.getSharedPreferences(PREFS,0).getString("playlists", "{}"));
            java.util.Iterator<String> keys = obj.keys();
            while(keys.hasNext()) names.add(keys.next());
            names.sort(String.CASE_INSENSITIVE_ORDER);
        } catch(Exception ignored) {}
        return names;
    }

    public static void createPlaylist(Context ctx, String name) {
        if(name == null || name.trim().isEmpty()) return;
        name=name.trim();
        try {
            SharedPreferences p=ctx.getSharedPreferences(PREFS,0);
            JSONObject obj=new JSONObject(p.getString("playlists","{}"));
            if(!obj.has(name)) obj.put(name,new JSONArray());
            p.edit().putString("playlists",obj.toString()).apply();
        } catch(Exception ignored) {}
    }

    public static HashSet<String> playlistUris(Context ctx, String name) {
        HashSet<String> result = new HashSet<>();
        try {
            JSONObject obj=new JSONObject(ctx.getSharedPreferences(PREFS,0).getString("playlists","{}"));
            JSONArray arr=obj.optJSONArray(name);
            if(arr!=null) for(int i=0;i<arr.length();i++) result.add(arr.optString(i));
        } catch(Exception ignored) {}
        return result;
    }

    public static void togglePlaylistTrack(Context ctx, String name, String uri) {
        try {
            SharedPreferences p=ctx.getSharedPreferences(PREFS,0);
            JSONObject obj=new JSONObject(p.getString("playlists","{}"));
            HashSet<String> set=playlistUris(ctx,name);
            if(!set.add(uri)) set.remove(uri);
            JSONArray arr = new JSONArray();
            for(String entry:set) arr.put(entry);
            obj.put(name,arr);
            p.edit().putString("playlists",obj.toString()).apply();
        } catch(Exception ignored) {}
    }
    /** Renomeia a playlist e conserva suas músicas; falha em nomes duplicados. */
    public static synchronized boolean renamePlaylist(Context ctx,String oldName,String newName){
        if(oldName==null||newName==null)return false;
        newName=newName.trim();
        if(newName.length()<1||newName.length()>60||newName.equals("Todas")||newName.equals("Curtidas"))return false;
        SharedPreferences prefs=ctx.getSharedPreferences(PREFS,0);
        try{
            JSONObject obj=new JSONObject(prefs.getString("playlists","{}"));
            if(!obj.has(oldName)||(!oldName.equals(newName)&&obj.has(newName)))return false;
            if(oldName.equals(newName))return true;
            JSONArray songs=obj.getJSONArray(oldName);
            obj.remove(oldName);obj.put(newName,songs);
            return prefs.edit().putString("playlists",obj.toString()).commit();
        }catch(Exception ignored){return false;}
    }
    /** Exclui somente a playlist, nunca os arquivos de áudio. */
    public static synchronized boolean deletePlaylist(Context ctx,String name){
        SharedPreferences prefs=ctx.getSharedPreferences(PREFS,0);
        try{
            JSONObject obj=new JSONObject(prefs.getString("playlists","{}"));
            if(!obj.has(name))return false;
            obj.remove(name);
            return prefs.edit().putString("playlists",obj.toString()).commit();
        }catch(Exception ignored){return false;}
    }

    public static synchronized void rememberOnline(Context ctx,Track track){
        if(!AudiusCatalog.canPlay(track)&&!JamendoCatalog.canPlay(track))return;
        SharedPreferences p=ctx.getSharedPreferences(PREFS,0);
        try{
            JSONArray prev=new JSONArray(p.getString("online_library","[]"));
            JSONArray next=new JSONArray();next.put(track.toJson());
            for(int i=0;i<prev.length()&&next.length()<200;i++){
                JSONObject item=prev.getJSONObject(i);
                if(!track.uri.equals(item.optString("uri")))next.put(item);
            }
            p.edit().putString("online_library",next.toString()).apply();
        }catch(Exception ignored){}
    }

}
