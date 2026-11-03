package com.merlin.player;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Copia arquivos compartilhados para a biblioteca privada do Merlin.
 * Evita que o acesso ao URI temporário do Snaptube expire após fechar o aplicativo.
 * Nenhum acesso irrestrito ao armazenamento é solicitado.
 */
public final class SharedMediaImport {
    public interface Callback { void done(int imported,String error); }
    private static final long LIMIT=1_073_741_824L;
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Handler UI=new Handler(Looper.getMainLooper());
    private SharedMediaImport(){}
    public static void save(Context ctx,List<Uri> uris,Callback callback){
        IO.execute(()->{
            int imported=0;String lastError="";
            File dir=new File(ctx.getFilesDir(),"merlin_importados");
            if(!dir.exists()&&!dir.mkdirs())lastError="Não foi possível criar a pasta interna.";
            else for(Uri uri:uris){
                if(uri==null)continue;
                String name="musica";
                try(Cursor c=ctx.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
                    if(c!=null && c.moveToFirst()&&!c.isNull(0))name=c.getString(0);
                }catch(Exception ignored){}
                name=name.replaceAll("[^A-Za-z0-9._ -]","_");
                if(name.length()>75)name=name.substring(name.length()-75);
                if(name.isEmpty())name="arquivo";
                if(!name.matches("(?i).*\\.(mp3|m4a|aac|flac|ogg|opus|wav|mp4|webm|mov|mkv)$")){
                    lastError="Tipo de arquivo não aceito: "+name;continue;
                }
                File saved=null;
                try(InputStream input=ctx.getContentResolver().openInputStream(uri)){
                    if(input==null)throw new Exception("arquivo indisponível");
                    saved=File.createTempFile("merlin_","_"+name,dir);
                    long size=0;
                    try(FileOutputStream output=new FileOutputStream(saved)){
                        byte[] bytes=new byte[65536];int read;
                        while((read=input.read(bytes))!=-1){
                            size+=read;
                            if(size>LIMIT)throw new Exception("arquivo maior que 1 GB");
                            output.write(bytes,0,read);
                        }
                    }
                    if(size==0)throw new Exception("arquivo vazio");
                    AudioLibrary.addImport(ctx,Uri.fromFile(saved));
                    imported++;
                }catch(Exception e){
                    lastError=e.getMessage()==null?"Falha ao copiar":e.getMessage();
                    if(saved!=null)saved.delete();
                }
            }
            int count=imported;String error=lastError;
            UI.post(()->callback.done(count,error));
        });
    }
}
