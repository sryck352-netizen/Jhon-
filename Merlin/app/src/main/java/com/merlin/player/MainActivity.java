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
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final int BG = 0xFF111111;
    private final int SURFACE = 0xFF292929;
    private final int PURPLE = 0xFF1ED760;
    private final int TEXT = 0xFFF9F9F9;
    private final int MUTED = 0xFFAAAAAA;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService onlineWorker = Executors.newFixedThreadPool(3);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Track> tracks = new ArrayList<>();
    private List<Track> visible = new ArrayList<>();
    private LinearLayout songsContainer, playlistBar, navBottom;
    private EditText searchField;
    private int currentScreen = 0; // 0 início, 1 busca, 2 biblioteca
    private final LruCache<String,Bitmap> artCache = new LruCache<>(80);
    private TextView countText, miniTitle, miniArtist, miniPlay, miniArt;
    private SeekBar miniSeek;
    private String search = "", filter = "Todas";
    private Dialog playerDialog;
    private List<OnlineCatalog.Song> onlineResults = new ArrayList<>();
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
        buildUI();
        checkPermission();
        handler.post(new Runnable(){
            @Override public void run(){updateMini();handler.postDelayed(this,700);}
        });
    }

    @Override protected void onStart() {
        super.onStart();
        if(Build.VERSION.SDK_INT>=33) registerReceiver(playbackReceiver,new IntentFilter(MusicService.ACTION_UPDATE),Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(playbackReceiver,new IntentFilter(MusicService.ACTION_UPDATE));
        updateMini();
    }
    @Override protected void onStop() {
        try{unregisterReceiver(playbackReceiver);}catch(Exception ignored){}
        super.onStop();
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

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18),dp(12),dp(14),dp(12));
        TextView badge=text("M",19,0xFFEADFFF,true);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable bg=round(0xFF57348C,99);
        badge.setBackground(bg);
        top.addView(badge,lp(39,39));
        badge.setOnClickListener(v->openProfile());
        top.addView(chip("Tudo",true,()->{filter="Todas";currentScreen=0;renderTracks();}),margins(9,0,0,0, -2,38));
        top.addView(chip("Música",false,()->{filter="Todas";currentScreen=2;renderTracks();}),margins(8,0,0,0,-2,38));
        top.addView(chip("Curtidas",false,()->{filter="Curtidas";currentScreen=2;renderTracks();}),margins(8,0,0,0,-2,38));
        root.addView(top);
        // A biblioteca e a busca ocupam a mesma área, sem páginas web.
        searchField=new EditText(this);
        searchField.setSingleLine(true);
        searchField.setTextColor(TEXT);
        searchField.setTextSize(15);
        searchField.setHintTextColor(MUTED);
        searchField.setHint("⌕  O que você quer ouvir?");
        searchField.setPadding(dp(15),0,dp(15),0);
        searchField.setBackground(round(0xFF2C2C2C,10));
        LinearLayout.LayoutParams sp=margins(18,1,18,8,-1,50);
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
    private void updateNavigation(){
        if(navBottom==null)return;
        navBottom.removeAllViews();
        addNav("⌂","Início",0,()->{currentScreen=0;filter="Todas";search="";renderTracks();});
        addNav("⌕","Buscar",1,()->{currentScreen=1;filter="Todas";renderTracks();searchField.requestFocus();});
        addNav("▥","Biblioteca",2,()->{currentScreen=2;renderTracks();});
        addNav("+","Criar",-1,()->createPlaylist());
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
                AudioLibrary.createPlaylist(this,name);filter=name;renderTabs();renderTracks();
            }).setNegativeButton("Cancelar",null).show();
    }


    private void renderTracks(){
        if(songsContainer==null)return;
        songsContainer.removeAllViews();
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
            songsContainer.addView(section("Buscar no Merlin"));
            TextView hint=text("Músicas baixadas no seu celular",13,MUTED,false);
            songsContainer.addView(hint);
            showTrackList(songsContainer,visible,50);
            songsContainer.addView(section("Resultados na internet"));
            TextView source=text("Catálogo musical público • a reprodução online acontece no serviço de origem",12,MUTED,false);
            source.setPadding(0,0,0,dp(10));
            songsContainer.addView(source);
            if(onlineLoading){
                TextView loading=text("Pesquisando músicas e artistas...",13,MUTED,false);
                songsContainer.addView(loading,margins(0,8,0,12,-1,38));
            }else if(onlineResults.isEmpty()){
                TextView empty=text(onlineMessage,13,MUTED,false);
                songsContainer.addView(empty,margins(0,8,0,12,-1,45));
            }else{
                for(OnlineCatalog.Song song:onlineResults)onlineRow(songsContainer,song);
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
            onlineLoading=false;onlineResults=new ArrayList<>();
            onlineMessage="Pesquise um título, álbum ou artista.";return;
        }
        if(getSharedPreferences("merlin_settings",0).getBoolean("offline_only",false)){
            onlineLoading=false;onlineResults=new ArrayList<>();
            onlineMessage="Busca online desativada no modo offline.";return;
        }
        onlineLoading=true;
        String term=search;
        pendingSearch=()->{
            onlineWorker.execute(()->{
                List<OnlineCatalog.Song> received;
                String message;
                try {
                    received=OnlineCatalog.search(term);
                    message=received.isEmpty()?"Nenhum resultado encontrado.":"";
                }catch(Exception e){
                    received=new ArrayList<>();
                    message="Não consegui acessar o catálogo. Verifique sua internet.";
                }
                final List<OnlineCatalog.Song> response=received;
                final String status=message;
                handler.post(()->{
                    if(request!=onlineRequest || currentScreen!=1)return;
                    onlineResults=response;onlineMessage=status;
                    onlineLoading=false;renderTracks();
                });
            });
        };
        handler.postDelayed(pendingSearch,370);
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
            "Privacidade: dados locais, nenhuma conta necessária"
        };
        boolean[] checked={offline,autoload,false,false,false};
        new AlertDialog.Builder(this).setTitle("Configurações do Merlin")
            .setMultiChoiceItems(options,checked,(d,index,checkedNow)->{
                if(index==0){pref.edit().putBoolean("offline_only",checkedNow).apply();scheduleOnlineSearch();renderTracks();}
                if(index==1)pref.edit().putBoolean("auto_scan",checkedNow).apply();
                if(index==2){pickAudio();}
                if(index==3){
                    try{startActivity(new Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL));}
                    catch(Exception e){toast("Seu Android não disponibilizou um equalizador");}
                }
            })
            .setPositiveButton("Concluído",null).show();
    }

    private void showTrackOptions(Track track){
        ArrayList<String> names=AudioLibrary.playlistNames(this);
        if(names.isEmpty()){new AlertDialog.Builder(this).setMessage("Crie uma playlist para adicionar músicas.")
            .setPositiveButton("Criar playlist",(d,w)->createPlaylist()).setNegativeButton("Cancelar",null).show();return;}
        String[] labels=new String[names.size()];
        for(int i=0;i<names.size();i++) labels[i]=(AudioLibrary.playlistUris(this,names.get(i)).contains(track.uri)?"✓  ":"+  ")+names.get(i);
        new AlertDialog.Builder(this).setTitle("Adicionar/remover da playlist")
            .setItems(labels,(d,which)->{
                String name=names.get(which);AudioLibrary.togglePlaylistTrack(this,name,track.uri);renderTracks();toast("Playlist atualizada");
            }).show();
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
        fav.setOnClickListener(v->{boolean selected=AudioLibrary.toggleFavorite(this,MusicService.currentUri);fav.setText(selected?"♥":"♡");renderTracks();});
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
