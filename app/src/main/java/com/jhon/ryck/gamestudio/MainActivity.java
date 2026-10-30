package com.jhon.ryck.gamestudio;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
  WebView web;
  TextToSpeech voice;
  boolean voiceReady=false;
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    web=new WebView(this);
    WebSettings s=web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true);
    s.setAllowFileAccessFromFileURLs(false);
    s.setAllowUniversalAccessFromFileURLs(false);
    s.setJavaScriptCanOpenWindowsAutomatically(false);
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){
        String url=req.getUrl().toString();
        if(url.startsWith("file:///android_asset/"))return false;
        open(url);return true;
      }
    });
    web.addJavascriptInterface(new Bridge(),"JhonAndroid");
    setContentView(web);
    web.loadUrl("file:///android_asset/index.html");
    voice=new TextToSpeech(this,status->{
      if(status==TextToSpeech.SUCCESS && voice!=null)
        voiceReady=voice.setLanguage(new Locale("pt","BR"))>=0;
    });
  }
  void send(String name,String txt){
    runOnUiThread(()->web.evaluateJavascript("window.JHON&&window.JHON."+name+"("+JSONObject.quote(txt)+")",null));
  }
  void open(String url){
    try{
      Uri uri=Uri.parse(url);
      if(!"https".equalsIgnoreCase(uri.getScheme()))return;
      startActivity(new Intent(Intent.ACTION_VIEW,uri));
    } catch(Exception ex){send("error","Não foi possível abrir o navegador.");}
  }
  public class Bridge{
    @JavascriptInterface public void speak(String txt){
      if(txt==null||txt.length()>3500)return;
      runOnUiThread(()->{
        if(voiceReady && voice!=null)voice.speak(txt,TextToSpeech.QUEUE_FLUSH,null,"jhon");
      });
    }
    @JavascriptInterface public void listen(){
      runOnUiThread(()->{
        try{
          Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
          intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
          intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"pt-BR");
          intent.putExtra(RecognizerIntent.EXTRA_PROMPT,"Fale com o Jhon");
          startActivityForResult(intent,101);
        } catch(Exception ex){send("error","Reconhecimento de voz indisponível neste aparelho.");}
      });
    }
    @JavascriptInterface public void browser(String url){
      if(url!=null && url.length()<15000)runOnUiThread(()->open(url));
    }
  }
  @Override protected void onActivityResult(int code,int result,Intent data){
    super.onActivityResult(code,result,data);
    if(code==101&&result==RESULT_OK&&data!=null){
      ArrayList<String> matches=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
      if(matches!=null&&!matches.isEmpty())send("heard",matches.get(0));
    }
  }
  @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
  @Override protected void onDestroy(){
    if(voice!=null){voice.stop();voice.shutdown();}
    if(web!=null){web.removeJavascriptInterface("JhonAndroid");web.destroy();}
    super.onDestroy();
  }
}