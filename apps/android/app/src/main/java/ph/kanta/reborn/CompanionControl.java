package ph.kanta.reborn;

import android.os.SystemClock;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;
import ph.kanta.reborn.core.*;

/** Foreground-only Android companion. Socket work never owns playback or touches Views. */
final class CompanionControl {
  static final int PORT = 45731;
  private final SessionController session;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final AtomicBoolean busy = new AtomicBoolean();
  private volatile Run active;
  private volatile long requestGeneration;
  String status = "Companion control is off", pairing = "";
  JSONObject remote;
  Runnable changed;

  private static final class Run {
    final boolean host;
    final long expires = SystemClock.elapsedRealtime() + 10 * 60_000;
    final long pairingExpires = SystemClock.elapsedRealtime() + 5 * 60_000;
    volatile String code;
    volatile boolean revoked, paired;
    private ServerSocket listener;
    private Socket socket;
    CompanionProtocol.Channel channel;
    final CompanionProtocol.Replay replay = new CompanionProtocol.Replay();
    Run(boolean host, String code) { this.host=host;this.code=code; }
    synchronized void socket(Socket value) throws IOException {
      if(revoked){value.close();throw new IOException("Revoked");} socket=value;
    }
    synchronized void listener(ServerSocket value) throws IOException {
      if(revoked){value.close();throw new IOException("Revoked");}listener=value;
    }
    synchronized void stopListening() throws IOException {
      if(listener!=null)listener.close();listener=null;
    }
    synchronized void revoke() {
      revoked=true; code=null;
      if(channel!=null)channel.close();
      try{if(socket!=null)socket.close();}catch(IOException ignored){}
      try{stopListening();}catch(IOException ignored){}
    }
    boolean valid(){return !revoked && SystemClock.elapsedRealtime()<expires;}
  }

