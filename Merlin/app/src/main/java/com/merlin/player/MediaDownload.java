package com.merlin.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Download de arquivo multimídia fornecido diretamente por quem tem direito de disponibilizá-lo.
 * NÃO usa APIs de extração, scraping, DRM, plataformas de vídeo ou URL de páginas web.
 * O arquivo é gravado num local selecionado pelo usuário via ACTION_CREATE_DOCUMENT (SAF).
 */
public final class MediaDownload {
    public interface Callback {
        void done(boolean success,String message);
    }
    private static final long MAX_BYTES=1_073_741_824L; // 1 GiB
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Handler UI=new Handler(Looper.getMainLooper());
    private static final String[] EXTENSIONS={
        ".mp3",".m4a",".aac",".flac",".wav",".ogg",".opus",".mp4",".webm",".mov",".mkv"
    };
    private MediaDownload(){}
    public static boolean hasFileSuffix(String value){
        try{
            String p=new URL(value).getPath().toLowerCase(Locale.ROOT);
            for(String ext:EXTENSIONS)if(p.endsWith(ext))return true;
        }catch(Exception ignored){}
        return false;
    }
    private static boolean verifiedUrl(String address){
        try{
            URL u=new URL(address);
            String host=u.getHost().toLowerCase(Locale.ROOT);
            if(!"https".equalsIgnoreCase(u.getProtocol())||u.getUserInfo()!=null||
                host.isEmpty()||host.equals("localhost")||host.endsWith(".local"))return false;
            if(host.equals("youtube.com")||host.endsWith(".youtube.com")||
                host.equals("youtu.be")||host.endsWith(".googlevideo.com")||
                host.equals("youtube-nocookie.com")||host.endsWith(".youtube-nocookie.com"))return false;
            return true;
        }catch(Exception e){return false;}
    }
    public static boolean isValid(String address){
        return address!=null&&verifiedUrl(address.trim());
    }
    public static String format(String url){
        try{
            String lower=new URL(url).getPath().toLowerCase(Locale.ROOT);
            if(lower.endsWith(".mp4")||lower.endsWith(".webm")||
                lower.endsWith(".mov")||lower.endsWith(".mkv"))return "video";
        }catch(Exception ignored){}
        return "audio";
    }
    public static String suggestedFileName(String url){
        try{
            String path=new URL(url).getPath();
            String name=path.substring(path.lastIndexOf('/')+1).replaceAll("[^A-Za-z0-9._-]","_");
            if(!name.isEmpty() && name.length()<80 && hasFileSuffix(url))return name;
        }catch(Exception ignored){}
        return "merlin_midia.mp3";
    }
    public static void save(Context ctx,String source,String format,Uri destination,Callback callback){
        if(!isValid(source)){
            callback.done(false,"Use uma URL HTTPS direta de arquivo autorizado.");
            return;
        }
        IO.execute(()->{
            boolean ok=false;
            String message;
            HttpURLConnection conn=null;
            try{
                URL current=new URL(source);
                for(int redirect=0;redirect<6;redirect++){
                    if(!verifiedUrl(current.toString()))throw new IllegalArgumentException("Redirecionamento não permitido");
                    conn=(HttpURLConnection)current.openConnection();
                    conn.setInstanceFollowRedirects(false);
                    conn.setConnectTimeout(13000);conn.setReadTimeout(25000);
                    conn.setRequestProperty("Accept","audio/*,video/*");
                    int status=conn.getResponseCode();
                    if(status>=300&&status<400){
                        String location=conn.getHeaderField("Location");
                        if(location==null)throw new IllegalArgumentException("Redirecionamento sem destino");
                        URL next=new URL(current,location);conn.disconnect();conn=null;
                        current=next;continue;
                    }
                    if(status!=200)throw new IllegalStateException("Servidor respondeu HTTP "+status);
                    String type=conn.getContentType();
                    type=type==null?"":type.toLowerCase(Locale.ROOT).split(";")[0].trim();
                    boolean isAudio=type.startsWith("audio/");
                    boolean isVideo=type.startsWith("video/");
                    if((isAudio&&!"audio".equals(format))||(isVideo&&!"video".equals(format)))
                        throw new IllegalArgumentException("O arquivo não corresponde ao formato escolhido.");
                    if(!isAudio&&!isVideo){
                        if(!type.equals("application/octet-stream")||!hasFileSuffix(current.toString()))
                            throw new IllegalArgumentException("O endereço não forneceu áudio ou vídeo direto.");
                        if(!format.equals(format(current.toString())))
                            throw new IllegalArgumentException("O arquivo não corresponde ao formato escolhido.");
                    }
                    long size=conn.getContentLengthLong();
                    if(size>MAX_BYTES)throw new IllegalArgumentException("Arquivo maior que 1 GB.");
                    long total=0;
                    try(InputStream in=conn.getInputStream();
                        OutputStream out=ctx.getContentResolver().openOutputStream(destination,"w")){
                        if(out==null)throw new IllegalStateException("Não foi possível gravar o arquivo.");
                        byte[] buffer=new byte[65536];int count;
                        while((count=in.read(buffer))!=-1){
                            total+=count;
                            if(total>MAX_BYTES)throw new IllegalArgumentException("Arquivo excedeu 1 GB.");
                            out.write(buffer,0,count);
                        }
                    }
                    if(total==0)throw new IllegalStateException("O servidor retornou um arquivo vazio.");
                    ok=true;break;
                }
                if(!ok)throw new IllegalStateException("Redirecionamentos demais.");
                message="Download finalizado. Abra na biblioteca do Merlin.";
            }catch(Exception e){
                try{ctx.getContentResolver().delete(destination,null,null);}catch(Exception ignored){}
                message="Não consegui baixar: "+(e.getMessage()==null?"erro de rede":e.getMessage());
            }finally{
                if(conn!=null)conn.disconnect();
            }
            boolean result=ok;String finalMessage=message;
            UI.post(()->callback.done(result,finalMessage));
        });
    }
}
