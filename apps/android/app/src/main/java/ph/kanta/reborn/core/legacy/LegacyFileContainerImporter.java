package ph.kanta.reborn.core.legacy;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.zip.Inflater;
import ph.kanta.reborn.core.songbook.ShubIndexProbe;

/** Bounded file-backed importer for user-supplied shubidx/shubblob pairs. */
public final class LegacyFileContainerImporter {
  private static final long MAX_BLOB_BYTES = 1024L * 1024L * 1024L;
  private static final int MAX_PAYLOAD_BYTES = 64 * 1024 * 1024;
  private static final byte[] KEY = hex("9eac94f7d1abf0c1e08e9f67d3a2f6fc45fda31d2477702dae565b259c49fe6b");
  private static final byte[] IV = "JtNpObAv6714@*#&".getBytes(StandardCharsets.US_ASCII);
  public static final class DecodedSong { public final String id,title,fileName; public final File file; public final MkmdDecoder.Signature signature; public final long decodedBytes; DecodedSong(String id,String title,String fileName,File file,MkmdDecoder.Signature signature,long decodedBytes){this.id=id;this.title=title;this.fileName=fileName;this.file=file;this.signature=signature;this.decodedBytes=decodedBytes;} }
  public static final class ImportError { public final int recordIndex; public final String message; ImportError(int recordIndex,String message){this.recordIndex=recordIndex;this.message=message;} }
  public static final class ImportResult { public final int totalSpans,decodedCount,skippedCount; public final List<DecodedSong> songs; public final List<ImportError> errors; ImportResult(int totalSpans,int decodedCount,int skippedCount,List<DecodedSong> songs,List<ImportError> errors){this.totalSpans=totalSpans;this.decodedCount=decodedCount;this.skippedCount=skippedCount;this.songs=songs;this.errors=errors;} }
  private LegacyFileContainerImporter() {}

  public static ImportResult importPair(
      File index, File blob, File outputDir) throws IOException {
    if (!index.isFile() || !blob.isFile()) throw new IOException("Both index and blob files are required");
    if (blob.length() <= 0 || blob.length() > MAX_BLOB_BYTES) throw new IOException("ShubBlob exceeds 1 GiB limit");
    byte[] indexBytes = readBounded(index, 16 * 1024 * 1024);
    List<ShubIndexProbe.TitleRecord> titles = ShubIndexProbe.decodeTitleRecords(indexBytes);
    if (titles.isEmpty()) throw new IOException("The Shub index has no title records");
    if (!outputDir.isDirectory() && !outputDir.mkdirs()) throw new IOException("Cannot create legacy import directory");
    ArrayList<Long> records = scanRecords(blob);
    if (records.size() < titles.size()) throw new IOException("The blob has fewer MKMD records than the index");
    ArrayList<DecodedSong> songs = new ArrayList<>();
    ArrayList<ImportError> errors = new ArrayList<>();
    int limit = titles.size();
    for (int i = 0; i < limit; i++) {
      try {
        byte[] payload = decodeRecord(blob, records.get(i));
        String title = titles.get(i).title;
        String ext = extension(payload);
        if (ext == null) throw new IOException("Decoded payload is not MIDI, WAV or MP3");
        String fileName = safe(title) + "-" + i + ext;
        File out = write(outputDir, fileName, payload);
        songs.add(new DecodedSong(
            UUID.randomUUID().toString(), title.isEmpty() ? "Legacy song " + i : title,
            fileName, out, signature(payload), payload.length));
      } catch (Exception failure) {
        errors.add(new ImportError(i, failure.getMessage() == null ? "Decode failed" : failure.getMessage()));
      }
    }
    int skipped = records.size() - songs.size();
    return new ImportResult(records.size(), songs.size(), skipped, songs, errors);
  }

  private static ArrayList<Long> scanRecords(File blob) throws IOException {
    ArrayList<Long> starts = new ArrayList<>();
    try (RandomAccessFile in = new RandomAccessFile(blob, "r")) {
      byte[] block = new byte[1024 * 1024]; long base = 0; int carry = 3; byte[] tail = new byte[3];
      while (base < in.length()) {
        int count = in.read(block, 0, (int)Math.min(block.length, in.length() - base));
        if (count <= 0) break;
        for (int i = 0; i < count; i++) {
          long absolute = base + i;
          if ((absolute & 1023L) != 0) continue;
          if (block[i] != 'M' || i + 3 >= count) {
            if (i + 3 >= count) { in.seek(absolute); byte[] h = new byte[4]; if (in.read(h) == 4 && h[0]=='M'&&h[1]=='K'&&h[2]=='M'&&h[3]=='D') starts.add(absolute); in.seek(base + count); }
            continue;
          }
          if (block[i+1]=='K' && block[i+2]=='M' && block[i+3]=='D') starts.add(absolute);
        }
        base += count;
      }
    }
    if (starts.isEmpty()) throw new IOException("No MKMD records found in ShubBlob");
    return starts;
  }

