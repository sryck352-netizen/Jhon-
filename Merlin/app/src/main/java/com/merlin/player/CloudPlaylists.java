package com.merlin.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import org.json.JSONObject;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Integração real com Firebase Authentication anônimo + Realtime Database REST.
 *
 * Requer projeto Firebase configurado pelo proprietário. Nenhum endpoint
 * fictício é usado: sem configuração, a UI explica o que falta.
 * Faixas sincronizadas são METADADOS, jamais URLs locais dos celulares.
 */
public final class CloudPlaylists {
    public interface Done<T> { void complete(T value, String error); }
    private interface Operation<T> { T execute() throws Exception; }

    public static final String COLLAB="collab";
    public static final String MATCH="match";
    private final Context context;
    private final SharedPreferences prefs;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final SecureRandom rng=new SecureRandom();
    private final String apiKey=BuildConfig.FIREBASE_API_KEY;
    private final String database=BuildConfig.FIREBASE_DATABASE_URL;
    private String uid="",idToken="",refreshToken="";
    private long tokenExpires=0;

    public static final class Room {
        public final String id, name, kind, owner;
        public final JSONObject members, songs, tastes;
        public Room(String id,JSONObject source) {
            this.id=id;
            JSONObject meta=source.optJSONObject("meta");
            if(meta==null) meta=new JSONObject();
            name=meta.optString("name","Playlist sem nome");
            kind=meta.optString("type",COLLAB);
            owner=meta.optString("owner","");
            members=source.optJSONObject("members")==null?new JSONObject():source.optJSONObject("members");
            songs=source.optJSONObject("tracks")==null?new JSONObject():source.optJSONObject("tracks");
            tastes=source.optJSONObject("tastes")==null?new JSONObject():source.optJSONObject("tastes");
        }
        public int participants(){return members.length();}
        public int songCount(){return songs.length();}
    }
    public CloudPlaylists(Context ctx){
        context=ctx.getApplicationContext();
        prefs=context.getSharedPreferences("merlin_cloud",Context.MODE_PRIVATE);
        uid=prefs.getString("uid","");
        idToken=prefs.getString("id_token","");
        refreshToken=prefs.getString("refresh_token","");
        tokenExpires=prefs.getLong("expires_at",0);
    }
    public boolean configured(){
        return apiKey!=null&&!apiKey.isEmpty()&&database!=null
            &&database.startsWith("https://")&&database.contains("firebase");
    }
    public String userId(){return uid;}
    public String nickname(){return prefs.getString("nickname","Ouvinte Merlin");}
    public void setNickname(String nickname){
        String name=nickname==null?"":nickname.trim();
        if(name.length()>32)name=name.substring(0,32);
        if(name.length()>0)prefs.edit().putString("nickname",name).apply();
    }
    private <T> void run(Operation<T> action,Done<T> done){
        io.execute(()->{
            T result=null;String error=null;
            try{
                if(!configured())throw new Exception("Configure Firebase API Key e Database URL para usar playlists compartilhadas.");
                result=action.execute();
            }catch(Exception e){error=e.getMessage()==null?"Não foi possível conectar.":e.getMessage();}
            final T value=result;final String message=error;
            main.post(()->done.complete(value,message));
        });
    }
    private static String urlEncode(String data)throws Exception{
        return URLEncoder.encode(data,"UTF-8");
    }
    private static String read(InputStream input)throws Exception{
        if(input==null)return "";
        try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int count;
            while((count=in.read(buffer))!=-1){
                out.write(buffer,0,count);
                if(out.size()>3_000_000)throw new Exception("Resposta do servidor excessivamente grande");
            }
            return out.toString("UTF-8");
        }
    }
    private static String request(String endpoint,String method,String json,String type)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL(endpoint).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(12000);connection.setReadTimeout(12000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept","application/json");
        if(json!=null){
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type",type==null?"application/json; charset=utf-8":type);
            byte[] b=json.getBytes(StandardCharsets.UTF_8);
            try(OutputStream out=connection.getOutputStream()){out.write(b);}
        }
        try{
            int status=connection.getResponseCode();
            String text=read(status>=200&&status<300?connection.getInputStream():connection.getErrorStream());
            if(status<200||status>=300){
                String details=text;
                try{
                    JSONObject err=new JSONObject(text).optJSONObject("error");
                    if(err!=null)details=err.optString("message",details);
                }catch(Exception ignored){}
                throw new Exception("Servidor: "+status+" ("+details.substring(0,Math.min(150,details.length()))+")");
            }
            return text;
        }finally{connection.disconnect();}
    }
    private synchronized void auth()throws Exception{
        if(System.currentTimeMillis()+120000<tokenExpires&&!idToken.isEmpty())return;
        String response;
        if(!refreshToken.isEmpty()){
            try{
                String payload="grant_type=refresh_token&refresh_token="+urlEncode(refreshToken);
                response=request("https://securetoken.googleapis.com/v1/token?key="+urlEncode(apiKey),
                    "POST",payload,"application/x-www-form-urlencoded");
                JSONObject r=new JSONObject(response);
                idToken=r.getString("id_token");
                refreshToken=r.getString("refresh_token");
                uid=r.getString("user_id");
                tokenExpires=System.currentTimeMillis()+Math.max(600, r.optLong("expires_in",3600))*1000;
                saveAuth();return;
            }catch(Exception ignored){refreshToken="";}
        }
        response=request("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key="+urlEncode(apiKey),
            "POST","{\"returnSecureToken\":true}",null);
        JSONObject r=new JSONObject(response);
        idToken=r.getString("idToken");
        refreshToken=r.getString("refreshToken");
        uid=r.getString("localId");
        tokenExpires=System.currentTimeMillis()+r.optLong("expiresIn",3600)*1000;
        saveAuth();
    }
    private void saveAuth(){
        prefs.edit().putString("id_token",idToken).putString("refresh_token",refreshToken)
            .putString("uid",uid).putLong("expires_at",tokenExpires).apply();
    }
    private String db(String path,String method,JSONObject data)throws Exception{
        auth();
        return request(database.replaceAll("/+$","")+"/"+path+".json?auth="+urlEncode(idToken),
            method,data==null?null:data.toString(),null);
    }
    private void delete(String path)throws Exception{db(path,"DELETE",null);}
    private JSONObject getObj(String path)throws Exception{
        String payload=db(path,"GET",null);
        if(payload==null||payload.trim().equals("null"))return new JSONObject();
        return new JSONObject(payload);
    }
    private static String randomCode(int bytes){
        byte[] data=new byte[bytes];new SecureRandom().nextBytes(data);
        StringBuilder b=new StringBuilder();
        for(byte x:data)b.append(String.format(java.util.Locale.US,"%02x",x&255));
        return b.toString();
    }
    public static String fingerprint(String title,String artist){
        String raw=(title+"|"+artist).trim().toLowerCase(java.util.Locale.ROOT);
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder();
            for(int i=0;i<16;i++)hex.append(String.format(java.util.Locale.US,"%02x",digest[i]&255));
            return hex.toString();
        }catch(Exception e){return Integer.toHexString(raw.hashCode());}
    }
    public void createRoom(String name,String type,Done<Room> callback){
        run(()->{
            auth();
            if(!type.equals(COLLAB)&&!type.equals(MATCH))throw new Exception("Tipo inválido");
            String clean=name==null?"":name.trim();
            if(clean.length()<2||clean.length()>60)throw new Exception("Nome: use de 2 a 60 caracteres.");
            String id=randomCode(16);
            JSONObject meta=new JSONObject().put("owner",uid).put("name",clean)
                .put("type",type).put("createdAt",System.currentTimeMillis());
            db("rooms/"+id+"/meta","PUT",meta);
            JSONObject member=new JSONObject().put("name",nickname()).put("joinedAt",System.currentTimeMillis());
            db("rooms/"+id+"/members/"+uid,"PUT",member);
            remember(id);
            return new Room(id,getObj("rooms/"+id));
        },callback);
    }
    private void remember(String id){
        Set<String> rooms=new HashSet<>(prefs.getStringSet("rooms",Collections.emptySet()));
        rooms.add(id);prefs.edit().putStringSet("rooms",rooms).apply();
    }
    private void forget(String id){
        Set<String> rooms=new HashSet<>(prefs.getStringSet("rooms",Collections.emptySet()));
        rooms.remove(id);prefs.edit().putStringSet("rooms",rooms).apply();
    }
    public void listRooms(Done<List<Room>> callback){
        run(()->{
            auth();
            ArrayList<Room> result=new ArrayList<>();
            for(String id:prefs.getStringSet("rooms",Collections.emptySet())){
                try{
                    JSONObject obj=getObj("rooms/"+id);
                    if(obj.has("meta"))result.add(new Room(id,obj));
                }catch(Exception ignored){} // Pode ter sido removido ou o usuário saiu
            }
            result.sort((a,b)->a.name.compareToIgnoreCase(b.name));
            return result;
        },callback);
    }
    public void getRoom(String id,Done<Room> callback){
        run(()->{
            JSONObject room=getObj("rooms/"+id);
            if(!room.has("meta"))throw new Exception("Playlist não encontrada ou acesso negado.");
            return new Room(id,room);
        },callback);
    }
    public void invite(String roomId,Done<String> callback){
        run(()->{
            auth();
            JSONObject room=getObj("rooms/"+roomId);
            JSONObject meta=room.optJSONObject("meta");
            if(meta==null||!uid.equals(meta.optString("owner")))throw new Exception("Somente o criador pode gerar convites.");
            if(room.optJSONObject("members")!=null && room.getJSONObject("members").length()>=11)
                throw new Exception("Limite de 10 convidados atingido.");
            String code=randomCode(20);
            JSONObject inv=new JSONObject().put("room",roomId).put("createdBy",uid)
                .put("expires",System.currentTimeMillis()+7L*24*60*60*1000);
            db("invites/"+code,"PUT",inv);
            return "merlin://join?room="+roomId+"&code="+code;
        },callback);
    }
    public void join(String invitation,Done<Room> callback){
        run(()->{
            auth();
            String cleaned=invitation==null?"":invitation.trim();
            String roomId="",code="";
            if(cleaned.startsWith("merlin://")){
                Uri u=Uri.parse(cleaned);
                roomId=u.getQueryParameter("room");code=u.getQueryParameter("code");
            }else if(cleaned.contains(":")){
                String[] parts=cleaned.split(":",2);
                roomId=parts[0];code=parts[1];
            }
            if(roomId==null||code==null||!roomId.matches("[a-f0-9]{32}")||!code.matches("[a-f0-9]{40}"))
                throw new Exception("Convite inválido. Cole o link completo enviado pelo amigo.");
            JSONObject inv=getObj("invites/"+code);
            if(!roomId.equals(inv.optString("room"))||
               inv.optLong("expires",0)<System.currentTimeMillis())
                throw new Exception("Convite inválido ou expirado.");
            String assignedSlot="";
            for(int slot=1;slot<=10;slot++){
                try{
                    String selected=Integer.toString(slot);
                    JSONObject claim=new JSONObject().put("uid",uid).put("invite",code);
                    db("rooms/"+roomId+"/slots/"+selected,"PUT",claim);
                    assignedSlot=selected;break;
                }catch(Exception occupied){ /* Vaga ocupada por outro participante. */ }
            }
            if(assignedSlot.isEmpty())throw new Exception("Esta sala está cheia (10 convidados) ou o convite não é válido.");
            JSONObject member=new JSONObject().put("name",nickname())
                .put("joinedAt",System.currentTimeMillis()).put("invite",code)
                .put("slot",assignedSlot);
            db("rooms/"+roomId+"/members/"+uid,"PUT",member);
            remember(roomId);
            return new Room(roomId,getObj("rooms/"+roomId));
        },callback);
    }
    public void renameRoom(String roomId,String name,Done<Boolean> callback){
        run(()->{
            auth();Room current=new Room(roomId,getObj("rooms/"+roomId));
            if(!uid.equals(current.owner))throw new Exception("Apenas o criador pode renomear.");
            if(name==null||name.trim().length()<2||name.trim().length()>60)throw new Exception("Nome inválido.");
            JSONObject meta=new JSONObject().put("name",name.trim()).put("owner",uid)
                .put("type",current.kind).put("updatedAt",System.currentTimeMillis());
            db("rooms/"+roomId+"/meta","PUT",meta);return true;
        },callback);
    }
    public void removeRoom(String roomId,Done<Boolean> callback){
        run(()->{
            auth();Room room=new Room(roomId,getObj("rooms/"+roomId));
            if(uid.equals(room.owner))delete("rooms/"+roomId);
            else {
                JSONObject raw=getObj("rooms/"+roomId);
                JSONObject myMembers=raw.optJSONObject("members");
                JSONObject member=myMembers==null?null:myMembers.optJSONObject(uid);
                if(member!=null){
                    String slot=member.optString("slot","");
                    if(slot.matches("([1-9]|10)"))delete("rooms/"+roomId+"/slots/"+slot);
                }
                delete("rooms/"+roomId+"/members/"+uid);
            }
            forget(roomId);return true;
        },callback);
    }
    public void putSong(String roomId,Track track,Done<Boolean> callback){
        run(()->{
            JSONObject song=new JSONObject().put("title",track.title)
                .put("artist",track.artist).put("album",track.album)
                .put("addedBy",uid.isEmpty()?"":uid).put("addedAt",System.currentTimeMillis());
            String key=fingerprint(track.title,track.artist);
            db("rooms/"+roomId+"/tracks/"+key,"PUT",song);return true;
        },callback);
    }
    public void removeSong(String roomId,String hash,Done<Boolean> callback){
        run(()->{delete("rooms/"+roomId+"/tracks/"+hash);return true;},callback);
    }
    public void publishTastes(String roomId,List<Track> favorites,Done<Boolean> callback){
        run(()->{
            auth();
            JSONObject payload=new JSONObject();
            int count=0;
            for(Track track:favorites){
                if(count>=50)break;
                String hash=fingerprint(track.title,track.artist);
                payload.put(hash,new JSONObject().put("title",track.title).put("artist",track.artist));
                count++;
            }
            db("rooms/"+roomId+"/tastes/"+uid,"PUT",payload);
            return true;
        },callback);
    }
    public static List<JSONObject> recommendations(Room room,int max){
        class Candidate {
            String hash,title,artist;
            Set<String> fans=new HashSet<>();
        }
        HashMap<String,Candidate> pool=new HashMap<>();
        java.util.Iterator<String> users=room.tastes.keys();
        while(users.hasNext()){
            String user=users.next();
            JSONObject taste=room.tastes.optJSONObject(user);
            if(taste==null)continue;
            java.util.Iterator<String> tracks=taste.keys();
            while(tracks.hasNext()){
                String hash=tracks.next();
                JSONObject item=taste.optJSONObject(hash);
                if(item==null)continue;
                Candidate c=pool.get(hash);
                if(c==null){
                    c=new Candidate();c.hash=hash;c.title=item.optString("title");
                    c.artist=item.optString("artist");pool.put(hash,c);
                }
                c.fans.add(user);
            }
        }
        HashMap<String,Integer> artistPopularity=new HashMap<>();
        for(Candidate c:pool.values()){
            artistPopularity.put(c.artist,artistPopularity.getOrDefault(c.artist,0)+c.fans.size());
        }
        ArrayList<Candidate> ordered=new ArrayList<>(pool.values());
        // Equilíbrio: diversidade e preferências compartilhadas; votos não sobrescrevem gostos minoritários.
        ordered.sort((a,b)->{
            int scoreA=a.fans.size()*10+Math.min(5,artistPopularity.getOrDefault(a.artist,0));
            int scoreB=b.fans.size()*10+Math.min(5,artistPopularity.getOrDefault(b.artist,0));
            if(scoreA!=scoreB)return Integer.compare(scoreB,scoreA);
            return a.title.compareToIgnoreCase(b.title);
        });
        ArrayList<JSONObject> result=new ArrayList<>();
        HashMap<String,Integer> usedArtists=new HashMap<>();
        for(Candidate c:ordered){
            if(result.size()>=max)break;
            int used=usedArtists.getOrDefault(c.artist,0);
            if(used>=3)continue;
            usedArtists.put(c.artist,used+1);
            try{result.add(new JSONObject().put("title",c.title).put("artist",c.artist)
                .put("votes",c.fans.size()).put("key",c.hash));}catch(Exception ignored){}
        }
        return result;
    }
    public void saveMatch(String roomId,Done<Integer> callback){
        run(()->{
            Room room=new Room(roomId,getObj("rooms/"+roomId));
            if(!MATCH.equals(room.kind))throw new Exception("Essa playlist não é um Match.");
            List<JSONObject> proposed=recommendations(room,40);
            Set<String> wanted=new HashSet<>();
            for(JSONObject song:proposed)wanted.add(song.optString("key"));
            // Apaga somente sugestões criadas automaticamente e que perderam relevância.
            java.util.Iterator<String> oldSongs=room.songs.keys();
            while(oldSongs.hasNext()){
                String key=oldSongs.next();
                JSONObject value=room.songs.optJSONObject(key);
                if(value!=null&&value.optBoolean("autoMatch",false)&&!wanted.contains(key)){
                    delete("rooms/"+roomId+"/tracks/"+key);
                }
            }
            for(JSONObject song:proposed){
                String key=song.optString("key");
                if(room.songs.has(key))continue;
                db("rooms/"+roomId+"/tracks/"+key,"PUT",new JSONObject()
                    .put("title",song.optString("title")).put("artist",song.optString("artist"))
                    .put("album","Match Merlin").put("addedBy",uid)
                    .put("autoMatch",true)
                    .put("addedAt",System.currentTimeMillis()));
            }
            return proposed.size();
        },callback);
    }

    /**
     * Escuta mudanças do Firebase em tempo real via Server Sent Events.
     * Um listener é mantido somente enquanto a playlist estiver visível.
     * O chamador deve encerrar a assinatura ao fechar a tela.
     */
    public final class Subscription {
        private volatile boolean running=true;
        private volatile HttpURLConnection current;
        private Thread thread;
        public void cancel(){
            running=false;
            HttpURLConnection connection=current;
            if(connection!=null)connection.disconnect();
            if(thread!=null)thread.interrupt();
        }
    }
    public Subscription listen(String roomId,Done<Boolean> updates){
        Subscription sub=new Subscription();
        if(!roomId.matches("[a-f0-9]{32}")){
            main.post(()->updates.complete(false,"Identificador inválido"));return sub;
        }
        sub.thread=new Thread(()->{
            long retry=1500;
            while(sub.running&&!Thread.currentThread().isInterrupted()){
                HttpURLConnection conn=null;
                try{
                    auth();
                    String endpoint=database.replaceAll("/+$","")+"/rooms/"+roomId+".json?auth="+urlEncode(idToken);
                    conn=(HttpURLConnection)new URL(endpoint).openConnection();
                    sub.current=conn;
                    conn.setRequestProperty("Accept","text/event-stream");
                    conn.setRequestProperty("Cache-Control","no-cache");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(45000);
                    conn.setInstanceFollowRedirects(true);
                    int status=conn.getResponseCode();
                    if(status!=200)throw new Exception("Erro "+status+" ao sincronizar.");
                    retry=1500;
                    try(BufferedReader reader=new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8))){
                        String line;String event="";StringBuilder data=new StringBuilder();
                        while(sub.running && (line=reader.readLine())!=null){
                            if(line.startsWith("event:")) event=line.substring(6).trim();
                            else if(line.startsWith("data:"))data.append(line.substring(5).trim());
                            else if(line.isEmpty()){
                                if(event.equals("auth_revoked")){
                                    tokenExpires=0;break;
                                }
                                if(event.equals("put")||event.equals("patch")){
                                    final String currentEvent=event;
                                    main.post(()->{if(sub.running)updates.complete(true,null);});
                                }
                                event="";data.setLength(0);
                            }
                        }
                    }
                }catch(Exception ignored){
                    // A UI mantém polling como fallback nos momentos sem SSE.
                }finally{
                    if(conn!=null)conn.disconnect();
                    sub.current=null;
                }
                if(!sub.running)break;
                try{Thread.sleep(retry);}catch(InterruptedException e){break;}
                retry=Math.min(15000,retry*2);
            }
        },"MerlinRoomRealtime");
        sub.thread.setDaemon(true);
        sub.thread.start();
        return sub;
    }

    public void close(){io.shutdown();}
}
