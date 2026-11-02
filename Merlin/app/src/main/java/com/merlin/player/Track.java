package com.merlin.player;

import org.json.JSONObject;

public class Track {
    public final String uri;
    public final String title;
    public final String artist;
    public final String album;
    public final long duration;

    public Track(String uri, String title, String artist, String album, long duration) {
        this.uri = uri;
        this.title = title == null || title.trim().isEmpty() ? "Faixa desconhecida" : title;
        this.artist = artist == null || artist.trim().isEmpty() || artist.equals("<unknown>") ? "Artista desconhecido" : artist;
        this.album = album == null ? "" : album;
        this.duration = Math.max(0, duration);
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("uri", uri);
            o.put("title", title);
            o.put("artist", artist);
            o.put("album", album);
            o.put("duration", duration);
        } catch (Exception ignored) { }
        return o;
    }

    public static Track fromJson(JSONObject o) {
        return new Track(o.optString("uri"), o.optString("title"), o.optString("artist"), o.optString("album"), o.optLong("duration"));
    }
}
