package ph.kanta.reborn.core;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.*;

/** Uses real generated identities and TLS sockets, not a plaintext HTTP test double. */
public final class BrowserRemoteRegressionTests {
  private static int checks;
  private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
  private static void rejectedTransportDispatchChecks(){
    AtomicBoolean busy=new AtomicBoolean(true),ran=new AtomicBoolean();
    boolean submitted=SafeExecutorSubmission.execute(command->{throw new RejectedExecutionException("transport closed");},()->ran.set(true),busy);
    check(!submitted&&!busy.get()&&!ran.get(),"Rejected browser transport dispatch retained its busy latch or ran after close");
    AtomicBoolean acceptedBusy=new AtomicBoolean(true);
    boolean accepted=SafeExecutorSubmission.execute(Runnable::run,()->{ran.set(true);acceptedBusy.set(false);},acceptedBusy);
    check(accepted&&ran.get()&&!acceptedBusy.get(),"Accepted browser transport dispatch did not execute normally");
  }
  private static String exchange(BrowserRemoteServer host,String method,String path,String origin,String token,byte[] body)throws Exception {
    return exchange(host,method,path,origin,token,body,host.fingerprint());
  }
  private static String exchange(BrowserRemoteServer host,String method,String path,String origin,String token,byte[] body,String pin)throws Exception {
    SSLContext tls=SSLContext.getInstance("TLS");
    tls.init(null,new TrustManager[]{new X509TrustManager(){
      public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
      public void checkClientTrusted(X509Certificate[] c,String a)throws java.security.cert.CertificateException{throw new java.security.cert.CertificateException();}
      public void checkServerTrusted(X509Certificate[] chain,String algorithm)throws java.security.cert.CertificateException{
        try{
          StringBuilder actual=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()))actual.append(String.format(Locale.ROOT,"%02x",b&255));
          if(!actual.toString().equals(pin))throw new java.security.cert.CertificateException("Wrong pin");
          chain[0].checkValidity();chain[0].verify(chain[0].getPublicKey());
        }catch(Exception e){throw new java.security.cert.CertificateException(e);}
      }
    }},new SecureRandom());
    URI uri=URI.create(host.address());
    try(SSLSocket socket=(SSLSocket)tls.getSocketFactory().createSocket(uri.getHost(),uri.getPort())){
      socket.setSoTimeout(4000);socket.startHandshake();
      X509Certificate certificate=(X509Certificate)socket.getSession().getPeerCertificates()[0];
      check(certificate.getSubjectAlternativeNames().stream().anyMatch(v->v.get(0).equals(7)&&v.get(1).equals("127.0.0.1")),"Certificate lacks address SAN");
      String headers=method+" "+path+" HTTP/1.1\r\nHost: "+uri.getAuthority()+"\r\nContent-Type: application/octet-stream\r\nContent-Length: "+body.length+"\r\n"+
        (origin==null?"":"Origin: "+origin+"\r\n")+(token==null?"":"Authorization: Bearer "+token+"\r\n")+"\r\n";
      socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(body);socket.getOutputStream().flush();
      return new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
    }
  }
  private static String body(String reply){return reply.substring(reply.indexOf("\r\n\r\n")+4);}
  private static BrowserRemoteServer host(AtomicInteger effects)throws Exception{
    Map<String,byte[]> artwork=Collections.singletonMap("/assets/covers/01.webp",new byte[]{'R','I','F','F'});
    BrowserRemoteServer server=new BrowserRemoteServer("127.0.0.1",0,"<h1>Remote</h1>".getBytes(StandardCharsets.UTF_8),new byte[0],new byte[0],artwork,request->{effects.incrementAndGet();return ("{\"request\":\""+request.id+"\"}").getBytes(StandardCharsets.UTF_8);},true);server.start();return server;
  }
  private static void timedSessionChecks()throws Exception {
    AtomicLong clock=new AtomicLong(10_000);
    AtomicInteger effects=new AtomicInteger();
    BrowserRemoteServer browser=new BrowserRemoteServer("127.0.0.1",0,new byte[0],new byte[0],new byte[0],Map.of(),(request,authorized)->{
      if(request.action.equals("next"))clock.incrementAndGet();
      if(!authorized.getAsBoolean())throw new IOException("Not authorized");
      if(!request.action.equals("snapshot"))effects.incrementAndGet();
      return ("{\"request\":\""+request.id+"\"}").getBytes(StandardCharsets.UTF_8);
    },true,clock::get);
    try(BrowserRemoteServer host=browser){
      host.start();
      byte[] code=host.code().getBytes(StandardCharsets.US_ASCII);
      byte[] saved=new CompanionProtocol.Request(UUID.randomUUID(),"reserve","song","Singer").encode();
      byte[] status=new CompanionProtocol.Request(UUID.randomUUID(),"snapshot","","").encode();
      long pairTime=clock.addAndGet(BrowserRemoteServer.PAIRING_MILLIS-1);
      String paired=exchange(host,"POST","/pair",host.address(),null,code);
      String token=body(paired);
      check(paired.startsWith("HTTP/1.1 200")&&paired.contains("X-Kanta-Remote-Reconnect: 1"),"Reconnect not negotiated on pairing");
      clock.incrementAndGet();
      check(!host.pairingOpen(),"Pairing stayed open at five-minute deadline");
      check(exchange(host,"POST","/pair",host.address(),null,code).startsWith("HTTP/1.1 403"),"Late pairing accepted");
      check(exchange(host,"POST","/status",host.address(),null,status).startsWith("HTTP/1.1 401"),"Unauthenticated reconnect accepted");
      check(exchange(host,"POST","/status",host.address(),token,saved).startsWith("HTTP/1.1 403"),"Read-only poll accepted a mutation");
      check(exchange(host,"POST","/status",null,token,status).startsWith("HTTP/1.1 403"),"Missing origin accepted");
      String result=exchange(host,"POST","/command",host.address(),token,saved);
      check(result.startsWith("HTTP/1.1 200")&&effects.get()==1,"Paired reserve failed");
      clock.set(pairTime+60_000);
      for(int i=0;i<60;i++){
        check(exchange(host,"POST","/status",host.address(),token,status).startsWith("HTTP/1.1 200"),"Read-only polling rejected below rate limit");
      }
      check(exchange(host,"POST","/status",host.address(),token,status).startsWith("HTTP/1.1 429"),"Over-rate guest authorized");
      clock.addAndGet(60_000);
      check(exchange(host,"POST","/status",host.address(),token,status).startsWith("HTTP/1.1 200"),"Rate limit was not temporary");
      clock.set(pairTime+BrowserRemoteServer.SESSION_MILLIS-1);
      try{
        exchange(host,"POST","/status",host.address(),token,status,"0".repeat(64));
        throw new AssertionError("Reconnect accepted a different certificate fingerprint");
      }catch(SSLHandshakeException expected){checks++;}
      check(exchange(host,"POST","/status",host.address(),token,status).startsWith("HTTP/1.1 200"),"A new Wi-Fi/TLS connection lost an unexpired guest");
      check(body(exchange(host,"POST","/command",host.address(),token,saved)).equals(body(result))&&effects.get()==1,"Reconnect lost replay protection");
      byte[] next=new CompanionProtocol.Request(UUID.randomUUID(),"next","","").encode();
      check(exchange(host,"POST","/command",host.address(),token,next).startsWith("HTTP/1.1 401")&&effects.get()==1,"In-flight command crossed its authorization deadline");
      check(exchange(host,"POST","/status",host.address(),token,status).startsWith("HTTP/1.1 401"),"Traffic extended the hard deadline");
      check(!result.contains(token)&&!result.contains(host.code())&&result.contains("Referrer-Policy: no-referrer"),"Credential leaked to a command response or referrer");
    }
    check(!browser.active(),"Stop left host active");
    try(BrowserRemoteServer restarted=host(effects)){
      check(!restarted.fingerprint().equals(browser.fingerprint()),"Restart reused host identity");
    }
  }
  private static void qrChecks() {
    String fingerprint="a".repeat(64),code="b".repeat(32);
    String text=BrowserPairingQr.encode("192.168.123.123",fingerprint,code);
    BrowserPairingQr pairing=BrowserPairingQr.parse(text);
    check(pairing!=null&&pairing.address.equals("192.168.123.123")&&pairing.code.equals(code)&&pairing.fingerprint.equals(fingerprint),"QR loses pairing data");
    var symbol=ph.kanta.reborn.core.connect.QrEncoder.encode(text);
    check(symbol.size>0&&!symbol.toString().contains(code)&&!pairing.toString().contains(code),"QR render unavailable or leaked secret in debug representation");
    for(String invalid:new String[]{text+"\n",text.replace("192.168.123.123","8.8.8.8"),text.replace("192.168.123.123","127.0.0.1"),
        text.replace("192.168.123.123","192.168.01.2"),text.replace(fingerprint,"xyz"),"https://192.168.1.1/?code="+code}){
      check(BrowserPairingQr.parse(invalid)==null,"Invalid scan allowed through trust bootstrap");
    }
  }
  public static int run()throws Exception{
    checks=0;rejectedTransportDispatchChecks();qrChecks();timedSessionChecks();AtomicInteger effects=new AtomicInteger();
    try(BrowserRemoteServer host=host(effects)){
      String shell=exchange(host,"GET","/",null,null,new byte[0]);
      check(shell.startsWith("HTTP/1.1 200"),"Shell did not load over TLS");
      check(shell.contains("frame-ancestors 'none'")&&shell.contains("Cache-Control: no-store")&&!shell.contains(host.code()),"Shell leaked credential or lacks hardening");
      String cover=exchange(host,"GET","/assets/covers/01.webp",null,null,new byte[0]);
      check(cover.startsWith("HTTP/1.1 200")&&cover.contains("Content-Type: image/webp"),"Allowlisted cover artwork did not load over TLS");
      check(exchange(host,"GET","/assets/covers/09.webp",null,null,new byte[0]).startsWith("HTTP/1.1 404"),"Unlisted artwork route was exposed");
      check(exchange(host,"GET","/../private",null,null,new byte[0]).startsWith("HTTP/1.1 404"),"Path traversal accepted");
      byte[] request=new CompanionProtocol.Request(UUID.randomUUID(),"reserve","song","Singer").encode();
      check(exchange(host,"POST","/command",host.address(),null,request).startsWith("HTTP/1.1 401"),"Unauthenticated command accepted");
      check(exchange(host,"POST","/pair","https://attacker.example",null,host.code().getBytes(StandardCharsets.US_ASCII)).startsWith("HTTP/1.1 403"),"Cross-origin pairing accepted");
      String paired=exchange(host,"POST","/pair",host.address(),null,host.code().getBytes(StandardCharsets.US_ASCII));
      check(paired.startsWith("HTTP/1.1 200"),"TLS pairing failed");String token=body(paired);check(token.matches("[a-f0-9]{64}"),"Weak token");
      String first=exchange(host,"POST","/command",host.address(),token,request);
      String repeated=exchange(host,"POST","/command",host.address(),token,request);
      check(body(first).equals(body(repeated))&&effects.get()==1,"Retried reservation applied twice");
      byte[] conflicting=request.clone();conflicting[conflicting.length-1]^=1;
      check(exchange(host,"POST","/command",host.address(),token,conflicting).startsWith("HTTP/1.1 409")&&effects.get()==1,"Command identity reused with changed body");
      byte[] intentional=new CompanionProtocol.Request(UUID.randomUUID(),"reserve","song","Singer").encode();
      exchange(host,"POST","/command",host.address(),token,intentional);check(effects.get()==2,"Distinct intentional reservation was deduplicated");
      exchange(host,"POST","/leave",host.address(),token,new byte[0]);
      check(exchange(host,"POST","/command",host.address(),token,request).startsWith("HTTP/1.1 401"),"Revoked guest retained authorization");
      for(int i=0;i<3;i++)check(exchange(host,"POST","/pair",host.address(),null,new byte[32]).startsWith("HTTP/1.1 403"),"Wrong pairing accepted");
      check(exchange(host,"POST","/pair",host.address(),null,host.code().getBytes(StandardCharsets.US_ASCII)).startsWith("HTTP/1.1 403"),"Pairing did not lock after three failures");
    }
    try(BrowserRemoteServer first=host(effects);BrowserRemoteServer second=host(effects)){
      check(!first.fingerprint().equals(second.fingerprint())&&!first.code().equals(second.code()),"Identity or pairing code reused between hosts");
      ArrayList<String> guestTokens=new ArrayList<>();
      for(int i=0;i<4;i++){
        String paired=exchange(first,"POST","/pair",first.address(),null,first.code().getBytes(StandardCharsets.US_ASCII));
        check(paired.startsWith("HTTP/1.1 200"),"Guest capacity below four");
        guestTokens.add(body(paired));
      }
      for(int i=0;i<3;i++)
        check(exchange(first,"POST","/pair",first.address(),null,first.code().getBytes(StandardCharsets.US_ASCII)).startsWith("HTTP/1.1 403"),"Guest capacity unbounded");
      check(exchange(first,"POST","/leave",first.address(),guestTokens.get(0),new byte[0]).startsWith("HTTP/1.1 200"),"Leaving guest did not free capacity");
      check(exchange(first,"POST","/pair",first.address(),null,first.code().getBytes(StandardCharsets.US_ASCII)).startsWith("HTTP/1.1 200"),"Capacity rejections consumed the incorrect-code lockout budget");
    }
    AtomicInteger oversizedEffects=new AtomicInteger();
    try(BrowserRemoteServer oversized=new BrowserRemoteServer("127.0.0.1",0,new byte[0],new byte[0],new byte[0],r->{oversizedEffects.incrementAndGet();return new byte[16385];},true)){
      oversized.start();String token=body(exchange(oversized,"POST","/pair",oversized.address(),null,oversized.code().getBytes(StandardCharsets.US_ASCII)));
      byte[] command=new CompanionProtocol.Request(UUID.randomUUID(),"reserve","song","").encode();
      String failed=exchange(oversized,"POST","/command",oversized.address(),token,command);
      String retry=exchange(oversized,"POST","/command",oversized.address(),token,command);
      check(body(failed).contains("metadata limit")&&body(failed).equals(body(retry))&&oversizedEffects.get()==1,"Oversized response allowed a duplicate side effect");
    }
    for(byte[] bad:new byte[][]{new byte[0],new byte[1025],new byte[22]}){
      try{BrowserRemoteServer.decode(bad);throw new AssertionError("Malformed browser command accepted");}catch(IOException expected){checks++;}
    }
    byte[] unicode=new CompanionProtocol.Request(UUID.randomUUID(),"search","{Tala} 🎵","José").encode();
    check(BrowserRemoteServer.decode(unicode).argument.equals("{Tala} 🎵"),"Bounded UTF-8 command corrupted");
    return checks;
  }
  public static void main(String[] args)throws Exception{System.out.println("Browser TLS checks: "+run());}
}
