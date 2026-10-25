package ph.kanta.reborn;

import android.net.Uri;
import android.webkit.*;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.*;
import org.json.*;

/** Every native request checks the exact host leaf pin, including otherwise trusted certificates. */
final class PinnedBrowserTransport implements AutoCloseable {
  private final String address,origin;
  private final SSLSocketFactory tls;
  private final WebView web;
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private final Set<HttpsURLConnection> connections=ConcurrentHashMap.newKeySet();
  private final AtomicBoolean busy=new AtomicBoolean();
  private volatile boolean closed;
  PinnedBrowserTransport(WebView web,String address,String fingerprint)throws Exception{
    this.web=web;this.address=address;origin="https://"+address+":"+ph.kanta.reborn.core.BrowserRemoteServer.PORT;
    SSLContext context=SSLContext.getInstance("TLS");
    context.init(null,new TrustManager[]{new X509TrustManager(){
      public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
      public void checkClientTrusted(X509Certificate[] chain,String auth)throws CertificateException{throw new CertificateException("Client certificates unsupported");}
      public void checkServerTrusted(X509Certificate[] chain,String auth)throws CertificateException{
        try{
          if(chain==null||chain.length==0)throw new CertificateException("Missing host identity");
          chain[0].checkValidity();
          StringBuilder actual=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()))actual.append(String.format(Locale.ROOT,"%02x",b&255));
          if(!MessageDigest.isEqual(actual.toString().getBytes(StandardCharsets.US_ASCII),fingerprint.getBytes(StandardCharsets.US_ASCII)))throw new CertificateException("Host fingerprint mismatch");
        }catch(GeneralSecurityException e){throw new CertificateException("Host identity rejected",e);}
      }
    }},new SecureRandom());tls=context.getSocketFactory();
  }
  boolean sameOrigin(Uri url){return "https".equals(url.getScheme())&&address.equals(url.getHost())&&url.getPort()==ph.kanta.reborn.core.BrowserRemoteServer.PORT&&url.getUserInfo()==null;}
  WebResourceResponse resource(WebResourceRequest request){
    try{
      if(closed||!sameOrigin(request.getUrl())||!request.getMethod().equals("GET"))return denied();
      String path=request.getUrl().getEncodedPath();
      if(!Arrays.asList("/","/remote.js","/remote.css").contains(path)||request.getUrl().getQuery()!=null)return denied();
      Result response=fetch(path,null,"");
      if(response.status!=200)return denied();
      String type=path.equals("/")?"text/html":path.equals("/remote.js")?"text/javascript":"text/css";
      return new WebResourceResponse(type,"UTF-8",200,"OK",response.headers,new ByteArrayInputStream(response.bytes));
    }catch(Exception e){return denied();}
  }
  private static WebResourceResponse denied(){return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream("Host certificate or request rejected. Check the exact fingerprint and shared Wi-Fi.".getBytes(StandardCharsets.UTF_8)));}
  /** The bridge cannot read files, navigate, invoke sensors or address any endpoint except this pinned host. */
  @JavascriptInterface public void post(String id,String path,String encoded,String token){
    if(closed||id==null||!id.matches("[a-f0-9-]{36}")||path==null||!Arrays.asList("/pair","/command","/leave").contains(path)||encoded==null||encoded.length()>1400||token==null||!token.matches("([a-f0-9]{64})?")||!busy.compareAndSet(false,true))return;
    worker.execute(()->{
      int status=0;String body="";
      try{byte[] bytes=Base64.decode(encoded,Base64.NO_WRAP);if(bytes.length>1024)throw new IOException("Body limit");Result response=fetch(path,bytes,token);status=response.status;body=new String(response.bytes,StandardCharsets.UTF_8);}catch(Exception ignored){}
      busy.set(false);
      if(!closed){
        int resultStatus=status;String resultBody=body;
        web.post(()->{if(!closed){
          try{JSONObject result=new JSONObject();result.put("id",id);result.put("status",resultStatus);result.put("body",resultBody);
            web.evaluateJavascript("window.dispatchEvent(new CustomEvent('kanta-response',{detail:"+result.toString()+"}))",null);
          }catch(JSONException ignored){}
        }});
      }
    });
  }
  private static final class Result {int status;byte[] bytes;Map<String,String> headers=new HashMap<>();}
  private Result fetch(String path,byte[] body,String token)throws Exception{
    if(closed)throw new IOException("Closed");
    HttpsURLConnection connection=(HttpsURLConnection)new URL(origin+path).openConnection();connections.add(connection);
    try{
      if(closed)throw new IOException("Closed");
      connection.setSSLSocketFactory(tls);connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(2500);connection.setReadTimeout(2500);connection.setUseCaches(false);
      connection.setRequestProperty("Origin",origin);connection.setRequestProperty("Sec-Fetch-Site","same-origin");
      if(body!=null){connection.setRequestMethod("POST");connection.setRequestProperty("Content-Type","application/octet-stream");
        if(!token.isEmpty())connection.setRequestProperty("Authorization","Bearer "+token);
        connection.setDoOutput(true);connection.setFixedLengthStreamingMode(body.length);try(OutputStream output=connection.getOutputStream()){output.write(body);}}
      Result result=new Result();result.status=connection.getResponseCode();
      for(Map.Entry<String,List<String>> entry:connection.getHeaderFields().entrySet())if(entry.getKey()!=null&&!entry.getValue().isEmpty())result.headers.put(entry.getKey(),entry.getValue().get(0));
      int max=body==null?131072:16384;
      try(InputStream input=result.status>=400?connection.getErrorStream():connection.getInputStream()){
        ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        if(input!=null)while((n=input.read(buffer))!=-1){output.write(buffer,0,n);if(output.size()>max)throw new IOException("Response limit");}
        result.bytes=output.toByteArray();
      }
      return result;
    }finally{connections.remove(connection);connection.disconnect();}
  }
  public void close(){closed=true;for(HttpsURLConnection connection:connections)connection.disconnect();worker.shutdownNow();}
}
