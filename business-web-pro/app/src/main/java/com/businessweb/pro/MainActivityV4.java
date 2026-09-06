package com.businessweb.pro;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.media.*;
import android.net.*;
import android.net.http.SslError;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;

import java.util.*;
import java.util.regex.*;

public class MainActivityV4 extends Activity {
    static final String HOME="https://web.whatsapp.com/";
    static final String MSG_CH="bwm_msg_v4", CALL_CH="bwm_call_v4";
    static final int FILE=10, MEDIA=11, NOTIFY=12, CALL_ID=900;
    FrameLayout root; WebView web; ProgressBar bar; ValueCallback<Uri[]> upload; PermissionRequest pending;
    boolean chatOpen=false, titlePrimed=false; int lastUnread=0, seq=0;
    long lastDetailed=0, lastCall=0; String lastKey="";

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(0xff0b141a); getWindow().setNavigationBarColor(0xff0b141a);
        channels(); askNotify(); keepAlive();
        root=new FrameLayout(this); setContentView(root);
        bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); bar.setMax(100);
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-1,Math.round(3*getResources().getDisplayMetrics().density));
        bp.gravity=Gravity.TOP; root.addView(bar,bp);
        try{ makeWeb(); if(b!=null && web.restoreState(b)!=null) root.postDelayed(this::inject,500); else web.loadUrl(HOME); }
        catch(Throwable e){ Toast.makeText(this,"Update Chrome / Android System WebView.",Toast.LENGTH_LONG).show(); }
        root.postDelayed(this::testNotification,1700);
    }

    void makeWeb(){
        if(web!=null){ try{ ((ViewGroup)web.getParent()).removeView(web); web.destroy(); }catch(Throwable ignored){} }
        web=new WebView(this); web.setLayerType(View.LAYER_TYPE_HARDWARE,null); web.setInitialScale(100);
        web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND,false); web.setBackgroundColor(Color.WHITE);
        root.addView(web,0,new FrameLayout.LayoutParams(-1,-1));
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true); s.setLoadWithOverviewMode(false); s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false); s.setAllowContentAccess(true); s.setAllowFileAccess(true);
        s.setLoadsImagesAutomatically(true); s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(false); s.setDisplayZoomControls(false); s.setTextZoom(100);
        s.setOffscreenPreRaster(false); s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); s.setUserAgentString(desktopUA(s.getUserAgentString()));
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.addJavascriptInterface(new Bridge(),"BWM");

        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView v,String u,Bitmap f){bar.setVisibility(View.VISIBLE);}
            @Override public void onPageFinished(WebView v,String u){bar.setVisibility(View.GONE);CookieManager.getInstance().flush();inject();}
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return nav(r.getUrl());}
            @Override public boolean shouldOverrideUrlLoading(WebView v,String u){return nav(Uri.parse(u));}
            @Override public void onReceivedSslError(WebView v,SslErrorHandler h,SslError e){h.cancel();}
            @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail d){
                try{ViewGroup p=(ViewGroup)v.getParent();if(p!=null)p.removeView(v);v.destroy();}catch(Throwable ignored){}
                web=null; root.postDelayed(()->{try{makeWeb();web.loadUrl(HOME);}catch(Throwable ignored){}},500); return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView v,int p){bar.setProgress(p);}
            @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p){
                if(upload!=null)upload.onReceiveValue(null); upload=cb;
                try{startActivityForResult(p.createIntent(),FILE);return true;}catch(Exception e){upload=null;return false;}
            }
            @Override public void onPermissionRequest(PermissionRequest r){runOnUiThread(()->webPermission(r));}
        });
    }

    void inject(){
        if(web==null)return;
        String js="""
        (function(){
          if(window.__BWM4){if(window.__BWMEnsure)window.__BWMEnsure();return;} window.__BWM4=true;
          const S={pane:null,po:null,ro:null,scan:null,call:null,primed:false,rows:{},chat:false,ring:false};
          const txt=x=>String(x==null?'':x).replace(/\\s+/g,' ').trim();
          function style(){
            if(document.getElementById('bwm4css'))return;
            const x=document.createElement('style');x.id='bwm4css';
            x.textContent='html,body,#app{width:100%!important;max-width:100%!important;overflow:hidden!important}'+
            '@media(max-width:900px){.bwmLeft{width:100vw!important;max-width:100vw!important;min-width:100vw!important;flex:0 0 100vw!important}'+
            '#side{width:calc(100vw - 60px)!important;max-width:none!important;min-width:0!important;flex:1 1 auto!important}'+
            '#main{position:absolute!important;inset:0!important;width:100vw!important;max-width:100vw!important;height:100%!important;z-index:999!important;background:#fff!important}'+
            'body.bwmList #main{display:none!important}body.bwmList .bwmLeft{display:flex!important}}';
            (document.head||document.documentElement).appendChild(x);
          }
          function pane(){return document.querySelector('#pane-side')||document.querySelector('[aria-label="Chat list"]')||document.querySelector('[aria-label*="chat list" i]');}
          function layout(){
            style();const side=document.querySelector('#side');if(side&&side.parentElement)side.parentElement.classList.add('bwmLeft');
            const open=!!document.querySelector('#main')&&!document.body.classList.contains('bwmList');
            if(open!==S.chat){S.chat=open;try{BWM.chat(open)}catch(e){}}
          }
          window.__BWMLayout=layout;
          function time(x){return /^\\d{1,2}:\\d{2}(\\s*[AP]M)?$/i.test(x)||/^(Yesterday|Today|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)$/i.test(x);}
          function info(r){
            const a=(r.innerText||'').split('\\n').map(txt).filter(Boolean); if(!a.length)return null;
            const sender=a[0].slice(0,90); if(!sender)return null;
            let badge=null;try{badge=r.querySelector('[aria-label*="unread" i],[data-testid*="unread" i],[data-icon*="unread" i]')}catch(e){}
            let n=0;if(badge){const m=txt(badge.getAttribute('aria-label')||badge.textContent).match(/\\d{1,3}/);if(m)n=parseInt(m[0])||0}
            for(let i=a.length-1;i>0;i--)if(/^\\d{1,3}$/.test(a[i])){const q=parseInt(a[i])||0;if(q>0&&q<1000){if(!n)n=q;break}}
            const unread=!!badge||n>0||/unread/i.test(txt(r.getAttribute&&r.getAttribute('aria-label')));
            let preview='';for(let i=1;i<a.length;i++){if(time(a[i])||/^\\d{1,3}$/.test(a[i])||/^(typing|online)$/i.test(a[i]))continue;preview=a[i];break}
            return {sender:sender,preview:(preview||'New message').slice(0,180),count:n||(unread?1:0),unread:unread};
          }
          function scan(){
            S.scan=null;const p=pane();if(!p)return;
            let rs=p.querySelectorAll('[role="listitem"],[data-testid="cell-frame-container"]');if(!rs.length)rs=p.querySelectorAll('[role="row"]');
            for(let i=0;i<Math.min(rs.length,60);i++){
              const z=info(rs[i]);if(!z)continue;const old=S.rows[z.sender];
              if(S.primed&&old&&z.unread&&(!old.unread||z.count>(old.count||0)||z.preview!==old.preview)){
                try{BWM.message(z.sender,z.preview,z.count)}catch(e){}
              }
              S.rows[z.sender]=z;
            }
            S.primed=true;layout();
          }
          function schedule(){if(S.scan)clearTimeout(S.scan);S.scan=setTimeout(scan,450)}
          function attach(){
            const p=pane();if(!p||S.pane===p)return;if(S.po)try{S.po.disconnect()}catch(e){}
            S.pane=p;S.primed=false;S.rows={};S.po=new MutationObserver(schedule);S.po.observe(p,{childList:true,subtree:true,characterData:true});
            p.addEventListener('click',()=>{document.body.classList.remove('bwmList');setTimeout(layout,80)},true);setTimeout(scan,700);
          }
          function caller(d){for(const x of (d.innerText||'').split('\\n').map(txt)){if(x&&!/incoming|voice call|video call|calling|accept|answer|decline|reject/i.test(x))return x.slice(0,90)}return 'WhatsApp contact'}
          function call(){
            S.call=null;let f=null;for(const d of document.querySelectorAll('[role="dialog"],[aria-modal="true"],[data-animate-modal-popup="true"]')){
              const low=txt(d.innerText).toLowerCase();let a=null,b=null;
              try{a=d.querySelector('[aria-label*="accept" i],[aria-label*="answer" i],[title*="accept" i],[title*="answer" i]');b=d.querySelector('[aria-label*="decline" i],[aria-label*="reject" i],[title*="decline" i],[title*="reject" i]')}catch(e){}
              if((a&&b)||((low.includes('incoming')||low.includes('calling'))&&(low.includes('call')||low.includes('video')))){f=d;break}
            }
            if(f&&!S.ring){S.ring=true;try{BWM.incoming(caller(f))}catch(e){}}else if(!f&&S.ring){S.ring=false;try{BWM.ended()}catch(e){}}
          }
          function callSoon(){if(S.call)clearTimeout(S.call);S.call=setTimeout(call,550)}
          function title(){
            let last='';const c=()=>{const t=document.title||'';if(t!==last){last=t;try{BWM.title(t)}catch(e){}}};c();
            const n=document.querySelector('title');if(n)new MutationObserver(c).observe(n,{childList:true,subtree:true,characterData:true});
          }
          function ensure(){
            style();attach();layout();
            if(!S.ro&&document.body){S.ro=new MutationObserver(()=>{callSoon();attach();layout()});S.ro.observe(document.body,{childList:true,subtree:true})}
            schedule();callSoon();
          }
          window.__BWMEnsure=ensure;title();ensure();setInterval(()=>{try{ensure()}catch(e){}},6000);
        })();
        """;
        web.evaluateJavascript(js,null);
    }

    public class Bridge{
        @JavascriptInterface public void message(String s,String p,int n){runOnUiThread(()->detailed(s,p,n));}
        @JavascriptInterface public void title(String t){runOnUiThread(()->titleAlert(t));}
        @JavascriptInterface public void incoming(String c){runOnUiThread(()->callNotification(c));}
        @JavascriptInterface public void ended(){runOnUiThread(()->getSystemService(NotificationManager.class).cancel(CALL_ID));}
        @JavascriptInterface public void chat(boolean x){runOnUiThread(()->chatOpen=x);}
    }

    void detailed(String s,String p,int n){
        s=clean(s,"WhatsApp Business");p=clean(p,"New message");String k=s+"|"+p+"|"+Math.max(1,n);long now=System.currentTimeMillis();
        if(k.equals(lastKey)&&now-lastDetailed<8000)return;lastKey=k;lastDetailed=now;messageNotification(s,p);
    }
    void titleAlert(String t){
        int u=0;Matcher m=Pattern.compile("\\((\\d+)\\)").matcher(t==null?"":t);if(m.find())try{u=Integer.parseInt(m.group(1));}catch(Exception ignored){}
        if(!titlePrimed){titlePrimed=true;lastUnread=u;return;}
        if(u>lastUnread&&System.currentTimeMillis()-lastDetailed>2500)messageNotification("WhatsApp Business",u==1?"1 new unread message":u+" unread messages");
        lastUnread=u;
    }
    String clean(String x,String f){if(x==null)return f;x=x.replaceAll("\\s+"," ").trim();if(x.isEmpty())return f;return x.length()>180?x.substring(0,180):x;}
    boolean canNotify(){return Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;}

    void messageNotification(String who,String body){
        if(!canNotify())return;Intent i=new Intent(this,MainActivityV4.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,1000+(seq%90),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Bitmap icon=BitmapFactory.decodeResource(getResources(),R.drawable.ic_launcher);
        Notification n=new Notification.Builder(this,MSG_CH).setSmallIcon(R.drawable.ic_launcher).setLargeIcon(icon).setContentTitle(who).setContentText(body)
            .setStyle(new Notification.BigTextStyle().bigText(body)).setSubText("Business Web Mobile").setCategory(Notification.CATEGORY_MESSAGE)
            .setPriority(Notification.PRIORITY_HIGH).setVisibility(Notification.VISIBILITY_PUBLIC).setAutoCancel(true).setOnlyAlertOnce(false)
            .setShowWhen(true).setWhen(System.currentTimeMillis()).setContentIntent(pi).build();
        getSystemService(NotificationManager.class).notify(100+(seq++%90),n);
    }
    void callNotification(String who){
        if(!canNotify())return;long now=System.currentTimeMillis();if(now-lastCall<7000)return;lastCall=now;who=clean(who,"WhatsApp contact");
        Intent i=new Intent(this,MainActivityV4.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,3000,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,CALL_CH).setSmallIcon(R.drawable.ic_launcher).setContentTitle(who).setContentText("Incoming WhatsApp call — tap to answer")
            .setCategory(Notification.CATEGORY_CALL).setPriority(Notification.PRIORITY_MAX).setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(true)
            .setOnlyAlertOnce(false).setTimeoutAfter(90000).setContentIntent(pi).setFullScreenIntent(pi,true).build();
        getSystemService(NotificationManager.class).notify(CALL_ID,n);
    }

    void channels(){
        NotificationManager nm=getSystemService(NotificationManager.class);
        AudioAttributes ma=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        NotificationChannel mc=new NotificationChannel(MSG_CH,"Message pop-up alerts",NotificationManager.IMPORTANCE_HIGH);
        mc.setDescription("Heads-up and lock-screen alerts for new linked WhatsApp messages");mc.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),ma);
        mc.enableVibration(true);mc.setVibrationPattern(new long[]{0,180,120,220});mc.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);mc.setShowBadge(true);nm.createNotificationChannel(mc);
        AudioAttributes ca=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        NotificationChannel cc=new NotificationChannel(CALL_CH,"Incoming call alerts",NotificationManager.IMPORTANCE_HIGH);
        cc.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),ca);cc.enableVibration(true);cc.setVibrationPattern(new long[]{0,500,450,500,450,500});
        cc.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);nm.createNotificationChannel(cc);
    }
    void askNotify(){if(Build.VERSION.SDK_INT>=33&&!canNotify())requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFY);}
    void testNotification(){
        if(!canNotify())return;android.content.SharedPreferences p=getSharedPreferences("bwm4",MODE_PRIVATE);if(p.getBoolean("tested",false))return;
        p.edit().putBoolean("tested",true).apply();messageNotification("Business Web Mobile","Pop-up notifications are ready.");
    }
    void keepAlive(){try{Intent i=new Intent(this,KeepAliveService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}catch(Throwable ignored){}}

    void webPermission(PermissionRequest r){
        Uri o=r.getOrigin();String h=o==null?null:o.getHost();if(h==null||!(h.equals("web.whatsapp.com")||h.endsWith(".whatsapp.com"))){r.deny();return;}
        pending=r;ArrayList<String> miss=new ArrayList<>();
        for(String x:r.getResources()){if(PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(x)&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)miss.add(Manifest.permission.RECORD_AUDIO);
            if(PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(x)&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)miss.add(Manifest.permission.CAMERA);}
        if(miss.isEmpty())grantWeb();else requestPermissions(miss.toArray(new String[0]),MEDIA);
    }
    void grantWeb(){
        if(pending==null)return;ArrayList<String>a=new ArrayList<>();
        for(String x:pending.getResources()){if(PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(x)&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)a.add(x);
            if(PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(x)&&checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)a.add(x);}
        if(a.isEmpty())pending.deny();else pending.grant(a.toArray(new String[0]));pending=null;
    }
    boolean nav(Uri u){
        String s=u.getScheme();if(s==null)return false;if("http".equalsIgnoreCase(s)||"https".equalsIgnoreCase(s)){
            String h=u.getHost();if(h!=null&&(h.equals("whatsapp.com")||h.endsWith(".whatsapp.com")))return false;try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}return true;}
        try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}return true;
    }
    String desktopUA(String x){
        if(x==null||x.isBlank())return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
        x=x.replaceFirst("\\([^)]*\\)","(Windows NT 10.0; Win64; x64)").replace("; wv","").replace(" Version/4.0","").replace(" Mobile","");
        return x.contains("Windows NT")?x:"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
    }

    @Override protected void onActivityResult(int r,int c,Intent d){if(r==FILE){if(upload!=null){upload.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(c,d));upload=null;}return;}super.onActivityResult(r,c,d);}
    @Override public void onRequestPermissionsResult(int r,String[]p,int[]g){super.onRequestPermissionsResult(r,p,g);if(r==MEDIA)grantWeb();else if(r==NOTIFY&&canNotify())root.postDelayed(this::testNotification,400);}
    @Override protected void onSaveInstanceState(Bundle b){if(web!=null)web.saveState(b);super.onSaveInstanceState(b);}
    @Override protected void onResume(){super.onResume();if(web!=null)try{web.onResume();web.resumeTimers();web.setNetworkAvailable(true);inject();}catch(Throwable ignored){}}
    @Override protected void onPause(){try{if(web!=null){web.resumeTimers();web.setNetworkAvailable(true);}CookieManager.getInstance().flush();}catch(Throwable ignored){}super.onPause();}
    @Override public void onBackPressed(){if(web!=null&&chatOpen){chatOpen=false;web.evaluateJavascript("document.body.classList.add('bwmList');if(window.__BWMLayout)window.__BWMLayout();",null);}else moveTaskToBack(true);}
    @Override protected void onDestroy(){if(upload!=null)upload.onReceiveValue(null);if(pending!=null)pending.deny();if(web!=null)try{ViewGroup p=(ViewGroup)web.getParent();if(p!=null)p.removeView(web);web.destroy();}catch(Throwable ignored){}super.onDestroy();}
}
