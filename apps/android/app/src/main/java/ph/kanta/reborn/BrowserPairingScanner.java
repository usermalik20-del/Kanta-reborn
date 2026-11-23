package ph.kanta.reborn;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import androidx.activity.ComponentActivity;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import ph.kanta.reborn.core.BrowserPairingQr;

/** Explicit, local-only scan. Neither frames nor decoded text are kept or forwarded to other apps. */
public final class BrowserPairingScanner extends ComponentActivity {
  private final ExecutorService frames=Executors.newSingleThreadExecutor();
  private final AtomicBoolean consumed=new AtomicBoolean();
  private final QRCodeReader reader=new QRCodeReader();
  private ProcessCameraProvider provider;
  private Preview preview;
  private ImageAnalysis analysis;
  private PreviewView camera;
  private TextView status;

  @Override protected void onCreate(Bundle state){
    super.onCreate(state);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    LinearLayout body=MenuDialogStyle.body(this);
    status=MenuDialogStyle.label(this,getString(R.string.browser_remote_scan_help));
    status.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
    body.addView(status);
    camera=new PreviewView(this);
    camera.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
    body.addView(camera,new LinearLayout.LayoutParams(-1,MenuDialogStyle.dp(this,242)));
    MenuDialogStyle.addControl(body,MenuDialogStyle.button(this,getString(android.R.string.cancel),R.drawable.kanta_submenu_cancel,false,this::finish));
    ScrollView scroll=new ScrollView(this);scroll.addView(body);setContentView(scroll);
    var permission=registerForActivityResult(new ActivityResultContracts.RequestPermission(),allowed->{
      if(allowed)startCamera();else unavailable();
    });
    if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();
    else permission.launch(Manifest.permission.CAMERA);
  }

  private void startCamera(){
    var pending=ProcessCameraProvider.getInstance(this);
    pending.addListener(()->{
      if(isFinishing()||isDestroyed()||consumed.get())return;
      try{
        provider=pending.get();
        CameraSelector lens=provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            ? CameraSelector.DEFAULT_BACK_CAMERA : CameraSelector.DEFAULT_FRONT_CAMERA;
        preview=new Preview.Builder().build();
        preview.setSurfaceProvider(camera.getSurfaceProvider());
        analysis=new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        analysis.setAnalyzer(frames,this::decode);
        provider.bindToLifecycle(this,lens,preview,analysis);
      }catch(Exception e){stopCamera();unavailable();}
    },androidx.core.content.ContextCompat.getMainExecutor(this));
  }

  private void decode(ImageProxy frame){
    try{
      if(consumed.get())return;
      int width=frame.getWidth(),height=frame.getHeight();
      if(width<1||height<1||(long)width*height>2_100_000)return;
      ImageProxy.PlaneProxy plane=frame.getPlanes()[0];
      if(plane.getPixelStride()!=1)return;
      ByteBuffer data=plane.getBuffer();
      int stride=plane.getRowStride();
      if(stride<width||(long)(height-1)*stride+width>data.remaining())return;
      int offset=data.position();
      byte[] luma=new byte[width*height];
      for(int row=0;row<height;row++){
        data.position(offset+row*stride);
        data.get(luma,row*width,width);
      }
      var source=new PlanarYUVLuminanceSource(luma,width,height,0,0,width,height,false);
      String text=reader.decode(new BinaryBitmap(new HybridBinarizer(source))).getText();
      BrowserPairingQr pairing=BrowserPairingQr.parse(text);
      if(pairing!=null)runOnUiThread(()->{
        if(isFinishing()||isDestroyed()||!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
            ||!consumed.compareAndSet(false,true))return;
        stopCamera();
        BrowserCompanionDialog.showRemote(this,pairing.address,pairing.fingerprint,pairing.code,this::finish);
      });
    }catch(ReaderException|RuntimeException ignored){
      // No result yet. Never write results or camera exceptions to diagnostics.
    }finally{frame.close();}
  }

  private void unavailable(){status.setText(R.string.browser_remote_scan_unavailable);}

  private void stopCamera(){
    if(analysis!=null)analysis.clearAnalyzer();
    if(provider!=null&&preview!=null&&analysis!=null)provider.unbind(preview,analysis);
    preview=null;analysis=null;
  }

  @Override protected void onDestroy(){
    consumed.set(true);stopCamera();frames.shutdownNow();
    super.onDestroy();
  }
}