  private static byte[] decodeRecord(File blob, long start) throws IOException, GeneralSecurityException {
    try (RandomAccessFile in = new RandomAccessFile(blob, "r")) {
      in.seek(start); byte[] header = new byte[13]; in.readFully(header);
      if (header[0]!='M'||header[1]!='K'||header[2]!='M'||header[3]!='D'||header[4]!=1) throw new IOException("Invalid MKMD header");
      long decoded = u32(header,5), compressed = u32(header,9);
      if (decoded <= 0 || decoded > MAX_PAYLOAD_BYTES || compressed <= 0 || compressed > MAX_PAYLOAD_BYTES) throw new IOException("MKMD size exceeds limit");
      int encrypted = (int)(((compressed + 15) / 16) * 16); byte[] cipher = new byte[encrypted]; in.readFully(cipher);
      javax.crypto.Cipher aes = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
      aes.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(KEY,"AES"), new javax.crypto.spec.IvParameterSpec(IV));
      byte[] plain = aes.doFinal(cipher); Inflater inflater = new Inflater(true); inflater.setInput(plain,0,(int)compressed);
      ByteArrayOutputStream out = new ByteArrayOutputStream((int)Math.min(decoded,1<<20)); byte[] buf = new byte[8192];
      while (!inflater.finished()) { int n=inflater.inflate(buf); if(n==0 && inflater.needsInput()) break; out.write(buf,0,n); if(out.size()>decoded) throw new IOException("Inflated payload exceeds declared size"); }
      inflater.end(); byte[] payload=out.toByteArray(); if(payload.length!=decoded) throw new IOException("Decoded size mismatch"); return payload;
    } catch (java.util.zip.DataFormatException e) { throw new IOException("MKMD compression failed",e); }
  }
  private static long u32(byte[] b,int o){return (b[o]&255L)|((b[o+1]&255L)<<8)|((b[o+2]&255L)<<16)|((b[o+3]&255L)<<24);}
  private static byte[] readBounded(File f,int max)throws IOException{if(f.length()>max)throw new IOException("Index exceeds limit");try(FileInputStream in=new FileInputStream(f)){byte[] b=new byte[(int)f.length()];int o=0,n;while(o<b.length&&(n=in.read(b,o,b.length-o))>0)o+=n;if(o!=b.length)throw new IOException("Short index read");return b;}}
  private static File write(File dir,String name,byte[] data)throws IOException{File f=new File(dir,name),t=File.createTempFile("legacy-",".tmp",dir);try(FileOutputStream o=new FileOutputStream(t)){o.write(data);o.getFD().sync();}if(!t.renameTo(f))throw new IOException("Cannot save decoded song");return f;}
  private static String safe(String s){StringBuilder b=new StringBuilder();for(char c:s.toCharArray())b.append(Character.isLetterOrDigit(c)||c==' '||c=='-'||c=='_'?c:'_');String x=b.toString().trim();return x.isEmpty()?"legacy-song":x;}
  private static String extension(byte[] p){if(p.length>=4&&p[0]=='M'&&p[1]=='T'&&p[2]=='h'&&p[3]=='d')return ".mid";if(p.length>=12&&p[0]=='R'&&p[1]=='I'&&p[2]=='F'&&p[3]=='F'&&p[8]=='W'&&p[9]=='A'&&p[10]=='V'&&p[11]=='E')return ".wav";if(p.length>=3&&p[0]=='I'&&p[1]=='D'&&p[2]=='3')return ".mp3";return null;}
  private static MkmdDecoder.Signature signature(byte[] p){String e=extension(p);return ".mid".equals(e)?MkmdDecoder.Signature.STANDARD_MIDI:".wav".equals(e)?MkmdDecoder.Signature.WAVE:".mp3".equals(e)?MkmdDecoder.Signature.MP3_ID3:MkmdDecoder.Signature.UNKNOWN;}
  private static byte[] hex(String s){byte[] out=new byte[s.length()/2];for(int i=0;i<out.length;i++)out[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return out;}
}
