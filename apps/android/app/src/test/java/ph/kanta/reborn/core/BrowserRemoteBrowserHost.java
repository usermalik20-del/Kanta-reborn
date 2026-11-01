package ph.kanta.reborn.core;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Synthetic host for real-browser contract tests; never part of the application APK. */
public final class BrowserRemoteBrowserHost {
  public static void main(String[] args)throws Exception{
    Path assets=Paths.get(args[0]);List<String> ids=new ArrayList<>();boolean[] playing={false};
    Map<String,byte[]> artwork=new LinkedHashMap<>();
    for(int index=1;index<=8;index++){String name=String.format(Locale.ROOT,"%02d.webp",index);artwork.put("/assets/covers/"+name,Files.readAllBytes(assets.resolve("art").resolve("covers").resolve(name)));}
    BrowserRemoteServer host=new BrowserRemoteServer("127.0.0.1",0,Files.readAllBytes(assets.resolve("index.html")),Files.readAllBytes(assets.resolve("remote.js")),Files.readAllBytes(assets.resolve("remote.css")),artwork,request->{
      switch(request.action){
        case "reserve":ids.add(UUID.randomUUID().toString());break;
        case "cancel":ids.remove(request.argument);break;
        case "play":playing[0]=true;break;
        case "pause":case "stop":playing[0]=false;break;
        case "next":if(!ids.isEmpty())ids.remove(0);break;
      }
      String songs=request.action.equals("search")?"[{\"id\":\"fixture\",\"title\":\"Morning Light <script>\",\"artist\":\"KANTA Originals\",\"code\":\"001\"}]":"[]";
      StringJoiner queue=new StringJoiner(",","[","]");for(String id:ids)queue.add("{\"id\":\""+id+"\",\"title\":\"Morning Light\",\"singer\":\"Guest\"}");
      return ("{\"request\":\""+request.id+"\",\"version\":1,\"source\":\"local\",\"capabilities\":[\"snapshot\",\"search\",\"reserve\",\"cancel\",\"play\",\"pause\",\"next\",\"stop\"],\"current\":\"Morning Light\",\"playing\":"+playing[0]+",\"songs\":"+songs+",\"queue\":"+queue+"}").getBytes(StandardCharsets.UTF_8);
    },true);
    Runtime.getRuntime().addShutdownHook(new Thread(host::close));host.start();
    Files.writeString(Paths.get(args[1]),host.address()+"\n"+host.code(),StandardCharsets.US_ASCII);
    Thread.currentThread().join();
  }
}
