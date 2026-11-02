package ph.kanta.reborn.core;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** A bounded HTTPS adapter. The supplied handler remains the only route to host state. */
public final class BrowserRemoteServer implements AutoCloseable {
  public interface Handler { byte[] apply(CompanionProtocol.Request request) throws Exception; }
  public static final int PORT=45732;
  private static final SecureRandom RANDOM=new SecureRandom();
  private final Handler handler;
  private final byte[] page,script,style;
  private final Map<String,byte[]> artwork;
  private final SSLServerSocket server;
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor();
  private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
  private final Map<String,Guest> guests=new HashMap<>();
  private final long started=System.nanoTime();
  private final String code,authority,origin,fingerprint;
  private volatile boolean closed;
  private int failures;
  private static final class Reply { final byte[] request,body; Reply(byte[] r,byte[] b){request=r.clone();body=b.clone();} }
  private static final class Guest {
    final long created=System.nanoTime(); final Map<UUID,Reply> replies=new HashMap<>(); int bytes;
    boolean valid(){return System.nanoTime()-created<TimeUnit.MINUTES.toNanos(10);}
  }
  public String fingerprint(){return fingerprint;}
  public String code(){return code;}
  public String address(){return origin;}
  public boolean active(){return !closed;}
  public BrowserRemoteServer(String address,byte[] page,byte[] script,byte[] style,Handler handler)throws Exception {
    this(address,PORT,page,script,style,Collections.emptyMap(),handler,false);
  }
  public BrowserRemoteServer(String address,byte[] page,byte[] script,byte[] style,Map<String,byte[]> artwork,Handler handler)throws Exception {
    this(address,PORT,page,script,style,artwork,handler,false);
  }
  BrowserRemoteServer(String address,int port,byte[] page,byte[] script,byte[] style,Handler handler,boolean test)throws Exception {
    this(address,port,page,script,style,Collections.emptyMap(),handler,test);
  }
  BrowserRemoteServer(String address,int port,byte[] page,byte[] script,byte[] style,Map<String,byte[]> artwork,Handler handler,boolean test)throws Exception {
    if(!test)CompanionProtocol.privateAddress(address);
    this.handler=Objects.requireNonNull(handler);this.page=page.clone();this.script=script.clone();this.style=style.clone();
    if(page.length>131072||script.length>131072||style.length>65536)throw new IOException("Asset limit");
    Set<String> allowed=new HashSet<>();for(int i=1;i<=8;i++)allowed.add(String.format(Locale.ROOT,"/assets/covers/%02d.webp",i));
    if(!allowed.containsAll(artwork.keySet()))throw new IOException("Unexpected artwork route");
    this.artwork=new HashMap<>();int artworkBytes=0;
    for(Map.Entry<String,byte[]> entry:artwork.entrySet()){
      byte[] value=Objects.requireNonNull(entry.getValue());
      if(value.length==0||value.length>160000||artworkBytes>1500000-value.length)throw new IOException("Artwork size limit");
      this.artwork.put(entry.getKey(),value.clone());artworkBytes+=value.length;
    }
    KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
    KeyPair keys=generator.generateKeyPair();
    X509Certificate certificate=certificate(keys,InetAddress.getByName(address).getAddress());
    fingerprint=hexText(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    char[] password=CompanionProtocol.newCode().toCharArray();
    KeyStore store=KeyStore.getInstance(KeyStore.getDefaultType());store.load(null,password);
    store.setKeyEntry("session",keys.getPrivate(),password,new java.security.cert.Certificate[]{certificate});
    KeyManagerFactory managers=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());managers.init(store,password);
    Arrays.fill(password,'\0');SSLContext tls=SSLContext.getInstance("TLS");tls.init(managers.getKeyManagers(),null,RANDOM);
    server=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket();
    server.setEnabledProtocols(Arrays.stream(server.getSupportedProtocols()).filter(p->p.equals("TLSv1.2")||p.equals("TLSv1.3")).toArray(String[]::new));
    server.bind(new InetSocketAddress(address,port),4);server.setSoTimeout(1000);
    authority=address+":"+server.getLocalPort();origin="https://"+authority;code=CompanionProtocol.newCode();
  }
  public void start(){worker.execute(()->{
    try {
      while(!closed&&System.nanoTime()-started<TimeUnit.MINUTES.toNanos(30)){
        Socket peer;try{peer=server.accept();}catch(SocketTimeoutException e){continue;}
        sockets.add(peer);
        ScheduledFuture<?> deadline=deadlines.schedule(()->{try{peer.close();}catch(IOException ignored){}},8,TimeUnit.SECONDS);
        try(peer){
          if(!peer.getInetAddress().isLoopbackAddress())CompanionProtocol.privateAddress(peer.getInetAddress().getHostAddress());
          peer.setSoTimeout(2500);((SSLSocket)peer).startHandshake();serve(peer);
        }catch(Exception ignored){}finally{deadline.cancel(false);sockets.remove(peer);}
      }
    }catch(IOException ignored){}finally{close();}
  });}
  private void serve(Socket peer)throws Exception {
    InputStream in=peer.getInputStream();ByteArrayOutputStream header=new ByteArrayOutputStream();int state=0;
    while(state!=4){int b=in.read();if(b<0||header.size()>=8192)throw new IOException("Header limit");header.write(b);
      state=(state==0||state==2)?(b==13?state+1:0):(b==10?state+1:0);
    }
    String[] lines=new String(header.toByteArray(),StandardCharsets.US_ASCII).split("\r\n");
    String[] first=lines[0].split(" ",-1);
    if(first.length!=3||!first[2].equals("HTTP/1.1"))throw new IOException("Invalid framing");
    Map<String,String> headers=new HashMap<>();
    for(int i=1;i<lines.length;i++){
      int colon=lines[i].indexOf(':');if(colon<1)throw new IOException("Invalid header");
      String key=lines[i].substring(0,colon).toLowerCase(Locale.ROOT);
      if(!key.matches("[a-z0-9-]+")||headers.put(key,lines[i].substring(colon+1).trim())!=null)throw new IOException("Duplicate header");
    }
    if(!authority.equals(headers.get("host"))||headers.containsKey("transfer-encoding"))throw new IOException("Wrong authority");
    String requestOrigin=headers.get("origin");
    if(requestOrigin!=null&&!requestOrigin.equals(origin)){reply(peer,403,"text/plain",new byte[0]);return;}
    String site=headers.get("sec-fetch-site");
    if(site!=null&&!site.equals("same-origin")&&!site.equals("none")){reply(peer,403,"text/plain",new byte[0]);return;}
    int length=0;
    if(headers.containsKey("content-length")){
      String raw=headers.get("content-length");if(!raw.matches("[0-9]{1,4}"))throw new IOException("Invalid length");length=Integer.parseInt(raw);
    }
    if(length>1024)throw new IOException("Body limit");
    if(first[0].equals("GET")&&length==0){
      switch(first[1]){
        case "/":reply(peer,200,"text/html; charset=utf-8",page);return;
        case "/remote.js":reply(peer,200,"text/javascript; charset=utf-8",script);return;
        case "/remote.css":reply(peer,200,"text/css; charset=utf-8",style);return;
        default:
          if(first[1].matches("/assets/covers/0[1-8]\\.webp")){
            byte[] cover=artwork.get(first[1]);
            if(cover!=null){reply(peer,200,"image/webp",cover);return;}
          }
          reply(peer,404,"text/plain",new byte[0]);return;
      }
    }
    if(!first[0].equals("POST")||!"application/octet-stream".equals(headers.get("content-type"))||!origin.equals(requestOrigin)){
      reply(peer,403,"text/plain",new byte[0]);return;
    }
    byte[] body=new byte[length];new DataInputStream(in).readFully(body);
    guests.entrySet().removeIf(e->!e.getValue().valid());
    if(first[1].equals("/pair")){
      boolean allowed=failures<3&&System.nanoTime()-started<TimeUnit.MINUTES.toNanos(5)&&guests.size()<4;
      if(!allowed||!MessageDigest.isEqual(code.getBytes(StandardCharsets.US_ASCII),body)){
        failures++;reply(peer,403,"text/plain",new byte[0]);return;
      }
      String token=randomToken();guests.put(token,new Guest());reply(peer,200,"text/plain",token.getBytes(StandardCharsets.US_ASCII));return;
    }
    String authorization=headers.getOrDefault("authorization","");
    Guest guest=authorization.startsWith("Bearer ")?guests.get(authorization.substring(7)):null;
    if(guest==null||!guest.valid()||closed){reply(peer,401,"text/plain",new byte[0]);return;}
    if(first[1].equals("/leave")){guests.remove(authorization.substring(7));reply(peer,200,"text/plain",new byte[0]);return;}
    if(!first[1].equals("/command")){reply(peer,404,"text/plain",new byte[0]);return;}
    CompanionProtocol.Request request=decode(body);Reply old=guest.replies.get(request.id);
    if(old!=null){
      if(!MessageDigest.isEqual(old.request,body)){reply(peer,409,"text/plain",new byte[0]);return;}
      reply(peer,200,"application/json",old.body);return;
    }
    if(guest.replies.size()>=512||guest.bytes>1024*1024-16384){guests.remove(authorization.substring(7));reply(peer,401,"text/plain",new byte[0]);return;}
    byte[] response;
    try{response=handler.apply(request);}catch(Exception e){response="{\"error\":\"Host could not complete the command. Refresh the queue before trying again.\"}".getBytes(StandardCharsets.UTF_8);}
    if(response==null||response.length>16384)response="{\"error\":\"Host response exceeded the metadata limit. Refresh the queue before trying again.\"}".getBytes(StandardCharsets.UTF_8);
    guest.replies.put(request.id,new Reply(body,response));guest.bytes+=body.length+response.length;
    if(!closed)reply(peer,200,"application/json",response);
  }
  private void reply(Socket socket,int status,String type,byte[] body)throws IOException {
    String header="HTTP/1.1 "+status+" "+(status==200?"OK":"Rejected")+"\r\nContent-Type: "+type+
      "\r\nContent-Length: "+body.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nX-Frame-Options: DENY\r\nPermissions-Policy: camera=(), microphone=(), geolocation=()\r\nContent-Security-Policy: default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'\r\n\r\n";
    OutputStream out=socket.getOutputStream();out.write(header.getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();
  }
  public static CompanionProtocol.Request decode(byte[] body)throws IOException {
    if(body.length<22||body.length>1024)throw new IOException("Command size");
    DataInputStream in=new DataInputStream(new ByteArrayInputStream(body));
    UUID id=new UUID(in.readLong(),in.readLong());String action=field(in,16),argument=field(in,128),singer=field(in,80);
    if(in.available()!=0||!Arrays.asList("snapshot","search","reserve","cancel","play","pause","next","stop").contains(action))throw new IOException("Unsupported command");
    if(action.equals("search")&&argument.trim().isEmpty())throw new IOException("Empty search");
    if(action.equals("cancel"))try{UUID.fromString(argument);}catch(IllegalArgumentException e){throw new IOException("Invalid reservation",e);}
    return new CompanionProtocol.Request(id,action,argument,singer);
  }
  private static String field(DataInputStream in,int max)throws IOException {
    int size=in.readUnsignedShort();if(size>max*4)throw new IOException("Field limit");
    byte[] bytes=new byte[size];in.readFully(bytes);
    String text;try{text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();}catch(java.nio.charset.CharacterCodingException e){throw new IOException("Invalid UTF-8",e);}if(text.length()>max)throw new IOException("Field limit");return text;
  }
  @Override public void close(){closed=true;try{server.close();}catch(IOException ignored){}
    for(Socket socket:sockets)try{socket.close();}catch(IOException ignored){}worker.shutdownNow();deadlines.shutdownNow();}
  private static String randomToken(){byte[] b=new byte[32];RANDOM.nextBytes(b);return hexText(b);}
  private static byte[] der(int tag,byte[]...parts)throws IOException {
    ByteArrayOutputStream value=new ByteArrayOutputStream();for(byte[] part:parts)value.write(part);
    byte[] body=value.toByteArray();ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(tag);
    if(body.length<128)out.write(body.length);else{int n=body.length>255?2:1;out.write(128+n);for(int i=n-1;i>=0;i--)out.write((body.length>>(8*i))&255);}
    out.write(body);return out.toByteArray();
  }
  private static String hexText(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
  private static byte[] parseHex(String value){byte[] out=new byte[value.length()/2];for(int i=0;i<out.length;i++)out[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16);return out;}
  private static byte[] hex(String s){return parseHex(s);}
  private static X509Certificate certificate(KeyPair keys,byte[] address)throws Exception {
    byte[] algorithm=hex("300d06092a864886f70d01010b0500");
    byte[] name=der(0x30,der(0x31,der(0x30,hex("0603550403"),der(0x0c,"KANTA LAN Remote".getBytes(StandardCharsets.UTF_8)))));
    DateTimeFormatter format=DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'",Locale.ROOT).withZone(ZoneOffset.UTC);
    byte[] validity=der(0x30,der(0x17,format.format(Instant.now().minusSeconds(300)).getBytes(StandardCharsets.US_ASCII)),der(0x17,format.format(Instant.now().plusSeconds(86400)).getBytes(StandardCharsets.US_ASCII)));
    byte[] serial=new byte[16];RANDOM.nextBytes(serial);serial[0]&=0x7f;serial[0]|=1;
    byte[] san=der(0x30,hex("0603551d11"),der(0x04,der(0x30,der(0x87,address))));
    byte[] extended=der(0x30,hex("0603551d25"),der(0x04,hex("300a06082b06010505070301")));
    byte[] tbs=der(0x30,hex("a003020102"),der(0x02,serial),algorithm,name,validity,name,keys.getPublic().getEncoded(),der(0xa3,der(0x30,san,extended)));
    Signature signature=Signature.getInstance("SHA256withRSA");signature.initSign(keys.getPrivate());signature.update(tbs);
    byte[] certificate=der(0x30,tbs,algorithm,der(0x03,new byte[]{0},signature.sign()));
    X509Certificate result=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(certificate));
    result.verify(keys.getPublic());return result;
  }
}
