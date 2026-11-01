package ph.kanta.reborn;

import android.os.SystemClock;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import ph.kanta.reborn.core.*;

/** Lifecycle and main-Looper boundary for the local HTTPS companion. */
final class BrowserCompanion {
  private final SessionController session;
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private volatile BrowserRemoteServer server;
  private volatile long generation;
  boolean preparing;
  String status="Browser companion is off",address="",code="",fingerprint="";
  Runnable changed;
  BrowserCompanion(SessionController session){this.session=session;}
  boolean active(){return server!=null&&server.active();}
  void stop(){generation++;BrowserRemoteServer old=server;server=null;if(old!=null)old.close();
    preparing=false;address="";code="";fingerprint="";status="Browser companion is off";notifyChanged();}
  private void notifyChanged(){if(changed!=null)changed.run();}
  void start(String address){
    if(!session.foreground||!session.ready||active()||preparing)return;
    long ticket=++generation;preparing=true;status="Preparing private HTTPS host…";notifyChanged();
    worker.execute(()->{
      BrowserRemoteServer candidate=null;
      try{
        if(ticket!=generation)return;
        java.net.InetAddress local=java.net.InetAddress.getByAddress(CompanionProtocol.privateAddress(address));
        if(java.net.NetworkInterface.getByInetAddress(local)==null)throw new IOException("Not a local interface");
        byte[] page=asset("index.html"),script=asset("remote.js"),style=asset("remote.css");
        Map<String,byte[]> artwork=new HashMap<>();
        for(int i=1;i<=8;i++){
          String number=String.format(Locale.ROOT,"%02d",i);
          artwork.put("/assets/covers/"+number+".webp",asset("art/covers/"+number+".webp",160000));
        }
        candidate=new BrowserRemoteServer(address,page,script,style,artwork,request->apply(ticket,request));
        BrowserRemoteServer ready=candidate;
        session.main.post(()->{
          if(ticket!=generation||!session.foreground){ready.close();return;}
          preparing=false;server=ready;this.address=ready.address();code=ready.code();fingerprint=ready.fingerprint();
          status="HTTPS host ready · up to four paired guests";ready.start();notifyChanged();
          session.main.postDelayed(()->{if(ticket==generation){stop();status="Host expired. Start again for a new certificate and code.";notifyChanged();}},30*60_000);
        });
      }catch(Exception e){if(candidate!=null)candidate.close();session.main.post(()->{if(ticket==generation){stop();status="Could not start HTTPS hosting. Check the selected Wi-Fi address.";notifyChanged();}});}
    });
  }
  private byte[] asset(String name)throws IOException { return asset(name,131072); }

  private byte[] asset(String name,int maximumBytes)throws IOException {
    try(InputStream input=session.context.getAssets().open("companion/"+name)){
      ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
      while((n=input.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>maximumBytes)throw new IOException("Asset limit");}
      return out.toByteArray();
    }
  }
  private byte[] apply(long ticket,CompanionProtocol.Request request)throws Exception {
    CompletableFuture<byte[]> result=new CompletableFuture<>();
    session.main.post(()->{
      if(ticket!=generation||!session.foreground||!active()||result.isCancelled()){result.completeExceptionally(new IOException("Revoked"));return;}
      try{
        JSONObject value;
        if(request.action.equals("stop")){
          session.stop();value=session.companion.apply(new CompanionProtocol.Request(request.id,"snapshot","",""));
        }else value=session.companion.apply(request);
        value.put("version",1);value.put("source","local");
        value.put("capabilities",new JSONArray(Arrays.asList("snapshot","search","reserve","cancel","play","pause","next","stop")));
        result.complete(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      }catch(Exception e){result.completeExceptionally(e);}
    });
    try{return result.get(3,TimeUnit.SECONDS);}catch(Exception e){result.cancel(false);throw e;}
  }
}
