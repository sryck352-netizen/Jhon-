package com.merlin.player;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.widget.ImageView;
import android.util.LruCache;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Iterator;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final int BG = 0xFF111014;
    private final int SURFACE = 0xFF26242C;
    private final int PURPLE = 0xFFB47BFF;
    private final int TEXT = 0xFFF9F9F9;
    private final int MUTED = 0xFFB4AEBB;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService onlineWorker = Executors.newFixedThreadPool(3);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Track> tracks = new ArrayList<>();
    private List<Track> visible = new ArrayList<>();
    private LinearLayout songsContainer, playlistBar, navBottom, headerRoot, headerChips;
    private TextView headerTitle;
    private EditText searchField;
    private int currentScreen = 0; // 0 início, 1 busca, 2 biblioteca
    private final LruCache<String,Bitmap> artCache = new LruCache<>(80);
    private TextView countText, miniTitle, miniArtist, miniPlay, miniArt;
    private SeekBar miniSeek;
    private String search = "", filter = "Todas";
    private Dialog playerDialog;
    private CloudPlaylists cloud;
    private List<CloudPlaylists.Room> cloudRooms = new ArrayList<>();
    private AlertDialog activeRoomDialog;
    private LinearLayout activeRoomContent;
    private String activeRoomId="";
    private Runnable roomPoll;
    private Runnable matchUpdateRunnable;
    private CloudPlaylists.Subscription activeSubscription;
    private String lastShownPlaybackError="";


    private List<OnlineCatalog.Song> onlineResults = new ArrayList<>();
    private List<Track> audiusResults = new ArrayList<>();
    private String onlineMessage="Digite pelo menos 2 letras para pesquisar na internet.";
    private boolean onlineLoading=false;
    private long onlineRequest=0;
    private Runnable pendingSearch;
    private Runnable timerStop;
    private final LruCache<String,Bitmap> onlineImages=new LruCache<>(120);
    private final BroadcastReceiver playbackReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateMini(); }
    };

    private int dp(float x) { return (int)(getResources().getDisplayMetrics().density*x+0.5f); }
    private LinearLayout.LayoutParams lp(int w,int h) {return new LinearLayout.LayoutParams(w<0?w:dp(w), h<0?h:dp(h));}
    private GradientDrawable round(int color,float radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private GradientDrawable gradient() {
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF522783,0xFF262044,0xFF171425});
        g.setCornerRadius(dp(24)); return g;
    }
    private TextView text(String s,int size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private TextView action(String s,int size,boolean filled) {
        TextView b=text(s,size, filled?BG:TEXT,true);
        b.setGravity(Gravity.CENTER); b.setPadding(dp(16),dp(11),dp(16),dp(11));
        b.setBackground(round(filled?PURPLE:SURFACE,20)); return b;
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        cloud=new CloudPlaylists(this);
        buildUI();
        checkPermission();
        handler.post(()->handleJoinIntent(getIntent()));
        handler.post(new Runnable(){
            @Override public void run(){updateMini();handler.postDelayed(this,700);}
        });
    }

    @Override protected void onStart() {
        super.onStart();
        if(Build.VERSION.SDK_INT>=33) registerReceiver(playbackReceiver,new IntentFilter(MusicService.ACTION_UPDATE),Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(playbackReceiver,new IntentFilter(MusicService.ACTION_UPDATE));
        updateMini();
        if(cloud!=null&&cloud.configured())loadCloudRooms();
        if(roomPoll!=null&&activeRoomDialog!=null&&activeRoomDialog.isShowing())handler.postDelayed(roomPoll,1000);
        if(activeRoomDialog!=null&&activeRoomDialog.isShowing()&&activeSubscription==null)startRealtimeRoom(activeRoomId);
    }
    @Override protected void onStop() {
        try{unregisterReceiver(playbackReceiver);}catch(Exception ignored){}
        if(roomPoll!=null)handler.removeCallbacks(roomPoll);
        if(activeSubscription!=null){activeSubscription.cancel();activeSubscription=null;}
        super.onStop();
    }


    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);handleJoinIntent(intent);
    }
    @Override protected void onDestroy(){
        if(roomPoll!=null)handler.removeCallbacks(roomPoll);
        if(activeSubscription!=null){activeSubscription.cancel();activeSubscription=null;}
        if(cloud!=null)cloud.close();
        super.onDestroy();
    }

    private void buildUI(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        if(Build.VERSION.SDK_INT>=35) root.setOnApplyWindowInsetsListener((v,insets)-> {
            android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(0,bars.top,0,bars.bottom);
            return insets;
        });
        setContentView(root);

        headerRoot=new LinearLayout(this);
        headerRoot.setOrientation(LinearLayout.VERTICAL);
        headerRoot.setPadding(dp(18),dp(11),dp(18),dp(5));
        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge=text("M",19,0xFFF4F0FF,true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(0xFF69469C,99));
        top.addView(badge,lp(40,40));
        badge.setOnClickListener(v->openProfile());
        headerTitle=text("Merlin",23,TEXT,true);
        headerTitle.setPadding(dp(13),0,0,0);
        top.addView(headerTitle,new LinearLayout.LayoutParams(0,dp(44),1));
        TextView add=text("＋",28,PURPLE,true);
        add.setGravity(Gravity.CENTER);
        top.addView(add,lp(44,44));
        add.setOnClickListener(v->openCreateMenu());
        headerRoot.addView(top,lp(-1,49));
        headerChips=new LinearLayout(this);
        headerChips.setGravity(Gravity.CENTER_VERTICAL);
        headerChips.setPadding(0,dp(8),0,dp(5));
        headerRoot.addView(headerChips,lp(-1,55));
        root.addView(headerRoot);
        // A biblioteca e a busca ocupam a mesma área, sem páginas web.
        searchField=new EditText(this);
        searchField.setSingleLine(true);
        searchField.setTextColor(0xFF181619);
        searchField.setTextSize(15);
        searchField.setHintTextColor(0xFF66616C);
        searchField.setHint("⌕  O que você quer ouvir?");
        searchField.setPadding(dp(15),0,dp(15),0);
        searchField.setBackground(round(0xFFF6F3FB,7));
        LinearLayout.LayoutParams sp=margins(18,5,18,7,-1,54);
        root.addView(searchField,sp);
        searchField.setVisibility(View.GONE);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                search=s.toString().toLowerCase(Locale.ROOT).trim();
                if(currentScreen==1) scheduleOnlineSearch();
                renderTracks();
            }
            @Override public void afterTextChanged(Editable e){}
        });

        ScrollView scroll=new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        songsContainer=new LinearLayout(this);
        songsContainer.setOrientation(LinearLayout.VERTICAL);
        songsContainer.setPadding(dp(18),dp(4),dp(18),dp(22));
        scroll.addView(songsContainer);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        // Barra permanente de reprodução, mesmo com a tela bloqueada.
        buildMini(root);
        navBottom=new LinearLayout(this);
        navBottom.setGravity(Gravity.CENTER);
        navBottom.setBackgroundColor(BG);
        root.addView(navBottom,lp(-1,58));
        countText=text("",13,MUTED,false);
        updateNavigation();
        renderTracks();
    }

    private LinearLayout.LayoutParams margins(int left,int top,int right,int bottom,int width,int height) {
        LinearLayout.LayoutParams p=lp(width,height);
        p.setMargins(dp(left),dp(top),dp(right),dp(bottom));
        return p;
    }
    private TextView chip(String name,boolean active,Runnable click){
        TextView b=text(name,13,active?0xFF111111:TEXT,active);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(17),0,dp(17),0);
        b.setBackground(round(active?PURPLE:0xFF303030,28));
        b.setOnClickListener(v->click.run());
        return b;
    }
    private TextView section(String s) {
        TextView t=text(s,21,TEXT,true);
        t.setPadding(0,dp(20),0,dp(13));
        return t;
    }
    private void updateTopHeader(){
        if(headerRoot==null||headerChips==null||headerTitle==null)return;
        headerTitle.setText(currentScreen==1?"Buscar":currentScreen==2?"Sua biblioteca":"Merlin");
        headerChips.removeAllViews();
        headerChips.setVisibility(currentScreen==0?View.VISIBLE:View.GONE);
        if(currentScreen!=0)return;
        headerChips.addView(chip("Tudo",true,()->{
            filter="Todas";currentScreen=0;renderTracks();
        }),margins(0,0,8,0,-2,39));
        headerChips.addView(chip("Música",false,()->{
            filter="Todas";currentScreen=2;renderTracks();
        }),margins(0,0,8,0,-2,39));
        headerChips.addView(chip("Curtidas",false,()->{
            filter="Curtidas";currentScreen=2;renderTracks();
        }),margins(0,0,8,0,-2,39));
    }
    private void updateNavigation(){
        if(navBottom==null)return;
        navBottom.removeAllViews();
        addNav("⌂","Início",0,()->{currentScreen=0;filter="Todas";search="";renderTracks();});
        addNav("⌕","Buscar",1,()->{currentScreen=1;filter="Todas";renderTracks();searchField.requestFocus();});
        addNav("▥","Biblioteca",2,()->{currentScreen=2;renderTracks();});
        addNav("+","Criar",-1,()->openCreateMenu());
    }
    private void addNav(String icon,String name,int page,Runnable click){
        LinearLayout col=new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        TextView symbol=text(icon,28,page==currentScreen?TEXT:MUTED,true);
        symbol.setGravity(Gravity.CENTER);
        TextView txt=text(name,10,page==currentScreen?TEXT:MUTED,page==currentScreen);
        txt.setGravity(Gravity.CENTER);
        col.addView(symbol,lp(-1,32));
        col.addView(txt,lp(-1,15));
        navBottom.addView(col,new LinearLayout.LayoutParams(0,dp(58),1));
        col.setOnClickListener(v->click.run());
    }
    private View cover(Track t,int size){
        FrameLayout frame=new FrameLayout(this);
        GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xFF7247A8,0xFF2E3A6C,0xFFB04B75});
        background.setCornerRadius(dp(4));
        frame.setBackground(background);
        frame.setClipToOutline(true);
        TextView mark=text("♫",size>=100?48:23,0xFFEFE6FF,true);
        mark.setGravity(Gravity.CENTER);
        frame.addView(mark,new FrameLayout.LayoutParams(-1,-1));
        if(t!=null){
            ImageView img=new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setTag(t.uri);
            frame.addView(img,new FrameLayout.LayoutParams(-1,-1));
            Bitmap b=artCache.get(t.uri);
            if(b!=null)img.setImageBitmap(b);
            else worker.execute(()->{
                Bitmap cover=null;
                if(t.artwork.startsWith("https://"))cover=OnlineCatalog.thumbnail(t.artwork);
                if(cover!=null){
                    Bitmap result=cover;
                    artCache.put(t.uri,result);
                    handler.post(()->{if(t.uri.equals(img.getTag()))img.setImageBitmap(result);});
                    return;
                }
                if(AudiusCatalog.canPlay(t))return;
                MediaMetadataRetriever retriever=new MediaMetadataRetriever();
                try{
                    retriever.setDataSource(getApplicationContext(),Uri.parse(t.uri));
                    byte[] bytes=retriever.getEmbeddedPicture();
                    if(bytes!=null){
                        BitmapFactory.Options opt=new BitmapFactory.Options();
                        opt.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,opt);
                        int max=Math.max(opt.outWidth,opt.outHeight);
                        opt.inSampleSize=Math.max(1,max/320);
                        opt.inJustDecodeBounds=false;
                        Bitmap decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opt);
                        if(decoded!=null){
                            cover=Bitmap.createScaledBitmap(decoded,Math.min(320,decoded.getWidth()),
                                Math.min(320,decoded.getHeight()),true);
                            if(cover!=decoded) decoded.recycle();
                        }
                    }
                }catch(Exception ignored){}finally{try{retriever.release();}catch(Exception ignored){}}
                if(cover!=null){
                    Bitmap result=cover;
                    artCache.put(t.uri,result);
                    handler.post(()->{
                        if(t.uri.equals(img.getTag()))img.setImageBitmap(result);
                    });
                }
            });
        }
        return frame;
    }
    private View quickTile(String name,Track art,Runnable click){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(round(SURFACE,5));
        row.addView(cover(art,56),lp(56,56));
        TextView title=text(name,12,TEXT,true);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(dp(9),0,dp(5),0);
        row.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        row.setOnClickListener(v->click.run());
        return row;
    }
    private void sectionRow(LinearLayout parent,String name,Track t,Runnable click){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(cover(t,55),lp(55,55));
        LinearLayout names=new LinearLayout(this);
        names.setPadding(dp(13),0,dp(6),0);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView song=text(name,15,TEXT,false);
        song.setSingleLine(true);song.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView artist=text(t==null?"Playlist":t.artist,12,MUTED,false);
        artist.setSingleLine(true);artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        names.addView(song);names.addView(artist);
        row.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        TextView menu=text("⋮",24,MUTED,false);menu.setGravity(Gravity.CENTER);
        row.addView(menu,lp(35,48));
        menu.setOnClickListener(v->{if(t!=null)showTrackOptions(t);else createPlaylist();});
        row.setOnClickListener(v->click.run());
        parent.addView(row,margins(0,0,0,8,-1,63));
    }
    private void addPlaylistCard(LinearLayout strip,String name,Track art,Runnable click){
        LinearLayout item=new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.addView(cover(art,156),lp(155,155));
        TextView label=text(name,13,TEXT,true);
        label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        item.addView(label,margins(0,7,0,0,-1,23));
        item.addView(text("Merlin • Playlist",11,MUTED,false));
        strip.addView(item,margins(0,0,15,0,155,-2));
        item.setOnClickListener(v->click.run());
        item.setOnLongClickListener(v->{if(AudioLibrary.playlistNames(this).contains(name)){localPlaylistMenu(name);return true;}return false;});
    }
    private void addHorizontalList(LinearLayout parent,ArrayList<String> names){
        HorizontalScrollView sc=new HorizontalScrollView(this);
        sc.setHorizontalScrollBarEnabled(false);
        LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);
        for(String name:names){
            Track first=null;
            HashSet<String> selected=AudioLibrary.playlistUris(this,name);
            for(Track t:tracks){if(selected.contains(t.uri)){first=t;break;}}
            final String choice=name;
            addPlaylistCard(line,name,first,()->{filter=choice;currentScreen=2;renderTracks();});
        }
        if(names.isEmpty())addPlaylistCard(line,"Criar playlist",null,()->createPlaylist());
        sc.addView(line);parent.addView(sc,lp(-1,205));
    }
    private void showTrackList(LinearLayout parent,List<Track> list,int limit){
        if(list.isEmpty()){
            TextView empty=text("Suas músicas aparecerão aqui.\nImporte músicas baixadas para começar.",14,MUTED,false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(15),dp(22),dp(15),dp(22));
            parent.addView(empty);
            return;
        }
        for(int i=0;i<Math.min(limit,list.size());i++){
            Track t=list.get(i);
            sectionRow(parent,t.title,t,()->playSelection(t));
        }
    }
    private ArrayList<Track> getRecent(){
        ArrayList<Track> r=new ArrayList<>();
        java.util.Set<String> recent=getSharedPreferences("merlin_library",0)
            .getStringSet("recent_uris",new HashSet<>());
        // Mantém as mais recentes por meio de uma lista ordenada separada.
        String sequence=getSharedPreferences("merlin_library",0).getString("recent_sequence","");
        for(String uri:sequence.split("\n")){
            for(Track t:tracks)if(t.uri.equals(uri)&&!r.contains(t))r.add(t);
        }
        return r;
    }
    private void remember(Track track){
        String old=getSharedPreferences("merlin_library",0).getString("recent_sequence","");
        StringBuilder recent=new StringBuilder(track.uri);
        int total=1;
        for(String uri:old.split("\n")){
            if(uri.isEmpty()||uri.equals(track.uri))continue;
            if(total++>=20)break;
            recent.append("\n").append(uri);
        }
        getSharedPreferences("merlin_library",0).edit().putString("recent_sequence",recent.toString()).apply();
        scheduleMatchUpdates();
    }

    private void buildMini(LinearLayout root) {
        LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackground(round(0xFF542137,9));
        LinearLayout.LayoutParams wl=lp(-1,-2);wl.setMargins(dp(10),dp(5),dp(10),dp(10));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10),dp(8),dp(8),dp(5));
        miniArt=text("♫",26,TEXT,true);miniArt.setGravity(Gravity.CENTER);miniArt.setBackground(round(0xFF75405B,5));
        bar.addView(miniArt,lp(47,47));
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(12),0,dp(4),0);
        miniTitle=text("Nada tocando",14,TEXT,true);miniTitle.setSingleLine(true);miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        miniArtist=text("Escolha uma música",11,MUTED,false);miniArtist.setSingleLine(true);
        names.addView(miniTitle);names.addView(miniArtist);
        bar.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        names.setOnClickListener(v->showPlayer());
        miniArt.setOnClickListener(v->showPlayer());
        miniPlay=text("▶",25,TEXT,true);miniPlay.setGravity(Gravity.CENTER);
        miniPlay.setOnClickListener(v->command(MusicService.ACTION_TOGGLE));bar.addView(miniPlay,lp(45,48));
        TextView next=text("⏭",23,TEXT,true);next.setGravity(Gravity.CENTER);
        next.setOnClickListener(v->command(MusicService.ACTION_NEXT));bar.addView(next,lp(35,48));
        wrap.addView(bar);
        miniSeek=new SeekBar(this);miniSeek.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        miniSeek.setThumbTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        miniSeek.setPadding(dp(6),0,dp(6),0);
        miniSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar s,int p,boolean from){ }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s){seek(s.getProgress());}
        });
        wrap.addView(miniSeek,lp(-1,11));root.addView(wrap,wl);
    }


    private void renderTabs(){
        if(playlistBar==null)return;
        playlistBar.removeAllViews();
        ArrayList<String> names=new ArrayList<>();
        names.add("Todas");names.add("Curtidas");
        names.addAll(AudioLibrary.playlistNames(this));
        for(String name:names){
            TextView b=chip(name,name.equals(filter),()->{filter=name;renderTracks();});
            playlistBar.addView(b,margins(0,0,8,0,-2,38));
            b.setOnLongClickListener(v->{if(AudioLibrary.playlistNames(this).contains(name)){localPlaylistMenu(name);return true;}return false;});
        }
    }

    private void createPlaylist(){
        EditText entry=new EditText(this);entry.setSingleLine(true);entry.setTextColor(TEXT);
        entry.setHint("Nome da playlist"); entry.setHintTextColor(MUTED);
        LinearLayout wrap=new LinearLayout(this);wrap.setPadding(dp(18),0,dp(18),0);wrap.addView(entry);
        new AlertDialog.Builder(this).setTitle("Nova playlist").setView(wrap)
            .setPositiveButton("Criar",(d,w)->{
                String name=entry.getText().toString().trim();
                if(name.isEmpty() || name.equals("Todas") || name.equals("Curtidas")) { toast("Escolha outro nome");return; }
                if(AudioLibrary.playlistNames(this).contains(name)){toast("Já existe uma playlist com esse nome");return;}
                AudioLibrary.createPlaylist(this,name);filter=name;currentScreen=2;renderTabs();renderTracks();
                toast("Playlist criada: "+name);
            }).setNegativeButton("Cancelar",null).show();
    }


    private void renderTracks(){
        if(songsContainer==null)return;
        songsContainer.removeAllViews();
        updateTopHeader();
        if(searchField!=null)searchField.setVisibility(currentScreen==1?View.VISIBLE:View.GONE);
        updateNavigation();
        HashSet<String> liked=AudioLibrary.favorites(this);
        HashSet<String> selected=filter.equals("Todas")||filter.equals("Curtidas")
            ?null:AudioLibrary.playlistUris(this,filter);
        visible=new ArrayList<>();
        for(Track t:tracks){
            if(filter.equals("Curtidas")&&!liked.contains(t.uri))continue;
            if(selected!=null&&!selected.contains(t.uri))continue;
            if(!search.isEmpty() && currentScreen==1 &&
                !t.title.toLowerCase(Locale.ROOT).contains(search)&&
                !t.artist.toLowerCase(Locale.ROOT).contains(search))continue;
            visible.add(t);
        }
        countText.setText(visible.size()+" músicas");
        if(currentScreen==0){
            // Atalhos em grade inspirados na organização da biblioteca do usuário.
            ArrayList<String> shortcuts=new ArrayList<>();
            shortcuts.add("Músicas Curtidas");shortcuts.add("Todas as músicas");
            shortcuts.addAll(AudioLibrary.playlistNames(this));
            if(shortcuts.size()>6)shortcuts=new ArrayList<>(shortcuts.subList(0,6));
            for(int i=0;i<shortcuts.size();i+=2){
                LinearLayout line=new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                for(int col=0;col<2;col++){
                    int k=i+col;if(k>=shortcuts.size())break;
                    String name=shortcuts.get(k);
                    String p=name.equals("Músicas Curtidas")?"Curtidas":
                             name.equals("Todas as músicas")?"Todas":name;
                    Track sample=null;
                    for(Track t:tracks){
                        if(p.equals("Todas")||(p.equals("Curtidas")&&liked.contains(t.uri))||
                            (!p.equals("Curtidas")&&AudioLibrary.playlistUris(this,p).contains(t.uri))){
                            sample=t;break;
                        }
                    }
                    LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(56),1);
                    if(col==1)a.leftMargin=dp(9);
                    line.addView(quickTile(name,sample,()->{filter=p;currentScreen=2;renderTracks();}),a);
                }
                songsContainer.addView(line,margins(0,0,0,9,-1,56));
            }

            ArrayList<Track> recently=getRecent();
            List<Track> highlight=recently.isEmpty()?tracks:recently;
            songsContainer.addView(section("Não sai do seu fone"));
            showTrackList(songsContainer,highlight,Math.min(4,highlight.size()));
            songsContainer.addView(section("Suas playlists"));
            addHorizontalList(songsContainer,AudioLibrary.playlistNames(this));
            renderCloudSection(songsContainer);

            songsContainer.addView(section("Recentes"));
            HorizontalScrollView sc=new HorizontalScrollView(this);
            sc.setHorizontalScrollBarEnabled(false);
            LinearLayout line=new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            ArrayList<Track> showing=recently.isEmpty()?new ArrayList<>(tracks):recently;
            for(int j=0;j<Math.min(showing.size(),8);j++){
                Track t=showing.get(j);
                LinearLayout card=new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.addView(cover(t,115),lp(115,115));
                TextView name=text(t.title,12,TEXT,true);
                name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                card.addView(name,margins(0,6,0,0,-1,19));
                TextView art=text(t.artist,11,MUTED,false);
                art.setSingleLine(true);art.setEllipsize(android.text.TextUtils.TruncateAt.END);
                card.addView(art);
                line.addView(card,margins(0,0,15,0,115,-2));
                card.setOnClickListener(v->playSelection(t));
            }
            if(showing.isEmpty()){
                TextView empty=text("Importe suas músicas para montar os recentes.",13,MUTED,false);
                line.addView(empty);
            }
            sc.addView(line);songsContainer.addView(sc,lp(-1,161));

            LinearLayout heading=new LinearLayout(this);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            heading.addView(section("Suas músicas"),new LinearLayout.LayoutParams(0,-2,1));
            TextView seeAll=text("Ver tudo",12,MUTED,true);
            seeAll.setOnClickListener(v->{filter="Todas";currentScreen=2;renderTracks();});
            heading.addView(seeAll);
            songsContainer.addView(heading);
            showTrackList(songsContainer,tracks,30);
        } else if(currentScreen==1){
            if(search.trim().isEmpty()){
                songsContainer.addView(section("Explore novos sons"));
                songsContainer.addView(text("Descubra artistas independentes ou procure uma música do seu celular.",13,MUTED,false));
                String[] categories={"Pop","Eletrônica","Indie","Lo-fi","Rock","Instrumental"};
                int[] colors={0xFFB52E81,0xFF3D43B3,0xFF7C4EA9,0xFF247B7A,0xFFB64356,0xFF7758A9};
                for(int i=0;i<categories.length;i+=2){
                    LinearLayout row=new LinearLayout(this);
                    for(int c=0;c<2;c++){
                        final String name=categories[i+c];
                        TextView card=text(name,19,TEXT,true);
                        card.setPadding(dp(15),dp(4),dp(8),dp(4));
                        card.setBackground(round(colors[i+c],9));
                        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(88),1);
                        if(c==1)p.leftMargin=dp(10);
                        row.addView(card,p);
                        card.setOnClickListener(v->{currentScreen=1;searchField.setText(name);searchField.setSelection(name.length());});
                    }
                    songsContainer.addView(row,margins(0,0,0,10,-1,88));
                }
                songsContainer.addView(section("Suas músicas"));
                if(tracks.isEmpty())songsContainer.addView(text("Importe suas músicas e os downloads do Snaptube para ouvir sem anúncios.",13,MUTED,false));
                showTrackList(songsContainer,tracks,8);
            }else{
                if(!visible.isEmpty()){
                    songsContainer.addView(section("Na sua biblioteca"));
                    showTrackList(songsContainer,visible,40);
                }
                songsContainer.addView(section("Ouvir no Merlin"));
                songsContainer.addView(text("Faixas disponíveis para reprodução interna • Audius"+(JamendoCatalog.configured()?" e Jamendo":""),12,MUTED,false));
                if(onlineLoading){
                    songsContainer.addView(text("Pesquisando…",14,MUTED,false));
                }else if(audiusResults.isEmpty()){
                    songsContainer.addView(text("Não encontrei esta música nos catálogos autorizados. Você ainda pode importar um arquivo que já possua.",13,MUTED,false));
                    TextView importIt=action("＋  Importar música do celular",13,false);
                    importIt.setOnClickListener(v->pickAudio());
                    songsContainer.addView(importIt,margins(0,12,0,6,-1,52));
                }else for(Track playable:audiusResults)audiusRow(songsContainer,playable);
                // Não misturar metadados do iTunes com músicas reproduzíveis.
                // Resultados que não têm licença de streaming são ocultados da busca.
            }
        } else {
            LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
            header.addView(section("Sua biblioteca"),new LinearLayout.LayoutParams(0,-2,1));
            TextView importButton=text("⇩  Importar",13,PURPLE,true);
            importButton.setGravity(Gravity.CENTER);
            importButton.setOnClickListener(v->pickAudio());
            header.addView(importButton,lp(102,46));
            songsContainer.addView(header);
            HorizontalScrollView tabs=new HorizontalScrollView(this);
            tabs.setHorizontalScrollBarEnabled(false);
            playlistBar=new LinearLayout(this);
            tabs.addView(playlistBar);
            songsContainer.addView(tabs,margins(0,0,0,12,-1,40));
            renderTabs();
            songsContainer.addView(section("Suas playlists"));
            addHorizontalList(songsContainer,AudioLibrary.playlistNames(this));
            renderCloudSection(songsContainer);
            TextView counter=text(visible.size()+" músicas  •  toque e segure para organizar",12,MUTED,false);
            songsContainer.addView(counter,margins(0,0,0,12,-1,-2));
            showTrackList(songsContainer,visible,400);
        }
        // Importação disponível mesmo com biblioteca vazia.
        if(tracks.isEmpty()){
            TextView importer=chip("＋  Importar músicas do Snaptube ou do celular",true,()->pickAudio());
            songsContainer.addView(importer,margins(0,10,0,12,-1,51));
        }
    }


    private void scheduleOnlineSearch(){
        onlineRequest++;
        long request=onlineRequest;
        if(pendingSearch!=null)handler.removeCallbacks(pendingSearch);
        if(search.length()<2){
            onlineLoading=false;onlineResults=new ArrayList<>();audiusResults=new ArrayList<>();
            onlineMessage="Pesquise um título, álbum ou artista.";return;
        }
        if(getSharedPreferences("merlin_settings",0).getBoolean("offline_only",false)){
            onlineLoading=false;onlineResults=new ArrayList<>();audiusResults=new ArrayList<>();
            onlineMessage="Busca online desativada no modo offline.";return;
        }
        onlineLoading=true;
        String term=search;
        pendingSearch=()->{
            onlineWorker.execute(()->{
                List<OnlineCatalog.Song> received;
                List<Track> playable;
                String message="";
                try{
                    playable=AudiusCatalog.search(term);
                }catch(Exception e){
                    playable=new ArrayList<>();
                    message="Audius: "+e.getMessage();
                }
                if(JamendoCatalog.configured()){
                    try {
                        playable.addAll(JamendoCatalog.search(term));
                    }catch(Exception e){
                        message += " Jamendo: "+e.getMessage();
                    }
                }
                try {
                    received=OnlineCatalog.search(term);
                }catch(Exception e){
                    received=new ArrayList<>();
                    if(message.isEmpty())message="Não foi possível consultar o catálogo online.";
                }
                if(received.isEmpty()&&playable.isEmpty()&&message.isEmpty())
                    message="Nenhum resultado encontrado.";
                final List<OnlineCatalog.Song> response=received;
                final List<Track> playableResponse=playable;
                final String status=message;
                handler.post(()->{
                    if(request!=onlineRequest || currentScreen!=1)return;
                    onlineResults=response; audiusResults=playableResponse;onlineMessage=status;
                    onlineLoading=false;renderTracks();
                });
            });
        };
        handler.postDelayed(pendingSearch,370);
    }

    private void audiusRow(LinearLayout list, Track song){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(cover(song,55),lp(55,55));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12),0,dp(8),0);
        TextView name=text(song.title,15,TEXT,false);
        name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(name);
        TextView subtitle=text(song.artist+(JamendoCatalog.canPlay(song)?" • Jamendo":" • Audius"),12,MUTED,false);
        subtitle.setSingleLine(true);subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(subtitle);
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView play=text("▶",24,PURPLE,true);play.setGravity(Gravity.CENTER);
        row.addView(play,lp(40,56));
        row.setOnClickListener(v->playOnline(song));
        row.setOnLongClickListener(v->{
            AudioLibrary.rememberOnline(this,song);
            AudioLibrary.toggleFavorite(this,song.uri);
            scheduleMatchUpdates();
            toast("Faixa salva nos favoritos; toque para reproduzir");
            loadTracks();return true;
        });
        list.addView(row,margins(0,0,0,9,-1,61));
    }
    private void playOnline(Track chosen){
        if(!AudiusCatalog.canPlay(chosen)&&!JamendoCatalog.canPlay(chosen)){toast("Fonte de áudio não autorizada.");return;}
        ArrayList<Track> queue=new ArrayList<>(audiusResults);
        if(queue.isEmpty())queue.add(chosen);
        int selected=-1;
        for(int i=0;i<queue.size();i++)if(queue.get(i).uri.equals(chosen.uri)){selected=i;break;}
        if(selected<0){queue.add(0,chosen);selected=0;}
        AudioLibrary.rememberOnline(this,chosen);
        boolean known=false;
        for(Track t:tracks)if(t.uri.equals(chosen.uri)){known=true;break;}
        if(!known)tracks.add(chosen);
        remember(chosen);
        AudioLibrary.saveQueue(this,queue);
        Intent intent=new Intent(this,MusicService.class).setAction(MusicService.ACTION_PLAY);
        intent.putExtra("index",selected);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(intent);else startService(intent);
        handler.postDelayed(this::updateMini,500);
        toast("Abrindo áudio no Merlin…");
    }

    private void onlineRow(LinearLayout list, OnlineCatalog.Song song){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout art=new FrameLayout(this);
        art.setBackground(round(0xFF373737,5));
        ImageView image=new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.addView(image,new FrameLayout.LayoutParams(-1,-1));
        Bitmap memory=onlineImages.get(song.art);
        if(memory!=null)image.setImageBitmap(memory);
        else if(song.art.startsWith("https://")){
            onlineWorker.execute(()->{
                Bitmap b=OnlineCatalog.thumbnail(song.art);
                if(b!=null){
                    synchronized(onlineImages){onlineImages.put(song.art,b);}
                    handler.post(()->image.setImageBitmap(b));
                }
            });
        }
        row.addView(art,lp(55,55));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12),0,dp(6),0);
        TextView title=text(song.title,15,TEXT,false);
        title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView artist=text(song.artist+" • "+song.album,12,MUTED,false);
        artist.setSingleLine(true);artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(title);labels.addView(artist);
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView go=text("↗",21,PURPLE,true);go.setGravity(Gravity.CENTER);
        row.addView(go,lp(39,50));
        row.setOnClickListener(v->openSongChoices(song));
        list.addView(row,margins(0,0,0,9,-1,61));
    }
    private void openSongChoices(OnlineCatalog.Song song){
        final String query=song.title+" "+song.artist;
        new AlertDialog.Builder(this).setTitle(song.title)
            .setMessage(song.artist+"\n\nO Merlin encontrou esta música na internet. Para ouvi-la online, é necessário um serviço de reprodução autorizado.")
            .setItems(new String[]{"Buscar no Spotify","Abrir no Apple Music / iTunes","Copiar nome da música"},(dialog,option)->{
                try{
                    if(option==0){
                        openExternal("https://open.spotify.com/search/"+Uri.encode(query));
                    }else if(option==1){
                        if(song.storeUrl.startsWith("https://"))openExternal(song.storeUrl);
                        else toast("Link não disponível");
                    }else{
                        ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE))
                            .setPrimaryClip(android.content.ClipData.newPlainText("Música",query));
                        toast("Música copiada");
                    }
                }catch(Exception e){toast("Não encontrei um aplicativo para abrir o link");}
            }).setNegativeButton("Fechar",null).show();
    }
    private void openExternal(String uri){
        startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(uri)));
    }
    private void openProfile(){
        ScrollView scroll=new ScrollView(this);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22),dp(24),dp(22),dp(28));body.setBackgroundColor(0xFF202020);
        TextView heading=text("Merlin   •   Perfil",25,TEXT,true);body.addView(heading,lp(-1,55));
        body.addView(text("Sua música, do seu jeito.",13,MUTED,false));
        TextView nickname=action("✎  Nome nas playlists: "+cloud.nickname(),14,false);
        body.addView(nickname,margins(0,14,0,10,-1,49));
        nickname.setOnClickListener(v->{
            EditText input=new EditText(this);input.setSingleLine(true);
            input.setText(cloud.nickname());
            new AlertDialog.Builder(this).setTitle("Nome para os amigos")
                .setView(input).setPositiveButton("Salvar",(d,w)->{
                    String name=input.getText().toString().trim();
                    if(name.isEmpty()){toast("Informe seu nome");return;}
                    cloud.setNickname(name);nickname.setText("✎  Nome nas playlists: "+cloud.nickname());
                    toast("Nome salvo para os próximos convites");
                }).setNegativeButton("Cancelar",null).show();
        });
        body.addView(section("Sua atividade"));
        TextView recent=action("◷   Músicas recentes",15,false);
        body.addView(recent,margins(0,0,0,9,-1,52));
        recent.setOnClickListener(v->{
            ArrayList<Track> tracksRecent=getRecent();
            StringBuilder lines=new StringBuilder();
            for(Track t:tracksRecent)lines.append("• ").append(t.title).append(" — ").append(t.artist).append("\n");
            new AlertDialog.Builder(this).setTitle("Recentes")
                .setMessage(lines.length()==0?"Você ainda não ouviu nenhuma música.":lines.toString())
                .setPositiveButton("Fechar",null).show();
        });
        TextView settings=action("⚙   Configurações e privacidade",15,false);
        body.addView(settings,margins(0,0,0,9,-1,52));
        settings.setOnClickListener(v->openSettings());
        TextView about=action("ⓘ   Sobre o Merlin",15,false);
        body.addView(about,margins(0,0,0,9,-1,52));
        about.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("Merlin 1.2")
            .setMessage("Player musical local e busca de metadados na internet. Sem assinatura, sem anúncios próprios. Não possui acesso ao áudio protegido do Spotify.")
            .setPositiveButton("OK",null).show());
        scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(scroll).setPositiveButton("Voltar",null).create();
        dialog.show();
    }
    private void openSettings(){
        android.content.SharedPreferences pref=getSharedPreferences("merlin_settings",0);
        boolean offline=pref.getBoolean("offline_only",false);
        boolean autoload=pref.getBoolean("auto_scan",true);
        String[] options={
            "Somente offline (desativa a busca na internet)",
            "Examinar automaticamente a biblioteca local",
            "Importar músicas baixadas",
            "Abrir configurações de áudio do Android",
            "Privacidade: dados locais, nenhuma conta necessária",
            "Exportar backup da biblioteca",
            "Restaurar backup da biblioteca"
        };
        boolean[] checked={offline,autoload,false,false,false,false,false};
        new AlertDialog.Builder(this).setTitle("Configurações do Merlin")
            .setMultiChoiceItems(options,checked,(d,index,checkedNow)->{
                if(index==0){pref.edit().putBoolean("offline_only",checkedNow).apply();scheduleOnlineSearch();renderTracks();}
                if(index==1)pref.edit().putBoolean("auto_scan",checkedNow).apply();
                if(index==2){pickAudio();}
                if(index==3){
                    try{startActivity(new Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL));}
                    catch(Exception e){toast("Seu Android não disponibilizou um equalizador");}
                }
                if(index==5)exportBackup();
                if(index==6)importBackup();
            })
            .setPositiveButton("Concluído",null).show();
    }

    private void showTrackOptions(Track track){
        ArrayList<String> personal=AudioLibrary.playlistNames(this);
        ArrayList<String> names=new ArrayList<>();
        ArrayList<String> ids=new ArrayList<>();
        for(String local:personal){
            names.add((AudioLibrary.playlistUris(this,local).contains(track.uri)?"✓ ":"＋ ")+local);
            ids.add("local:"+local);
        }
        for(CloudPlaylists.Room room:cloudRooms){
            names.add("☁  "+room.name+"  ("+(room.kind.equals(CloudPlaylists.MATCH)?"Match":"colaborativa")+")");
            ids.add("cloud:"+room.id);
        }
        names.add("＋ Criar playlist pessoal");ids.add("create");
        new AlertDialog.Builder(this).setTitle("Adicionar ou remover • "+track.title)
            .setItems(names.toArray(new String[0]),(dialog,index)->{
                String chosen=ids.get(index);
                if(chosen.equals("create")){createPlaylist();return;}
                if(chosen.startsWith("local:")){
                    AudioLibrary.togglePlaylistTrack(this,chosen.substring(6),track.uri);
                    renderTracks();toast("Playlist atualizada");return;
                }
                String roomId=chosen.substring(6);
                cloud.putSong(roomId,track,(success,error)->{
                    if(error!=null){showCloudError(error);return;}
                    toast("Música adicionada à playlist compartilhada");
                    loadCloudRooms();
                });
            }).setNegativeButton("Cancelar",null).show();
    }


    // O menu Criar tem 3 caminhos reais, não é apenas visual.
    private void openCreateMenu(){
        String[] options={"Playlist pessoal","Playlist colaborativa","Match musical (até 10 amigos)"};
        new AlertDialog.Builder(this).setTitle("Criar no Merlin")
            .setItems(options,(dialog,which)->{
                if(which==0)createPlaylist();
                else createCloudDialog(which==1?CloudPlaylists.COLLAB:CloudPlaylists.MATCH);
            }).setNegativeButton("Cancelar",null).show();
    }
    private void localPlaylistMenu(String name){
        new AlertDialog.Builder(this).setTitle(name)
            .setItems(new String[]{"Renomear playlist","Excluir playlist","Ver músicas"},(d,i)->{
                if(i==0){
                    EditText edit=new EditText(this);edit.setSingleLine(true);edit.setText(name);
                    new AlertDialog.Builder(this).setTitle("Novo nome").setView(edit)
                        .setPositiveButton("Salvar",(dialog,which)->{
                            String newName=edit.getText().toString().trim();
                            if(!AudioLibrary.renamePlaylist(this,name,newName)){
                                toast("Nome inválido ou já utilizado");return;
                            }
                            if(filter.equals(name))filter=newName;
                            renderTracks();toast("Playlist renomeada");
                        }).setNegativeButton("Cancelar",null).show();
                }else if(i==1){
                    new AlertDialog.Builder(this).setTitle("Excluir "+name+"?")
                        .setMessage("Somente a playlist será apagada. As músicas continuam no celular.")
                        .setPositiveButton("Excluir",(dialog,which)->{
                            if(AudioLibrary.deletePlaylist(this,name)){
                                filter="Todas";renderTracks();toast("Playlist excluída");
                            }else toast("Não foi possível excluir.");
                        }).setNegativeButton("Cancelar",null).show();
                }else{
                    currentScreen=2;filter=name;renderTracks();
                }
            }).setNegativeButton("Fechar",null).show();
    }
    private void showCloudError(String message){
        new AlertDialog.Builder(this).setTitle("Merlin • sincronização")
            .setMessage(message).setPositiveButton("Entendi",null).show();
    }
    private void needFirebase(){
        new AlertDialog.Builder(this).setTitle("Ativar playlists online")
            .setMessage("A sincronização com amigos usa Firebase Authentication e Realtime Database. É preciso configurar o projeto Firebase e recompilar o APK com os dados de conexão. Playlists pessoais funcionam sem isso.\n\nVeja FIREBASE-SETUP.md no repositório Merlin.")
            .setPositiveButton("Copiar guia",(d,w)->{
                String url="https://github.com/sryck352-netizen/Jhon-/blob/merlin/Merlin/FIREBASE-SETUP.md";
                ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE))
                    .setPrimaryClip(android.content.ClipData.newPlainText("Guia Merlin",url));
                toast("Link copiado");
            }).setNegativeButton("Fechar",null).show();
    }
    private void createCloudDialog(String kind){
        if(cloud==null||!cloud.configured()){needFirebase();return;}
        EditText name=new EditText(this);name.setSingleLine(true);
        name.setTextColor(TEXT);name.setHintTextColor(MUTED);
        name.setHint(kind.equals(CloudPlaylists.MATCH)?"Nome do Match":"Nome da playlist colaborativa");
        LinearLayout container=new LinearLayout(this);container.setPadding(dp(20),0,dp(20),0);
        container.addView(name);
        new AlertDialog.Builder(this)
            .setTitle(kind.equals(CloudPlaylists.MATCH)?"Novo Match musical":"Nova playlist colaborativa")
            .setView(container)
            .setMessage(kind.equals(CloudPlaylists.MATCH)
                ?"Convide até 10 amigos. As recomendações serão criadas a partir das curtidas que cada pessoa escolher compartilhar."
                :"Todos os participantes poderão adicionar ou remover músicas. Os arquivos de áudio não são enviados aos amigos.")
            .setPositiveButton("Criar",(d,w)->{
                String title=name.getText().toString().trim();
                if(title.length()<2||title.length()>60){toast("Use de 2 a 60 caracteres");return;}
                cloud.createRoom(title,kind,(room,error)->{
                    if(error!=null){showCloudError(error);return;}
                    toast(kind.equals(CloudPlaylists.MATCH)?"Match criado":"Playlist colaborativa criada");
                    loadCloudRooms();
                    showCloudRoom(room);
                });
            }).setNegativeButton("Cancelar",null).show();
    }
    private void loadCloudRooms(){
        if(cloud==null||!cloud.configured())return;
        cloud.listRooms((rooms,error)->{
            if(error==null&&rooms!=null){
                cloudRooms=rooms;
                if(currentScreen==0||currentScreen==2)renderTracks();
            }
        });
    }
    private void renderCloudSection(LinearLayout parent){
        if(cloudRooms.isEmpty()){
            TextView entry=action("♧   Playlists colaborativas e Match  •  entrar com convite",13,false);
            entry.setOnClickListener(v->openJoinDialog());
            parent.addView(entry,margins(0,2,0,10,-1,51));
            return;
        }
        parent.addView(section("Compartilhadas e Match"));
        for(CloudPlaylists.Room room:cloudRooms){
            String mark=room.kind.equals(CloudPlaylists.MATCH)?"✧ ":"♧ ";
            LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);
            TextView tile=text(mark,28,PURPLE,true);tile.setGravity(Gravity.CENTER);
            tile.setBackground(round(0xFF382554,9));line.addView(tile,lp(55,55));
            LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(dp(12),0,0,0);
            TextView title=text(room.name,15,TEXT,true);
            TextView subtitle=text(room.participants()+" participantes · "+room.songCount()+" músicas",12,MUTED,false);
            labels.addView(title);labels.addView(subtitle);
            line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            parent.addView(line,margins(0,0,0,9,-1,60));
            line.setOnClickListener(v->loadCloudRoom(room.id,true));
        }
        TextView enter=action("＋  Entrar com convite",12,false);
        enter.setOnClickListener(v->openJoinDialog());
        parent.addView(enter,margins(0,2,0,5,-1,46));
    }
    private void openJoinDialog(){
        if(cloud==null||!cloud.configured()){needFirebase();return;}
        EditText input=new EditText(this);
        input.setTextColor(TEXT);input.setHintTextColor(MUTED);
        input.setSingleLine(false);input.setMinLines(2);
        input.setHint("Cole merlin://join?room=...&code=... ou id:código");
        new AlertDialog.Builder(this).setTitle("Entrar com convite")
            .setView(input).setPositiveButton("Entrar",(d,w)->joinCloudRoom(input.getText().toString()))
            .setNegativeButton("Cancelar",null).show();
    }
    private void handleJoinIntent(Intent intent){
        if(intent==null||intent.getData()==null)return;
        Uri u=intent.getData();
        if(!"merlin".equals(u.getScheme())||!"join".equals(u.getHost()))return;
        String full=u.toString();
        new AlertDialog.Builder(this).setTitle("Convite para o Merlin")
            .setMessage("Deseja entrar nesta playlist compartilhada?")
            .setPositiveButton("Aceitar",(d,w)->joinCloudRoom(full))
            .setNegativeButton("Cancelar",null).show();
    }
    private void joinCloudRoom(String invite){
        if(cloud==null||!cloud.configured()){needFirebase();return;}
        cloud.join(invite,(room,error)->{
            if(error!=null){showCloudError(error);return;}
            toast("Você entrou em "+room.name);
            loadCloudRooms();showCloudRoom(room);
        });
    }
    private void loadCloudRoom(String roomId,boolean open){
        if(cloud==null||!cloud.configured())return;
        cloud.getRoom(roomId,(room,error)->{
            if(error!=null){showCloudError(error);return;}
            if(open)showCloudRoom(room);
            else if(activeRoomDialog!=null&&activeRoomDialog.isShowing()&&roomId.equals(activeRoomId))
                drawCloudRoom(room);
        });
    }
    private void showCloudRoom(CloudPlaylists.Room room){
        if(activeRoomDialog!=null&&activeRoomDialog.isShowing())activeRoomDialog.dismiss();
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        activeRoomContent=new LinearLayout(this);
        activeRoomContent.setOrientation(LinearLayout.VERTICAL);
        activeRoomContent.setPadding(dp(18),dp(12),dp(18),dp(20));
        activeRoomContent.setBackgroundColor(BG);
        scroll.addView(activeRoomContent);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(scroll)
            .setNegativeButton("Fechar",null).create();
        activeRoomDialog=dialog;activeRoomId=room.id;
        dialog.setOnDismissListener(d->{
            if(activeRoomDialog==dialog){
                if(roomPoll!=null)handler.removeCallbacks(roomPoll);
                if(activeSubscription!=null){activeSubscription.cancel();activeSubscription=null;}
                activeRoomDialog=null;activeRoomContent=null;activeRoomId="";
            }
        });
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-1);
        drawCloudRoom(room);
        startRealtimeRoom(room.id);
        roomPoll=new Runnable(){
            @Override public void run(){
                if(!dialog.isShowing()||!room.id.equals(activeRoomId))return;
                loadCloudRoom(room.id,false);
                handler.postDelayed(this,8500);
            }
        };
        handler.postDelayed(roomPoll,8500);
    }
    private void startRealtimeRoom(String roomId){
        if(cloud==null||!cloud.configured()||roomId==null||roomId.isEmpty())return;
        if(activeSubscription!=null){activeSubscription.cancel();activeSubscription=null;}
        activeSubscription=cloud.listen(roomId,(changed,error)->{
            if(changed&&roomId.equals(activeRoomId)&&activeRoomDialog!=null&&activeRoomDialog.isShowing())
                loadCloudRoom(roomId,false);
        });
    }
    private void drawCloudRoom(CloudPlaylists.Room room){
        if(activeRoomContent==null||activeRoomDialog==null||!activeRoomDialog.isShowing())return;
        LinearLayout page=activeRoomContent;page.removeAllViews();
        boolean match=CloudPlaylists.MATCH.equals(room.kind);
        TextView title=text((match?"✧ ":"♧ ")+room.name,24,TEXT,true);
        page.addView(title,margins(0,3,0,4,-1,48));
        page.addView(text((match?"Match musical":"Playlist colaborativa")+" · "+
            room.participants()+"/11 participantes",13,MUTED,false));
        StringBuilder names=new StringBuilder("Participantes: ");
        Iterator<String> participants=room.members.keys();
        while(participants.hasNext()){
            JSONObject member=room.members.optJSONObject(participants.next());
            if(member!=null){
                if(names.length()>15)names.append(", ");
                names.append(member.optString("name","Ouvinte"));
            }
        }
        TextView members=text(names.toString(),12,MUTED,false);
        members.setPadding(0,dp(10),0,dp(5));
        page.addView(members);
        if(room.owner.equals(cloud.userId())){
            TextView invite=action("⇧  Convidar amigos (até 10)",14,true);
            page.addView(invite,margins(0,8,0,7,-1,49));
            invite.setOnClickListener(v->cloud.invite(room.id,(link,error)->{
                if(error!=null){showCloudError(error);return;}
                new AlertDialog.Builder(this).setTitle("Convite criado")
                    .setMessage("Convite válido por 7 dias. Quem tiver o link poderá entrar até o limite de participantes.\n\n"+link)
                    .setPositiveButton("Compartilhar",(d,w)->shareInvite(link))
                    .setNeutralButton("Copiar",(d,w)->{
                        ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE))
                            .setPrimaryClip(android.content.ClipData.newPlainText("Convite Merlin",link));
                        toast("Convite copiado");
                    }).setNegativeButton("Fechar",null).show();
            }));
        }
        TextView add=action("＋  Adicionar música da biblioteca",14,false);
        page.addView(add,margins(0,8,0,5,-1,49));
        add.setOnClickListener(v->pickTrackForRoom(room.id));
        if(match){
            page.addView(section("Gostos musicais compartilhados"));
            TextView shareTaste=action("♥  Compartilhar minhas curtidas",14,false);
            page.addView(shareTaste,margins(0,0,0,10,-1,50));
            shareTaste.setOnClickListener(v->confirmTasteSharing(room.id));
            List<JSONObject> recommended=CloudPlaylists.recommendations(room,16);
            page.addView(section("Recomendações para o grupo"));
            if(recommended.isEmpty())
                page.addView(text("Nenhum participante compartilhou curtidas ainda.",13,MUTED,false));
            else for(JSONObject rec:recommended){
                TextView t=text(rec.optString("title")+" · "+rec.optString("artist")+
                    "   ♥ "+rec.optInt("votes",0),13,TEXT,false);
                t.setPadding(dp(8),dp(8),dp(6),dp(8));
                page.addView(t);
            }
            TextView generate=action("✧  Gerar playlist compartilhada",14,true);
            page.addView(generate,margins(0,11,0,5,-1,50));
            generate.setOnClickListener(v->{
                new AlertDialog.Builder(this).setTitle("Gerar playlist do Match?")
                    .setMessage("As recomendações serão adicionadas à playlist do grupo. Todos os participantes poderão vê-las e alterá-las.")
                    .setPositiveButton("Gerar",(d,w)->cloud.saveMatch(room.id,(count,error)->{
                        if(error!=null){showCloudError(error);return;}
                        toast(count==0?"Compartilhem curtidas para gerar recomendações":count+" recomendações processadas");
                        loadCloudRoom(room.id,false);loadCloudRooms();
                    })).setNegativeButton("Cancelar",null).show();
            });
        }
        page.addView(section("Músicas compartilhadas ("+room.songCount()+")"));
        if(room.songCount()==0)
            page.addView(text("Nenhuma música adicionada. Toque em Adicionar música para começar.",13,MUTED,false));
        Iterator<String> keys=room.songs.keys();
        while(keys.hasNext()){
            String hash=keys.next();JSONObject item=room.songs.optJSONObject(hash);
            if(item==null)continue;
            String songTitle=item.optString("title","Música");
            String artist=item.optString("artist","");
            TextView row=action("♫  "+songTitle+"\n     "+artist,13,false);
            page.addView(row,margins(0,0,0,6,-1,65));
            row.setOnClickListener(v->{
                new AlertDialog.Builder(this).setTitle(songTitle)
                    .setItems(new String[]{"Reproduzir (se disponível neste celular)","Remover da playlist"},(d,choice)->{
                        if(choice==0){
                            Track local=findTrack(songTitle,artist);
                            if(local==null){
                                Track remote=CloudPlaylists.audiusTrack(item.optString("audiusId",""),songTitle,artist);
                                if(remote!=null){playOnline(remote);return;}
                                toast("Esta música precisa estar no celular, ou ter uma versão autorizada no Audius.");return;
                            }
                            filter="Todas";visible=new ArrayList<>(tracks);
                            playSelection(local);
                        }else{
                            new AlertDialog.Builder(this).setTitle("Remover música?")
                                .setMessage("A música será removida da playlist de todos, mas nenhum arquivo será apagado.")
                                .setPositiveButton("Remover",(dialog,which)->cloud.removeSong(room.id,hash,(ok,error)->{
                                    if(error!=null){showCloudError(error);return;}
                                    toast("Música removida para todos");
                                    loadCloudRoom(room.id,false);
                                    loadCloudRooms();
                                })).setNegativeButton("Cancelar",null).show();
                        }
                    }).show();
            });
        }
        TextView manage=action(room.owner.equals(cloud.userId())?
            "⚙  Renomear ou excluir grupo":"◁  Sair desta playlist",13,false);
        page.addView(manage,margins(0,18,0,0,-1,46));
        manage.setOnClickListener(v->manageCloudRoom(room));
    }
    private void manageCloudRoom(CloudPlaylists.Room room){
        boolean owner=room.owner.equals(cloud.userId());
        new AlertDialog.Builder(this).setTitle("Gerenciar • "+room.name)
            .setItems(owner?new String[]{"Renomear grupo","Excluir grupo para todos"}:
                new String[]{"Sair da playlist"},(dialog,index)->{
                if(owner&&index==0){
                    EditText name=new EditText(this);name.setText(room.name);
                    new AlertDialog.Builder(this).setTitle("Novo nome").setView(name)
                        .setPositiveButton("Salvar",(d,w)->cloud.renameRoom(room.id,name.getText().toString(),(ok,error)->{
                            if(error!=null){showCloudError(error);return;}
                            toast("Grupo renomeado");loadCloudRoom(room.id,false);loadCloudRooms();
                        })).setNegativeButton("Cancelar",null).show();
                }else{
                    new AlertDialog.Builder(this).setTitle(owner?"Excluir para todos?":"Sair da playlist?")
                        .setMessage(owner?"Todos perderão o acesso e os metadados do grupo serão apagados.":"Você sairá da playlist e deixará de receber atualizações.")
                        .setPositiveButton(owner?"Excluir":"Sair",(d,w)->cloud.removeRoom(room.id,(ok,error)->{
                            if(error!=null){showCloudError(error);return;}
                            if(activeRoomDialog!=null)activeRoomDialog.dismiss();
                            toast(owner?"Grupo excluído":"Você saiu da playlist");
                            loadCloudRooms();
                        })).setNegativeButton("Cancelar",null).show();
                }
            }).show();
    }
    private void shareInvite(String link){
        Intent share=new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT,"Entre no meu Merlin: "+link);
        startActivity(Intent.createChooser(share,"Enviar convite do Merlin"));
    }
    private Track findTrack(String title,String artist){
        for(Track t:tracks){
            if(t.title.equalsIgnoreCase(title)&&t.artist.equalsIgnoreCase(artist))return t;
        }
        return null;
    }
    private void pickTrackForRoom(String roomId){
        if(tracks.isEmpty()){toast("Importe algumas músicas antes de adicionar");return;}
        String[] songs=new String[Math.min(250,tracks.size())];
        for(int i=0;i<songs.length;i++)songs[i]=tracks.get(i).title+" — "+tracks.get(i).artist;
        new AlertDialog.Builder(this).setTitle("Adicionar à playlist")
            .setItems(songs,(d,which)->{
                Track selected=tracks.get(which);
                cloud.putSong(roomId,selected,(ok,error)->{
                    if(error!=null){showCloudError(error);return;}
                    toast("Música sincronizada");
                    loadCloudRoom(roomId,false);loadCloudRooms();
                });
            }).setNegativeButton("Cancelar",null).show();
    }

    private ArrayList<Track> selectedMatchTastes(String roomId){
        android.content.SharedPreferences consent=getSharedPreferences("merlin_match_consent",0);
        HashSet<String> liked=AudioLibrary.favorites(this);
        ArrayList<Track> selected=new ArrayList<>();
        HashSet<String> used=new HashSet<>();
        for(Track t:tracks)if(liked.contains(t.uri)&&used.add(t.title.toLowerCase(Locale.ROOT)+"|"+t.artist.toLowerCase(Locale.ROOT)))selected.add(t);
        if(consent.getBoolean("history_"+roomId,false)){
            for(Track t:getRecent()){
                if(used.add(t.title.toLowerCase(Locale.ROOT)+"|"+t.artist.toLowerCase(Locale.ROOT)))selected.add(t);
            }
        }
        if(selected.size()>50)return new ArrayList<>(selected.subList(0,50));
        return selected;
    }
    private void refreshMatch(String roomId,boolean notify){
        ArrayList<Track> selected=selectedMatchTastes(roomId);
        cloud.publishTastes(roomId,selected,(ok,error)->{
            if(error!=null){if(notify)showCloudError(error);return;}
            cloud.saveMatch(roomId,(count,error2)->{
                if(error2!=null){if(notify)showCloudError(error2);return;}
                if(notify)toast("Match atualizado • "+count+" sugestões");
                if(roomId.equals(activeRoomId))loadCloudRoom(roomId,false);
                loadCloudRooms();
            });
        });
    }
    private void scheduleMatchUpdates(){
        if(cloud==null||!cloud.configured())return;
        if(matchUpdateRunnable!=null)handler.removeCallbacks(matchUpdateRunnable);
        matchUpdateRunnable=()->{
            android.content.SharedPreferences consent=getSharedPreferences("merlin_match_consent",0);
            for(CloudPlaylists.Room room:cloudRooms){
                if(CloudPlaylists.MATCH.equals(room.kind)&&consent.getBoolean("enabled_"+room.id,false))
                    refreshMatch(room.id,false);
            }
        };
        handler.postDelayed(matchUpdateRunnable,2600);
    }
    private void confirmTasteSharing(String roomId){
        android.content.SharedPreferences pref=getSharedPreferences("merlin_match_consent",0);
        final boolean[] includeHistory={pref.getBoolean("history_"+roomId,false)};
        new AlertDialog.Builder(this).setTitle("Permissões do Match")
            .setMessage("O grupo verá apenas títulos e artistas das suas músicas escolhidas. Você pode parar de compartilhar quando quiser.")
            .setMultiChoiceItems(new String[]{"Incluir minhas 20 músicas recentes (opcional)"},
                new boolean[]{includeHistory[0]},(dialog,which,checked)->includeHistory[0]=checked)
            .setPositiveButton("Compartilhar e atualizar",(d,w)->{
                pref.edit().putBoolean("enabled_"+roomId,true)
                    .putBoolean("history_"+roomId,includeHistory[0]).apply();
                if(selectedMatchTastes(roomId).isEmpty()){
                    pref.edit().putBoolean("enabled_"+roomId,false).apply();
                    toast("Curta músicas ou autorize seus recentes");return;
                }
                refreshMatch(roomId,true);
            }).setNeutralButton("Revogar acesso",(d,w)->{
                pref.edit().putBoolean("enabled_"+roomId,false).putBoolean("history_"+roomId,false).apply();
                cloud.revokeTastes(roomId,(success,error)->{
                    if(error!=null){showCloudError(error);return;}
                    cloud.saveMatch(roomId,(count,failure)->{
                        if(failure!=null)showCloudError(failure);
                        else {toast("Gostos removidos deste Match");loadCloudRoom(roomId,false);}
                    });
                });
            }).setNegativeButton("Cancelar",null).show();
    }

    private void playSelection(Track track){
        if(visible.isEmpty())return;
        remember(track);
        AudioLibrary.saveQueue(this,visible);
        Intent i=new Intent(this,MusicService.class).setAction(MusicService.ACTION_PLAY);
        i.putExtra("index",visible.indexOf(track));
        if(Build.VERSION.SDK_INT>=26) startForegroundService(i);else startService(i);
        updateMini();
    }

    private void command(String action){
        Intent i=new Intent(this,MusicService.class).setAction(action);
        startService(i);
        handler.postDelayed(this::updateMini,170);
    }
    private void seek(int position){
        Intent i=new Intent(this,MusicService.class).setAction(MusicService.ACTION_SEEK);
        i.putExtra("position",position);startService(i);
    }

    private void updateMini(){
        if(miniTitle==null)return;
        boolean active=!MusicService.currentUri.isEmpty();
        miniTitle.setText(active?MusicService.currentTitle:"Nada tocando");
        miniArtist.setText(active?MusicService.currentArtist:"Escolha uma música");
        miniPlay.setText(MusicService.playing?"❚❚":"▶");
        if(!MusicService.playbackError.isEmpty()&&!MusicService.playbackError.equals(lastShownPlaybackError)){
            lastShownPlaybackError=MusicService.playbackError;
            toast(lastShownPlaybackError);
        }
        if(miniSeek!=null && !miniSeek.isPressed()){
            miniSeek.setMax(Math.max(1,MusicService.durationMs));
            miniSeek.setProgress(Math.max(0,MusicService.positionMs));
        }
    }

    private static String time(int ms){
        long seconds=Math.max(0,ms)/1000;
        return String.format(Locale.getDefault(),"%d:%02d",seconds/60,seconds%60);
    }

    private void showPlayer(){
        if(MusicService.currentUri.isEmpty()) {toast("Escolha uma música primeiro");return;}
        Dialog d=new Dialog(this); playerDialog=d;
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24),dp(20),dp(24),dp(30));root.setBackgroundColor(BG);
        TextView back=text("⌄    Tocando agora",18,TEXT,true);back.setOnClickListener(v->d.dismiss());root.addView(back,lp(-1,54));
        FrameLayout art=new FrameLayout(this);art.setBackground(gradient());
        TextView mark=text("✧\n♫",90,0xFFEDE5FF,true);mark.setGravity(Gravity.CENTER);
        art.addView(mark,new FrameLayout.LayoutParams(-1,-1));
        for(Track selected:tracks){
            if(selected.uri.equals(MusicService.currentUri)){
                art.addView(cover(selected,300),new FrameLayout.LayoutParams(-1,-1));
                break;
            }
        }
        LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(-1,0,1);al.setMargins(0,dp(23),0,dp(28));root.addView(art,al);
        LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout nameBox=new LinearLayout(this);nameBox.setOrientation(LinearLayout.VERTICAL);
        TextView song=text(MusicService.currentTitle,23,TEXT,true);song.setSingleLine(true);song.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView artist=text(MusicService.currentArtist,15,MUTED,false);
        nameBox.addView(song);nameBox.addView(artist);
        titleRow.addView(nameBox,new LinearLayout.LayoutParams(0,-2,1));
        TextView fav=text(AudioLibrary.favorites(this).contains(MusicService.currentUri)?"♥":"♡",30,PURPLE,true);
        titleRow.addView(fav,lp(40,50));
        fav.setOnClickListener(v->{boolean selected=AudioLibrary.toggleFavorite(this,MusicService.currentUri);fav.setText(selected?"♥":"♡");scheduleMatchUpdates();renderTracks();});
        root.addView(titleRow);
        SeekBar seekBar=new SeekBar(this);seekBar.setMax(Math.max(1,MusicService.durationMs));
        seekBar.setProgress(MusicService.positionMs);
        seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        LinearLayout.LayoutParams sl=lp(-1,46);sl.topMargin=dp(19);root.addView(seekBar,sl);
        LinearLayout timer=new LinearLayout(this);TextView pos=text("0:00",12,MUTED,false);TextView total=text("0:00",12,MUTED,false);
        timer.addView(pos,new LinearLayout.LayoutParams(0,-2,1));timer.addView(total);root.addView(timer);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean fromUser){if(fromUser)pos.setText(time(p));}
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){seek(s.getProgress());}
        });
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams cl=lp(-1,80);cl.topMargin=dp(19);
        TextView shuffle=text("⇄",25,MUTED,false);shuffle.setGravity(Gravity.CENTER);
        TextView prev=text("⏮",29,TEXT,true);prev.setGravity(Gravity.CENTER);
        TextView toggle=text("▶",30,BG,true);toggle.setGravity(Gravity.CENTER);toggle.setBackground(round(PURPLE,60));
        TextView next=text("⏭",29,TEXT,true);next.setGravity(Gravity.CENTER);
        TextView repeat=text("↻",27,MUTED,false);repeat.setGravity(Gravity.CENTER);
        controls.addView(shuffle,new LinearLayout.LayoutParams(0,dp(55),1));
        controls.addView(prev,new LinearLayout.LayoutParams(0,dp(55),1));
        controls.addView(toggle,lp(72,72));
        controls.addView(next,new LinearLayout.LayoutParams(0,dp(55),1));
        controls.addView(repeat,new LinearLayout.LayoutParams(0,dp(55),1));
        root.addView(controls,cl);
        shuffle.setOnClickListener(v->command(MusicService.ACTION_SHUFFLE));
        repeat.setOnClickListener(v->command(MusicService.ACTION_REPEAT));
        prev.setOnClickListener(v->command(MusicService.ACTION_PREVIOUS));
        toggle.setOnClickListener(v->command(MusicService.ACTION_TOGGLE));
        next.setOnClickListener(v->command(MusicService.ACTION_NEXT));
        LinearLayout extraControls=new LinearLayout(this);extraControls.setGravity(Gravity.CENTER);
        TextView lyrics=action("♫  Letras",13,false);
        TextView share=action("⇧  Compartilhar",13,false);
        TextView sleep=action("◷  Timer",13,false);
        extraControls.addView(lyrics,new LinearLayout.LayoutParams(0,dp(51),1));
        LinearLayout.LayoutParams spread=new LinearLayout.LayoutParams(0,dp(51),1);
        spread.setMargins(dp(6),0,dp(6),0);
        extraControls.addView(share,spread);
        extraControls.addView(sleep,new LinearLayout.LayoutParams(0,dp(51),1));
        root.addView(extraControls,margins(0,11,0,0,-1,51));
        lyrics.setOnClickListener(v->openLyrics());
        share.setOnClickListener(v->shareCurrent());
        sleep.setOnClickListener(v->showSleepTimer());
        TextView footer=text("MERLIN  •  REPRODUÇÃO LOCAL",12,MUTED,true);footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fl=lp(-1,42);fl.topMargin=dp(14);root.addView(footer,fl);
        d.setContentView(root);Window w=d.getWindow();
        if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(-1,-1);}
        d.show();w=d.getWindow();if(w!=null){w.setLayout(-1,-1);w.setStatusBarColor(BG);w.setNavigationBarColor(BG);}
        Runnable sync=new Runnable(){
            @Override public void run(){
                if(!d.isShowing())return;
                song.setText(MusicService.currentTitle);artist.setText(MusicService.currentArtist);
                toggle.setText(MusicService.playing?"❚❚":"▶");
                shuffle.setTextColor(MusicService.shuffle?PURPLE:MUTED);
                repeat.setText(MusicService.repeat==2?"↻¹":"↻");
                repeat.setTextColor(MusicService.repeat>0?PURPLE:MUTED);
                if(!seekBar.isPressed()){
                    seekBar.setMax(Math.max(1,MusicService.durationMs));seekBar.setProgress(MusicService.positionMs);
                    pos.setText(time(MusicService.positionMs));
                }
                total.setText(time(MusicService.durationMs));
                fav.setText(AudioLibrary.favorites(MainActivity.this).contains(MusicService.currentUri)?"♥":"♡");
                handler.postDelayed(this,700);
            }
        };handler.post(sync);
    }


    private void openLyrics(){
        String key=MusicService.currentUri;
        String lyrics=getSharedPreferences("merlin_lyrics",0).getString(key,"");
        new AlertDialog.Builder(this).setTitle("Letras • "+MusicService.currentTitle)
            .setMessage(lyrics.isEmpty()
                ?"Importe um arquivo de letras .lrc ou .txt dessa música para acompanhar a letra. O Merlin não baixa letras protegidas sem autorização."
                :lyrics.replaceAll("(?m)^\\[[0-9:.]+\\]","").trim())
            .setPositiveButton("Importar letra",(d,w)->{
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/*");
                startActivityForResult(intent,101);
            }).setNegativeButton("Fechar",null).show();
    }
    private void shareCurrent(){
        if(MusicService.currentUri.isEmpty()){toast("Selecione uma música");return;}
        Intent share=new Intent(Intent.ACTION_SEND);
        share.setType("audio/*");
        share.putExtra(Intent.EXTRA_STREAM,Uri.parse(MusicService.currentUri));
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivity(Intent.createChooser(share,"Compartilhar música"));}
        catch(Exception e){toast("Esse arquivo não pode ser compartilhado");}
    }
    private void showSleepTimer(){
        String[] times={"5 minutos","15 minutos","30 minutos","60 minutos","Cancelar timer"};
        new AlertDialog.Builder(this).setTitle("Parar música automaticamente")
            .setItems(times,(d,index)->{
                if(timerStop!=null)handler.removeCallbacks(timerStop);
                timerStop=null;
                if(index==4){toast("Timer cancelado");return;}
                int[] mins={5,15,30,60};
                timerStop=()->command(MusicService.ACTION_PAUSE);
                handler.postDelayed(timerStop,mins[index]*60_000L);
                toast("Música vai parar em "+times[index]);
            }).show();
    }

    private void exportBackup(){
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE,"Merlin-Backup.json");
        startActivityForResult(intent,150);
    }
    private void importBackup(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent,151);
    }
    private void writeBackup(Uri dest){
        worker.execute(()->{
            try{
                android.content.SharedPreferences p=getSharedPreferences("merlin_library",0);
                JSONObject root=new JSONObject().put("format","merlin-library-backup")
                    .put("version",1).put("playlists",new JSONObject(p.getString("playlists","{}")))
                    .put("imports",new JSONArray(p.getString("imports","[]")))
                    .put("online_library",new JSONArray(p.getString("online_library","[]")))
                    .put("recent_sequence",p.getString("recent_sequence",""));
                JSONArray liked=new JSONArray();
                for(String uri:AudioLibrary.favorites(this))liked.put(uri);
                root.put("favorites",liked);
                try(java.io.OutputStream out=getContentResolver().openOutputStream(dest,"w")){
                    if(out==null)throw new Exception("Arquivo indisponível");
                    out.write(root.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                handler.post(()->toast("Backup da biblioteca salvo"));
            }catch(Exception e){handler.post(()->toast("Erro ao exportar: "+e.getMessage()));}
        });
    }
    private void readBackup(Uri source){
        worker.execute(()->{
            try(java.io.InputStream in=getContentResolver().openInputStream(source)){
                if(in==null)throw new Exception("Arquivo indisponível");
                ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                byte[] chunk=new byte[4096];int n;
                while((n=in.read(chunk))!=-1){
                    buffer.write(chunk,0,n);
                    if(buffer.size()>1_500_000)throw new Exception("Backup maior que o esperado.");
                }
                JSONObject root=new JSONObject(buffer.toString("UTF-8"));
                if(!"merlin-library-backup".equals(root.optString("format"))||root.optInt("version")!=1)
                    throw new Exception("Esse arquivo não é um backup válido do Merlin.");
                // Validar ANTES de modificar as preferências.
                JSONObject playlists=root.getJSONObject("playlists");
                JSONArray imports=root.getJSONArray("imports");
                JSONArray remote=root.getJSONArray("online_library");
                JSONArray favorites=root.getJSONArray("favorites");
                String recent=root.optString("recent_sequence","");
                HashSet<String> liked=new HashSet<>();
                for(int i=0;i<favorites.length();i++){
                    String uri=favorites.getString(i);
                    if(uri.length()<=2048)liked.add(uri);
                }
                android.content.SharedPreferences.Editor edit=getSharedPreferences("merlin_library",0).edit();
                edit.putString("playlists",playlists.toString());
                edit.putString("imports",imports.toString());
                edit.putString("online_library",remote.toString());
                edit.putString("recent_sequence",recent);
                edit.putStringSet("favorites",liked);
                if(!edit.commit())throw new Exception("Falha ao salvar dados restaurados.");
                handler.post(()->{loadTracks();toast("Favoritos e playlists restaurados");});
            }catch(Exception e){handler.post(()->toast("Erro na restauração: "+e.getMessage()));}
        });
    }

    private void checkPermission(){
        String permission=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE;
        if(checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED)loadTracks();
        else requestPermissions(new String[]{permission},42);
    }
    @Override public void onRequestPermissionsResult(int req,String[] perm,int[] grants){
        super.onRequestPermissionsResult(req,perm,grants);
        if(req==42) { if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)loadTracks();
            else {toast("Você pode importar músicas manualmente");loadTracks();} }
    }
    private void pickAudio(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("audio/*");i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,99);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==150&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            writeBackup(data.getData());return;
        }
        if(request==151&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri backupUri=data.getData();
            new AlertDialog.Builder(this).setTitle("Restaurar backup?")
                .setMessage("Esta ação substitui seus favoritos e playlists atuais. Importe novamente os arquivos se o Android perder o acesso após uma reinstalação.")
                .setPositiveButton("Restaurar",(d,w)->readBackup(backupUri))
                .setNegativeButton("Cancelar",null).show();
            return;
        }
        if(request==101 && result==RESULT_OK && data!=null && data.getData()!=null){
            final String songUri=MusicService.currentUri;
            Uri uri=data.getData();
            worker.execute(()->{
                try(InputStream input=getContentResolver().openInputStream(uri)){
                    if(input==null)return;
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                    byte[] buffer=new byte[4096];int read;
                    while((read=input.read(buffer))!=-1 && bytes.size()<250000)bytes.write(buffer,0,read);
                    String lyric=bytes.toString("UTF-8");
                    getSharedPreferences("merlin_lyrics",0).edit().putString(songUri,lyric).apply();
                    handler.post(()->toast("Letra importada"));
                }catch(Exception e){handler.post(()->toast("Não consegui importar esta letra"));}
            });return;
        }
        if(request!=99||result!=RESULT_OK||data==null)return;
        ArrayList<Uri> uris=new ArrayList<>();
        if(data.getClipData()!=null){
            for(int x=0;x<data.getClipData().getItemCount();x++)uris.add(data.getClipData().getItemAt(x).getUri());
        } else if(data.getData()!=null) uris.add(data.getData());
        if(uris.isEmpty())return;
        for(Uri uri:uris){
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        }
        worker.execute(()->{
            for(Uri uri:uris)AudioLibrary.addImport(getApplicationContext(),uri);
            runOnUiThread(()->{toast(uris.size()+" música(s) importada(s)");loadTracks();});
        });
    }
    private void loadTracks(){
        countText.setText("Carregando músicas...");
        worker.execute(()->{
            List<Track> loaded=AudioLibrary.getTracks(getApplicationContext());
            runOnUiThread(()->{
                tracks.clear();tracks.addAll(loaded);renderTracks();
            });
        });
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
