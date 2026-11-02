package com.merlin.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MusicService extends Service {
    public static final String ACTION_PLAY = "com.merlin.player.PLAY";
    public static final String ACTION_PAUSE = "com.merlin.player.PAUSE";
    public static final String ACTION_TOGGLE = "com.merlin.player.TOGGLE";
    public static final String ACTION_NEXT = "com.merlin.player.NEXT";
    public static final String ACTION_PREVIOUS = "com.merlin.player.PREVIOUS";
    public static final String ACTION_SHUFFLE = "com.merlin.player.SHUFFLE";
    public static final String ACTION_REPEAT = "com.merlin.player.REPEAT";
    public static final String ACTION_SEEK = "com.merlin.player.SEEK";
    public static final String ACTION_UPDATE = "com.merlin.player.UPDATE";
    public static volatile String currentTitle = "Nenhuma música";
    public static volatile String currentArtist = "Escolha uma faixa para começar";
    public static volatile String currentUri = "";
    public static volatile int positionMs = 0;
    public static volatile int durationMs = 0;
    public static volatile boolean playing = false;
    public static volatile boolean shuffle = false;
    public static volatile int repeat = 0; // 0=off, 1=all, 2=one
    public static volatile int index = -1;
    public static volatile String playbackError = "";

    private static final String CHANNEL_ID = "merlin_playback";
    private static final int NOTIFICATION_ID = 101;
    private final List<Track> queue = new ArrayList<>();
    private MediaPlayer mediaPlayer;
    private MediaSession session;
    private AudioManager audioManager;
    private boolean isReady = false;
    private final Random random = new Random();

    @Override public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager)getSystemService(AUDIO_SERVICE);
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Reprodução do Merlin", NotificationManager.IMPORTANCE_LOW));
        session = new MediaSession(this, "MerlinPlayback");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resume(); }
            @Override public void onPause() { pause(); }
            @Override public void onSkipToNext() { next(false); }
            @Override public void onSkipToPrevious() { previous(); }
            @Override public void onSeekTo(long pos) { seek((int)pos); }
            @Override public void onStop() { pause(); }
        });
        session.setActive(true);
        updateMediaSession();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (action == null) return START_NOT_STICKY;
        switch(action) {
            case ACTION_PLAY: {
                queue.clear();
                queue.addAll(AudioLibrary.loadQueue(this));
                if (!queue.isEmpty()) playIndex(intent.getIntExtra("index", 0));
                break;
            }
            case ACTION_TOGGLE: if (playing) pause(); else resume(); break;
            case ACTION_PAUSE: pause(); break;
            case ACTION_NEXT: next(false); break;
            case ACTION_PREVIOUS: previous(); break;
            case ACTION_SHUFFLE: shuffle = !shuffle; notifyAndBroadcast(); break;
            case ACTION_REPEAT: repeat = (repeat+1)%3; notifyAndBroadcast(); break;
            case ACTION_SEEK: seek(intent.getIntExtra("position",0)); break;
        }
        return START_NOT_STICKY;
    }

    private void playIndex(int wanted) {
        if(queue.isEmpty()) return;
        index = Math.max(0,Math.min(wanted,queue.size()-1));
        Track track = queue.get(index);
        disposePlayer();
        currentTitle=track.title; currentArtist=track.artist; currentUri=track.uri;
        durationMs=(int)Math.min(Integer.MAX_VALUE,track.duration); positionMs=0;
        playing=false;
        playbackError = "";
        try {
            mediaPlayer=new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            if(AudiusCatalog.canPlay(track)){
                // Http(s) streaming autorizado, sem navegador ou links externos.
                mediaPlayer.setDataSource(track.uri);
            }else{
                mediaPlayer.setDataSource(this, android.net.Uri.parse(track.uri));
            }
            mediaPlayer.setOnPreparedListener(mp -> {
                isReady=true;
                durationMs=mp.getDuration();
                mp.start();
                playing=true;
                notifyAndBroadcast();
            });
            mediaPlayer.setOnCompletionListener(mp -> next(true));
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                playbackError="Não foi possível reproduzir esta faixa. Verifique sua conexão ou tente outra música.";
                playing=false; isReady=false; notifyAndBroadcast();
                return true;
            });
            isReady=false;
            startForeground(NOTIFICATION_ID, createNotification());
            updateMediaSession();
            mediaPlayer.prepareAsync();
            broadcast();
        } catch (Exception ex) {
            playbackError="Erro ao abrir música: "+ex.getClass().getSimpleName();
            disposePlayer();
            playing=false;
            notifyAndBroadcast();
        }
    }

    private void pause() {
        if(mediaPlayer != null && isReady && mediaPlayer.isPlaying()) {
            positionMs = mediaPlayer.getCurrentPosition();
            mediaPlayer.pause();
        }
        playing=false;
        notifyAndBroadcast();
    }

    private void resume() {
        if(mediaPlayer != null && isReady) {
            mediaPlayer.start();
            playing=true;
            notifyAndBroadcast();
        } else if(index>=0 && index<queue.size()) {
            playIndex(index);
        }
    }

    private void next(boolean finished) {
        if(queue.isEmpty()) return;
        if (finished && repeat == 2) { playIndex(index); return; }
        int wanted;
        if (shuffle && queue.size() > 1) {
            wanted = random.nextInt(queue.size()-1);
            if(wanted >= index) wanted++;
        } else {
            wanted = index+1;
            if(wanted>=queue.size()) {
                if(finished && repeat==0) { pause(); seek(0); return; }
                wanted=0;
            }
        }
        playIndex(wanted);
    }

    private void previous() {
        if(queue.isEmpty()) return;
        if(isReady && mediaPlayer != null && mediaPlayer.getCurrentPosition()>3000) { seek(0); return; }
        int wanted=index-1;
        if(wanted<0) wanted=queue.size()-1;
        playIndex(wanted);
    }

    private void seek(int pos) {
        if(mediaPlayer != null && isReady) {
            int p = Math.max(0,Math.min(pos,mediaPlayer.getDuration()));
            mediaPlayer.seekTo(p);
            positionMs=p;
            notifyAndBroadcast();
        }
    }

    private void disposePlayer() {
        isReady=false;
        if(mediaPlayer != null) {
            try { mediaPlayer.reset(); } catch(Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer=null;
        }
    }

    private void updateMediaSession() {
        if (session == null) return;
        int state = playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE |
               PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT |
               PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO)
            .setState(state, currentPosition(), playing ? 1f : 0f, SystemClock.elapsedRealtime()).build());
        session.setMetadata(new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, currentArtist)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs).build());
    }

    private int currentPosition() {
        if (mediaPlayer != null && isReady) {
            try { positionMs=mediaPlayer.getCurrentPosition(); } catch (Exception ignored) { }
        }
        return positionMs;
    }

    private PendingIntent control(String action, int code) {
        Intent intent=new Intent(this, MusicService.class).setAction(action);
        return PendingIntent.getService(this,code,intent,PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification createNotification() {
        Intent intent=new Intent(this,MainActivity.class);
        PendingIntent open=PendingIntent.getActivity(this,1,intent,PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this,CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous,"Anterior",control(ACTION_PREVIOUS,2)).build())
            .addAction(new Notification.Action.Builder(playing?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,
                 playing?"Pausar":"Reproduzir", control(ACTION_TOGGLE,3)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next,"Próxima",control(ACTION_NEXT,4)).build());
        builder.setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1,2));
        return builder.build();
    }

    private void notifyAndBroadcast() {
        currentPosition();
        updateMediaSession();
        if(index>=0) {
            NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            n.notify(NOTIFICATION_ID,createNotification());
        }
        broadcast();
    }

    private void broadcast() {
        Intent i = new Intent(ACTION_UPDATE).setPackage(getPackageName());
        sendBroadcast(i);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        disposePlayer();
        if(session!=null) {session.setActive(false);session.release();session=null;}
        playing=false;
        super.onDestroy();
    }
}
