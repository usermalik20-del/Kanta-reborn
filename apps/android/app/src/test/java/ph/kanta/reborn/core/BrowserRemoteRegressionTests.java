package ph.kanta.reborn.core;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.*;

/** Uses real generated identities and TLS sockets, not a plaintext HTTP test double. */
public final class BrowserRemoteRegressionTests {
  private static int checks;
  private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
  private static String exchange(BrowserRemoteServer host,String method,String path,String origin,String token,byte[] body)throws Exception {
    SSLContext tls=SSLContext.getInstance("TLS");
    tls.init(null,new TrustManager[]{new X509TrustManager(){
      public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
      public void checkClientTrusted(X509Certificate[] c,String a)throws java.security.cert.CertificateException{throw new java.security.cert.CertificateException();}
      public void checkServerTrusted(X509Certificate[] chain,String algorithm)throws java.security.cert.CertificateException{
        try{
          StringBuilder actual=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()))actual.append(String.format(Locale.ROOT,"%02x",b&255));
          if(!actual.toString().equals(host.fingerprint()))throw new java.security.cert.CertificateException("Wrong pin");
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
    BrowserRemoteServer server=new BrowserRemoteServer("127.0.0.1",0,"<h1>Remote</h1>".getBytes(StandardCharsets.UTF_8),new byte[0],new byte[0],request->{effects.incrementAndGet();return ("{\"request\":\""+request.id+"\"}").getBytes(StandardCharsets.UTF_8);},true);server.start();return server;
  }
  public static int run()throws Exception{
    checks=0;AtomicInteger effects=new AtomicInteger();
    try(BrowserRemoteServer host=host(effects)){
      String shell=exchange(host,"GET","/",null,null,new byte[0]);
      check(shell.startsWith("HTTP/1.1 200"),"Shell did not load over TLS");
      check(shell.contains("frame-ancestors 'none'")&&shell.contains("Cache-Control: no-store")&&!shell.contains(host.code()),"Shell leaked credential or lacks hardening");
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
