package ph.kanta.reborn.core;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

/** Real stream handshakes and adversarial authenticated records, without Android dependencies. */
public final class CompanionRegressionTests {
  private static int checks;
  private static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
  private interface Attempt{void run()throws Exception;}
  private static void rejects(Attempt action,String message)throws Exception{
    try{action.run();}catch(IOException expected){check(true,message);return;}throw new AssertionError(message);
  }
  private static final class Capture extends FilterOutputStream {
    final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
    Capture(OutputStream out){super(out);}
    @Override public void write(int value)throws IOException{out.write(value);bytes.write(value);}
    @Override public void write(byte[] b,int off,int count)throws IOException{out.write(b,off,count);bytes.write(b,off,count);}
  }
  private static final class Pair implements AutoCloseable {
    final ServerSocket listener=new ServerSocket(0,1,InetAddress.getLoopbackAddress());
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final Socket clientSocket=new Socket(InetAddress.getLoopbackAddress(),listener.getLocalPort());
    final Socket hostSocket=listener.accept();
    final Capture clientOutput=new Capture(clientSocket.getOutputStream());
    final Capture hostOutput=new Capture(hostSocket.getOutputStream());
    CompanionProtocol.Channel host,client;
    Pair(String hostCode,String clientCode)throws Exception{
      clientSocket.setSoTimeout(2000);hostSocket.setSoTimeout(2000);
      Future<CompanionProtocol.Channel> future=worker.submit(()->{
        try{return CompanionProtocol.handshake(hostSocket.getInputStream(),hostOutput,hostCode,true);}
        catch(IOException e){hostSocket.close();throw e;}
      });
      try{
        client=CompanionProtocol.handshake(clientSocket.getInputStream(),clientOutput,clientCode,false);
        host=future.get(3,TimeUnit.SECONDS);clientOutput.bytes.reset();hostOutput.bytes.reset();
      }catch(Exception e){close();throw e;}
    }
    public void close()throws IOException{
      if(client!=null)client.close();if(host!=null)host.close();
      clientSocket.close();hostSocket.close();listener.close();worker.shutdownNow();
    }
  }
  public static int run()throws Exception{
    checks=0;
    for(String url:new String[]{"https://youtu.be/dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://m.youtube.com/shorts/dQw4w9WgXcQ", "https://youtube.com/embed/dQw4w9WgXcQ"})
      check(YouTubeVideoReference.parse(url)!=null,"Valid video reference rejected");
    for(String url:new String[]{"http://youtu.be/dQw4w9WgXcQ", "https://youtu.be.evil.com/dQw4w9WgXcQ", "https://youtube.com/watch?v=invalid", "https://youtube.com/watch?v=dQw4w9WgXcQ&v=dQw4w9WgXcQ", "https://user@youtube.com/watch?v=dQw4w9WgXcQ"})
      check(YouTubeVideoReference.parse(url)==null,"Unsafe video reference accepted");
    ReservationQueue mixed = new ReservationQueue();
    Song local = new Song("local-1", "Local", "", "", "local.mid", false);
    Song videoMetadata = new Song("web-1", "Online", "", "", "online.reference", false);
    YouTubeVideoReference video = YouTubeVideoReference.parse("https://youtu.be/dQw4w9WgXcQ");
    UUID firstRequest = UUID.randomUUID();
    ReservationQueue.Entry localEntry = mixed.reserve(firstRequest, local, "Singer");
    check(mixed.reserve(firstRequest, local, "Singer").id.equals(localEntry.id),
        "Replayed local reservation created another entry");
    ReservationQueue.Entry onlineOne = mixed.reserveVideo(UUID.randomUUID(), videoMetadata, video, "Singer");
    ReservationQueue.Entry onlineTwo = mixed.reserveVideo(UUID.randomUUID(), videoMetadata, video, "Singer");
    check(onlineOne.youtube != null && onlineTwo.youtube != null,
        "Online reservation source was lost");
    check(!onlineOne.id.equals(onlineTwo.id), "Intentional repeat reused playback identity");
    check(mixed.peek().id.equals(localEntry.id), "Mixed queue reordered the first local song");
    check(mixed.remove(localEntry.id) && mixed.peek().id.equals(onlineOne.id),
        "Next did not preserve FIFO order across source types");
    check(mixed.remove(onlineOne.id) && mixed.peek().id.equals(onlineTwo.id),
        "Next skipped a repeated online reservation");
    check(mixed.remove(onlineTwo.id) && mixed.peek() == null,
        "Cancellation left a stale online reservation");
    // A corrupted persisted queue must not partially replace the live queue.
    ReservationQueue restored = new ReservationQueue();
    ReservationQueue.Entry retained = restored.reserve(UUID.randomUUID(), local, "Singer");
    try {
      restored.restore(Arrays.asList(onlineOne, null));
      throw new AssertionError("Malformed restored queue accepted");
    } catch (IllegalArgumentException expected) {
      check(restored.peek().id.equals(retained.id), "Invalid restore replaced live queue");
    }
    try {
      restored.restore(Arrays.asList(onlineOne, onlineOne));
      throw new AssertionError("Duplicate restored reservation accepted");
    } catch (IllegalArgumentException expected) {
      check(restored.peek().id.equals(retained.id), "Duplicate restore replaced live queue");
    }
    try {
      restored.reserve(null, local, "Singer");
      throw new AssertionError("Null request identity accepted");
    } catch (NullPointerException expected) {
      check(restored.snapshot().size() == 1, "Null request changed queue");
    }
    mixed.reserveVideo(UUID.randomUUID(), videoMetadata, video, "");
    mixed.clear();
    check(mixed.peek() == null, "Queue clear left a stale online reservation");
    String code=CompanionProtocol.newCode();
    check(code.matches("[0-9a-f]{32}"),"Pair code format");
    check(!code.equals(CompanionProtocol.newCode()),"Fresh pairing entropy");
    for(String address:new String[]{"10.0.0.2","172.16.0.1","172.31.255.254","192.168.1.2"})
      check(CompanionProtocol.privateAddress(address).length==4,"Private IPv4 rejected");
    for(String address:new String[]{"0.0.0.0","127.0.0.1","8.8.8.8","172.15.1.1","172.32.0.1","192.169.1.1","example.com","192.168.1.999","192.168.01.1","192.168.1.2:80","::1","10.0.0.1\n"})
      rejects(()->CompanionProtocol.privateAddress(address),"Unsafe address accepted: "+address);
    rejects(()->new Pair(code,CompanionProtocol.newCode()),"Wrong pairing secret accepted");
    rejects(()->CompanionProtocol.handshake(new ByteArrayInputStream(new byte[0]),new ByteArrayOutputStream(),"123456",true),"Short PIN accepted");
    byte[] firstFrame;
    try(Pair p=new Pair(code,code)){
      p.client.send("request".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      check(Arrays.equals(p.host.receive(),"request".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"Authenticated client record");
      firstFrame=p.clientOutput.bytes.toByteArray();
      check(!new String(firstFrame,java.nio.charset.StandardCharsets.ISO_8859_1).contains("request"),"Plain command leaked");
      p.host.send(new byte[]{1,2,3});check(Arrays.equals(p.client.receive(),new byte[]{1,2,3}),"Authenticated host record");
      p.clientOutput.write(firstFrame);p.clientOutput.flush();
      rejects(p.host::receive,"Record replay accepted");
    }
    try(Pair p=new Pair(code,code)){
      p.client.send("request".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      check(!Arrays.equals(firstFrame,p.clientOutput.bytes.toByteArray()),"Session key/nonce reuse");p.host.receive();
    }
    try(Pair p=new Pair(code,code)){
      p.client.send(new byte[]{4,5,6});byte[] frame=p.clientOutput.bytes.toByteArray();p.host.receive();
      // Use the opposite direction, whose expected counter is still zero.
      p.hostOutput.write(frame);p.hostOutput.flush();rejects(p.client::receive,"Direction reflection accepted");
    }
    try(Pair p=new Pair(code,code)){
      p.client.send(new byte[]{4,5,6});byte[] frame=p.clientOutput.bytes.toByteArray();p.host.receive();
      frame[frame.length-1]^=1;
      // The next expected sequence is one; a forged counter and tag must still fail authentication.
      ByteBuffer.wrap(frame).putLong(4,1);
      p.clientOutput.write(frame);p.clientOutput.flush();rejects(p.host::receive,"Tampered ciphertext accepted");
    }
    try(Pair p=new Pair(code,code)){
      p.clientOutput.write(ByteBuffer.allocate(12).putInt(Integer.MAX_VALUE).putLong(0).array());p.clientOutput.flush();
      rejects(p.host::receive,"Oversized record allocation");
    }
    try(Pair p=new Pair(code,code)){
      p.clientOutput.write(ByteBuffer.allocate(12).putInt(16).putLong(2).array());p.clientOutput.flush();
      rejects(p.host::receive,"Out-of-order record accepted");
    }
    try(Pair p=new Pair(code,code)){
      p.client.send(new byte[CompanionProtocol.MAX_RECORD-16]);check(p.host.receive().length==CompanionProtocol.MAX_RECORD-16,"Maximum bounded record");
      rejects(()->p.client.send(new byte[CompanionProtocol.MAX_RECORD]),"Oversized plaintext accepted");
      p.client.close();rejects(()->p.client.send(new byte[]{1}),"Revoked channel reused");
    }
    UUID id=UUID.randomUUID();
    for(String action:new String[]{"snapshot","search","reserve","cancel","play","pause","next"}){
      CompanionProtocol.Request r=CompanionProtocol.Request.decode(new CompanionProtocol.Request(id,action,"Búwan","Gelo").encode());
      check(r.id.equals(id)&&r.action.equals(action)&&r.argument.equals("Búwan")&&r.singer.equals("Gelo"),"Command round trip");
    }
    rejects(()->new CompanionProtocol.Request(id,"microphone","","").encode(),"Sensor command accepted");
    for(String blank:new String[]{"", " ", "\t\n"})
      rejects(()->new CompanionProtocol.Request(id,"search",blank,"").encode(),"Blank search accepted");
    rejects(()->new CompanionProtocol.Request(id,"reserve","x".repeat(129),"").encode(),"Oversized song ID");
    rejects(()->new CompanionProtocol.Request(id,"reserve","one","x".repeat(81)).encode(),"Oversized singer");
    byte[] encoded=new CompanionProtocol.Request(id,"snapshot","","").encode();
    rejects(()->CompanionProtocol.Request.decode(Arrays.copyOf(encoded,encoded.length+1)),"Trailing command bytes");
    for(int i=0;i<encoded.length;i++){
      final int length=i;rejects(()->CompanionProtocol.Request.decode(Arrays.copyOf(encoded,length)),"Truncated command accepted");
    }
    CompanionProtocol.Replay replay=new CompanionProtocol.Replay();
    byte[] result=new byte[]{1};replay.remember(id,result);result[0]=2;
    check(replay.get(id)[0]==1,"Replay response aliases mutable state");
    byte[] returned=replay.get(id);returned[0]=3;check(replay.get(id)[0]==1,"Replay response read aliases state");
    rejects(()->replay.remember(id,new byte[]{4}),"Duplicate completion accepted");
    for(int i=1;i<512;i++)replay.remember(UUID.randomUUID(),new byte[]{1});
    check(replay.full(),"Replay bound absent");
    rejects(()->replay.remember(UUID.randomUUID(),new byte[]{1}),"Replay cache evicted an executable identity");
    check(replay.get(id)[0]==1,"Original identity evicted at capacity");
    check(CompanionProtocol.jsonText("{\"x\":\"[{\\\"}\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)).contains("x"),"JSON string bracket handling");
    rejects(()->CompanionProtocol.jsonText("[".repeat(9).concat("]".repeat(9)).getBytes(java.nio.charset.StandardCharsets.UTF_8)),"Recursive peer JSON accepted");
    rejects(()->CompanionProtocol.jsonText(new byte[]{(byte)0xc3,0x28}),"Invalid peer UTF-8 accepted");
    rejects(()->CompanionProtocol.jsonText("{".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"Truncated peer JSON accepted");
    rejects(()->CompanionProtocol.jsonText("}".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"Unbalanced peer JSON accepted");
    return checks;
  }
}
