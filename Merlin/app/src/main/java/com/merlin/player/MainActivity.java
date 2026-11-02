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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final int BG = Color.rgb(9,8,19);
    private final int SURFACE = Color.rgb(25,22,42);
    private final int PURPLE = Color.rgb(165,113,255);
    private final int TEXT = Color.rgb(248,246,255);
    private final int MUTED = Color.rgb(166,160,185);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Track> tracks = new ArrayList<>();
    private List<Track> visible = new ArrayList<>();
    private LinearLayout songsContainer, playlistBar;
    private TextView countText, miniTitle, miniArtist, miniPlay, miniArt;
    private SeekBar miniSeek;
    private String search = "", filter = "Todas";
    private Dialog playerDialog;
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
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        if(Build.VERSION.SDK_INT>=35) root.setOnApplyWindowInsetsListener((v,insets)-> {
            android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(0,bars.top,0,bars.bottom);return insets;
        });
        setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(22),dp(18),dp(22),dp(11));
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);
        TextView title=text("✧ Merlin",32,TEXT,true);brand.addView(title);
        brand.addView(text("Seu universo musical",12,MUTED,false));
        header.addView(brand,new LinearLayout.LayoutParams(0,dp(62),1));
        TextView importBtn=action("+ Importar",13,true);
        importBtn.setOnClickListener(v->pickAudio());
        header.addView(importBtn); root.addView(header);

        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(gradient());hero.setPadding(dp(22),dp(20),dp(22),dp(20));
        LinearLayout.LayoutParams heroLp=lp(-1,-2);heroLp.setMargins(dp(18),dp(10),dp(18),dp(17));
        hero.addView(text("MÚSICA SEM LIMITES",11,0xFFE5C7FF,true));
        TextView heroTitle=text("Toda a sua música.\\nEm um só lugar.",23,TEXT,true);
        heroTitle.setText("Toda a sua música.\nEm um só lugar.");
        LinearLayout.LayoutParams ht=lp(-1,-2);ht.topMargin=dp(8);hero.addView(heroTitle,ht);
        TextView heroInfo=text("Ouça seus downloads do Snaptube e do celular, inclusive offline.",13,0xFFD4CBE5,false);
        LinearLayout.LayoutParams hi=lp(-1,-2);hi.topMargin=dp(10);hero.addView(heroInfo,hi);
        LinearLayout hBtns=new LinearLayout(this);hBtns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams hb=lp(-1,-2);hb.topMargin=dp(17);
        TextView importHero=action("Importar músicas",13,true);
        importHero.setOnClickListener(v->pickAudio());hBtns.addView(importHero);
        TextView refresh=action("↻",20,false);
        LinearLayout.LayoutParams rf=lp(50,44);rf.leftMargin=dp(10);
        refresh.setOnClickListener(v->loadTracks());hBtns.addView(refresh,rf);
        hero.addView(hBtns,hb);root.addView(hero,heroLp);

        EditText searchField=new EditText(this);searchField.setSingleLine(true);searchField.setTextColor(TEXT);
        searchField.setHintTextColor(MUTED);searchField.setTextSize(14);searchField.setHint("⌕   Buscar músicas e artistas");
        searchField.setBackground(round(SURFACE,16));searchField.setPadding(dp(17),0,dp(15),0);
        LinearLayout.LayoutParams sf=lp(-1,49);sf.setMargins(dp(18),0,dp(18),dp(16));
        root.addView(searchField,sf);
        searchField.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) { search=s.toString().toLowerCase(Locale.ROOT);renderTracks(); }
            @Override public void afterTextChanged(Editable e) {}
        });

        HorizontalScrollView tabs=new HorizontalScrollView(this);tabs.setHorizontalScrollBarEnabled(false);
        LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setPadding(dp(18),0,dp(12),dp(6));
        playlistBar=wrap;tabs.addView(wrap);root.addView(tabs,lp(-1,55));
        countText=text("Carregando músicas...",13,MUTED,false);
        countText.setPadding(dp(20),dp(8),0,dp(8));root.addView(countText);

        ScrollView scroller=new ScrollView(this);scroller.setFillViewport(false);
        songsContainer=new LinearLayout(this);songsContainer.setOrientation(LinearLayout.VERTICAL);
        songsContainer.setPadding(dp(14),dp(3),dp(14),dp(12));scroller.addView(songsContainer);
        root.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));
        buildMini(root);
        renderTabs();
        renderTracks();
    }

    private void buildMini(LinearLayout root) {
        LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackground(round(0xFF2B2144,15));
        LinearLayout.LayoutParams wl=lp(-1,-2);wl.setMargins(dp(10),dp(5),dp(10),dp(10));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10),dp(8),dp(8),dp(5));
        miniArt=text("♫",26,TEXT,true);miniArt.setGravity(Gravity.CENTER);miniArt.setBackground(round(0xFF7046A7,11));
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
        wrap.addView(miniSeek,lp(-1,25));root.addView(wrap,wl);
    }

    private void renderTabs(){
        playlistBar.removeAllViews();
        ArrayList<String> labels=new ArrayList<>();labels.add("Todas");labels.add("Curtidas");
        labels.addAll(AudioLibrary.playlistNames(this));
        for(String label:labels){
            boolean selected=label.equals(filter);
            TextView t=action(label,13,selected);
            if(!selected)t.setTextColor(MUTED);
            LinearLayout.LayoutParams tl=lp(-2,40);tl.rightMargin=dp(8);playlistBar.addView(t,tl);
            t.setOnClickListener(v->{filter=label;renderTabs();renderTracks();});
        }
        TextView create=action("+ Playlist",13,false);
        playlistBar.addView(create,lp(-2,40));create.setOnClickListener(v->createPlaylist());
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
        HashSet<String> fav=AudioLibrary.favorites(this);
        HashSet<String> chosen = filter.equals("Todas") || filter.equals("Curtidas") ? null : AudioLibrary.playlistUris(this,filter);
        visible=new ArrayList<>();
        for(Track t:tracks){
            if(filter.equals("Curtidas") && !fav.contains(t.uri))continue;
            if(chosen!=null && !chosen.contains(t.uri))continue;
            if(!search.isEmpty() && !(t.title.toLowerCase(Locale.ROOT).contains(search) || t.artist.toLowerCase(Locale.ROOT).contains(search)))continue;
            visible.add(t);
        }
        countText.setText(visible.size()+" músicas" + (visible.isEmpty()?" • Importe ou atualize sua biblioteca":""));
        songsContainer.removeAllViews();
        if(visible.isEmpty()){
            TextView empty=text("Nenhuma música por aqui ainda.\n\nToque em Importar para escolher MP3, M4A ou outros arquivos de áudio.",14,MUTED,false);
            empty.setGravity(Gravity.CENTER);empty.setPadding(dp(26),dp(40),dp(26),dp(40));
            songsContainer.addView(empty);
            return;
        }
        int renderCount=Math.min(visible.size(),400);
        for(int i=0;i<renderCount;i++){
            Track track=visible.get(i);
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(7),dp(8),dp(6),dp(8));
            LinearLayout.LayoutParams rl=lp(-1,69);rl.bottomMargin=dp(2);songsContainer.addView(row,rl);
            TextView art=text("♫",23,PURPLE,true);art.setGravity(Gravity.CENTER);art.setBackground(round(SURFACE,11));
            row.addView(art,lp(50,50));
            LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(13),0,dp(3),0);
            TextView songTitle=text(track.title,15,TEXT,true);songTitle.setSingleLine(true);songTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView artist=text(track.artist+"  ·  "+time((int)track.duration),12,MUTED,false);
            artist.setSingleLine(true);artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(songTitle);info.addView(artist);row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            TextView heart=text(fav.contains(track.uri)?"♥":"♡",25,fav.contains(track.uri)?PURPLE:MUTED,false);
            heart.setGravity(Gravity.CENTER);row.addView(heart,lp(42,46));
            heart.setOnClickListener(v->{AudioLibrary.toggleFavorite(this,track.uri);renderTracks();});
            row.setOnClickListener(v->playSelection(track));
            row.setOnLongClickListener(v->{showTrackOptions(track);return true;});
        }
        if(visible.size()>renderCount){TextView more=text("Exibindo as primeiras "+renderCount+" faixas. Use a pesquisa para encontrar outras.",12,MUTED,false);more.setPadding(dp(16),dp(12),dp(16),dp(18));songsContainer.addView(more);}
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
        TextView footer=text("MERLIN  •  MÚSICAS OFFLINE",12,MUTED,true);footer.setGravity(Gravity.CENTER);
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