  CompanionControl(SessionController session) { this.session=session; }
  boolean enabled(){return active!=null;}
  boolean client(){Run r=active;return r!=null&&!r.host&&r.paired&&r.valid();}
  private void notifyChanged(){if(changed!=null)changed.run();}
  void stop() {
    requestGeneration++;
    Run old=active;active=null;
    if(old!=null){old.revoke();session.main.removeCallbacksAndMessages(old);}
    pairing="";remote=null;status="Companion control is off";notifyChanged();
  }
  void startHost(String address) { start(true,address,CompanionProtocol.newCode()); }
  void join(String address,String code) { start(false,address,code); }
  private void start(boolean host,String address,String code) {
    if(!session.foreground || !session.ready || active!=null || busy.get())return;
    final InetAddress endpoint;
    try {
      endpoint=InetAddress.getByAddress(CompanionProtocol.privateAddress(address));
      if(host && NetworkInterface.getByInetAddress(endpoint)==null)throw new IOException();
      if(code==null||!code.matches("[a-fA-F0-9]{32}"))throw new IOException();
    }catch(Exception e){status="Use this device's private IPv4 address to host, or the host address and full 32-character code to join.";notifyChanged();return;}
    Run run=new Run(host,code);active=run;remote=null;
    status=host?"Opening private companion host…":"Connecting to companion host…";
    pairing="";notifyChanged();
    session.main.postAtTime(()->{if(active==run&&!run.paired){stop();status="Pairing expired. Start again for a new code.";notifyChanged();}},run,SystemClock.uptimeMillis()+5*60_000);
    session.main.postAtTime(()->{if(active==run){stop();status="Companion session expired. Pair again.";notifyChanged();}},run,SystemClock.uptimeMillis()+10*60_000);
    busy.set(true);
    worker.execute(()->{
      try {
        if(host) host(run,endpoint);
        else {
          Socket socket=new Socket();run.socket(socket);
          socket.connect(new InetSocketAddress(endpoint,PORT),5000);
          authenticate(run,socket);
          session.main.post(()->{if(active==run){status="Connected · Android companion";pairing="";notifyChanged();poll(run);}});
        }
      }catch(Exception e){fail(run,"Companion disconnected or pairing rejected. Check the address, code and shared Wi-Fi.");}
      finally{busy.set(false);if(host)run.revoke();}
    });
  }
  private void poll(Run run){
    session.main.postAtTime(()->{
      if(active==run && run.valid() && session.foreground){send("snapshot","","");poll(run);}
    },run,SystemClock.uptimeMillis()+5000);
  }
  private void authenticate(Run run,Socket socket)throws IOException {
    socket.setSoTimeout(5000);socket.setTcpNoDelay(true);
    run.channel=CompanionProtocol.handshake(socket.getInputStream(),socket.getOutputStream(),run.code,run.host);
    if(!run.valid()||SystemClock.elapsedRealtime()>=run.pairingExpires)throw new IOException("Expired pairing");
    run.code=null;run.paired=true;socket.setSoTimeout(15000);
  }
  private void host(Run run,InetAddress endpoint)throws Exception {
    ServerSocket server=new ServerSocket();run.listener(server);
    server.bind(new InetSocketAddress(endpoint,PORT),1);server.setSoTimeout(1000);
    session.main.post(()->{if(active==run){pairing=run.code;status="Host "+endpoint.getHostAddress()+" · pairing expires in 5 minutes";notifyChanged();}});
    int failed=0;
    while(run.valid() && !run.paired && SystemClock.elapsedRealtime()<run.pairingExpires){
      Socket socket;
      try{socket=server.accept();}catch(SocketTimeoutException e){continue;}
      run.socket(socket);
      try {
        CompanionProtocol.privateAddress(socket.getInetAddress().getHostAddress());
        authenticate(run,socket);
      }catch(IOException e){socket.close();if(++failed>=3)throw e;continue;}
      run.stopListening();
      session.main.post(()->{if(active==run){pairing="";status="One Android companion connected · Stop disconnects and revokes it";notifyChanged();}});
      while(run.valid()) {
        CompanionProtocol.Request request=CompanionProtocol.Request.decode(run.channel.receive());
        byte[] reply=run.replay.get(request.id);
        if(reply==null){
          if(run.replay.full())throw new IOException("Session command limit");
          reply=execute(run,request);
          run.replay.remember(request.id,reply);
        }
        run.channel.send(reply);
      }
    }
    throw new IOException("Session expired");
  }
  private byte[] execute(Run run,CompanionProtocol.Request request)throws Exception {
    CompletableFuture<byte[]> completed=new CompletableFuture<>();
    session.main.post(()->{
      if(active!=run||!run.valid()||!session.foreground||completed.isCancelled()){
        completed.completeExceptionally(new IOException("Revoked"));return;
      }
      try{completed.complete(apply(request).toString().getBytes(StandardCharsets.UTF_8));}
      catch(Exception e){completed.completeExceptionally(e);}
    });
    try{return completed.get(3,TimeUnit.SECONDS);}
    catch(Exception e){completed.cancel(false);run.revoke();throw e;}
  }
  JSONObject apply(CompanionProtocol.Request request)throws Exception {
    if(!session.foreground||!session.ready)throw new IOException("Host unavailable");
    switch(request.action){
      case "reserve": {
        Song selected=session.songs.stream().filter(s->s.id.equals(request.argument)).findFirst().orElse(null);
        if(selected==null||session.queue.snapshot().size()>=1000)throw new IOException("Song unavailable or queue full");
        session.reserve(selected,request.singer);break;
      }
      case "cancel": session.remove(UUID.fromString(request.argument));break;
      case "play": if(!session.playing)session.toggle();break;
      case "pause": session.pause();break;
      case "next": session.next();break;
      case "search":
        if(request.argument.trim().isEmpty())throw new IOException("Empty companion search");
        break;
      case "snapshot":break;
      default:throw new IOException("Unsupported companion command");
    }
    JSONObject reply=new JSONObject();reply.put("request",request.id.toString());
    reply.put("playing",session.playing);reply.put("current",shortText(session.current==null?"No song playing":session.current.title,128));
    JSONArray songs=new JSONArray();
    if(request.action.equals("search")){
      int examined=0;
      String[] terms=Song.searchTerms(request.argument);
      for(Song song:session.songs){
        if(++examined>10000||songs.length()>=10)break;
        if(song.matchesTerms(terms)){
          JSONObject value=new JSONObject();value.put("id",song.id);value.put("title",shortText(song.title,128));
          value.put("artist",shortText(song.artist,80));value.put("code",shortText(song.code,32));songs.put(value);
        }
      }
    }
    reply.put("songs",songs);
    JSONArray queue=new JSONArray();
    for(ReservationQueue.Entry entry:session.queue.snapshot()){
      if(queue.length()>=10)break;
      JSONObject value=new JSONObject();value.put("id",entry.id.toString());value.put("title",shortText(entry.song.title,128));value.put("singer",shortText(entry.singer,80));queue.put(value);
    }
    reply.put("queue",queue);return reply;
  }
  private static String shortText(String value,int limit){return value.substring(0,Math.min(value.length(),limit));}
  void send(String action,String argument,String singer){
    Run run=active;
    if("search".equals(action) && (argument==null || argument.trim().isEmpty()))return;
    if(run==null||run.host||!run.paired||!run.valid()||!busy.compareAndSet(false,true))return;
    long generation=++requestGeneration;
    CompanionProtocol.Request request=new CompanionProtocol.Request(UUID.randomUUID(),action,argument,singer);
    worker.execute(()->{
      try {
        run.channel.send(request.encode());
        JSONObject response=new JSONObject(CompanionProtocol.jsonText(run.channel.receive()));
        if(!request.id.toString().equals(response.getString("request")))throw new IOException("Mismatched response");
        session.main.post(()->{if(active==run&&run.valid()&&generation==requestGeneration){if(!action.equals("search")&&remote!=null)try{response.put("songs",remote.optJSONArray("songs"));}catch(JSONException ignored){} remote=response;status="Connected · Android companion";notifyChanged();}});
      }catch(Exception e){fail(run,"Companion disconnected. Pair again to reconnect.");}
      finally{busy.set(false);}
    });
  }
  private void fail(Run run,String notice){
    run.revoke();
    if(run.channel!=null)run.channel.close();
    session.main.post(()->{if(active==run){stop();status=notice;notifyChanged();}});
  }
  static List<String> addresses(){
    List<String> result=new ArrayList<>();
    try{
      Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
      while(interfaces.hasMoreElements()){
        NetworkInterface network=interfaces.nextElement();
        if(!network.isUp()||network.isLoopback())continue;
        for(InetAddress address:Collections.list(network.getInetAddresses())){
          try{CompanionProtocol.privateAddress(address.getHostAddress());result.add(address.getHostAddress());}
          catch(IOException ignored){}
        }
      }
    }catch(SocketException ignored){}
    return result;
  }
}
