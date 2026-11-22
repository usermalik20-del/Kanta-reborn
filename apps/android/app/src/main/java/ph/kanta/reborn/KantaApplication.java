package ph.kanta.reborn;

import android.app.Application;

public final class KantaApplication extends Application {
  SessionController session;
  AutomaticDiagnostics automaticDiagnostics;

  @Override
  public void onCreate() {
    super.onCreate();
    automaticDiagnostics = new AutomaticDiagnostics(this);
    DiagnosticEvents diagnostics = new DiagnosticEvents(this);
    diagnostics.installCrashCapture(automaticDiagnostics::markCrash);
    session = new SessionController(this, diagnostics);
  }
}
