package ph.kanta.reborn;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import androidx.activity.ComponentActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraState;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import ph.kanta.reborn.StageSurface;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

/** One foreground preview binding; no capture, analysis, recording or playback authority. */
final class CameraStage {
  private final ComponentActivity owner;
  private final SessionController session;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private ProcessCameraProvider provider;
  private Preview preview;
  private PreviewView view;
  private android.view.View surface;
  private LiveData<CameraState> cameraStates;
  private Observer<CameraState> cameraObserver;
  private Observer<PreviewView.StreamState> streamObserver;
  private long generation;
  private boolean enabled, streaming;

  CameraStage(ComponentActivity owner, SessionController session) {
    this.owner = owner;
    this.session = session;
  }

  boolean enabled() {
    return enabled;
  }

  boolean streaming() {
    return streaming;
  }

  void start(android.view.View target) {
    stop();
    if (!session.foreground
        || target == null
        || owner.isFinishing()
        || owner.checkSelfPermission(Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
      unavailable("Camera permission is needed for the optional front-camera background.");
      return;
    }
    // Resolve a real preview surface before advertising the camera as enabled.
    surface = target;
    if (target instanceof PreviewView) {
      view = (PreviewView) target;
    } else if (target instanceof StageSurface) {
      view = new PreviewView(owner);
      view.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
      view.setScaleType(PreviewView.ScaleType.FILL_CENTER);
      view.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      ((StageSurface) target).mountCamera(view);
    } else {
      surface = null;
      unavailable("The stage does not provide a camera preview surface.");
      return;
    }
    enabled = true;
    session.cameraPreviewEnabled = true;
    long token = generation;
    streamObserver =
        state -> {
          if (token != generation || !enabled) return;
          streaming = state == PreviewView.StreamState.STREAMING;
          if (surface instanceof StageSurface) {
            ((StageSurface) surface).cameraFrame(streaming);
          }
          if (streaming) handler.removeCallbacksAndMessages(null);
        };
    view.getPreviewStreamState().observe(owner, streamObserver);
    handler.postDelayed(
        () -> {
          if (generation == token && enabled && !streaming)
            unavailable(
                "The front camera did not become ready. Choose another stage or try again.");
        },
        12000);
    try {
      var future = ProcessCameraProvider.getInstance(owner);
      future.addListener(
          () -> {
            if (token != generation || !enabled || !session.foreground) return;
            try {
              provider = future.get();
              if (!provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                unavailable("No usable front camera is available on this device.");
                return;
              }
              preview = new Preview.Builder().build();
              preview.setSurfaceProvider(view.getSurfaceProvider());
              Camera camera =
                  provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview);
              cameraStates = camera.getCameraInfo().getCameraState();
              cameraObserver =
                  state -> {
                    if (token == generation && enabled && state != null && state.getError() != null)
                      unavailable(
                          "The front camera is unavailable. Close other camera apps or choose a"
                              + " background.");
                  };
              cameraStates.observe(owner, cameraObserver);
            } catch (Exception failure) {
              unavailable(
                  "The front camera could not be opened. Your saved background is unchanged.");
            }
          },
          command -> handler.post(command));
    } catch (RuntimeException failure) {
      unavailable("The front camera could not be initialized. Choose another stage.");
    }
  }

  void moveTo(android.view.View target) {
    if (enabled && target != surface) start(target);
  }

  void stop() {
    generation++;
    enabled = false;
    streaming = false;
    session.cameraPreviewEnabled = false;
    handler.removeCallbacksAndMessages(null);
    if (cameraStates != null && cameraObserver != null) cameraStates.removeObserver(cameraObserver);
    if (view != null && streamObserver != null)
      view.getPreviewStreamState().removeObserver(streamObserver);
    if (provider != null && preview != null) provider.unbind(preview);
    if (surface instanceof StageSurface && view != null) {
      ((StageSurface) surface).unmountCamera(view);
    }
    if (preview != null) preview.setSurfaceProvider(null);
    cameraStates = null;
    cameraObserver = null;
    streamObserver = null;
    preview = null;
    view = null;
    surface = null;
  }

  private void unavailable(String message) {
    stop();
    session.message = message;
    session.publish();
  }
}
