package ph.kanta.reborn;

import android.app.*;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.util.*;
import ph.kanta.reborn.core.CompanionProtocol;

/** Built-in remote uses the same locally served interface as browser guests. */
final class BrowserCompanionDialog {
  static Dialog show(Activity activity,SessionController session){
    LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
    int pad=(int)(18*activity.getResources().getDisplayMetrics().density);body.setPadding(pad,pad,pad,pad);
    body.setBackgroundColor(Color.rgb(10,17,39));
    ScrollView scroll=new ScrollView(activity);scroll.addView(body);
    body.addView(label(activity,"Host / Companion Remote"));
    body.addView(label(activity,"Host serves the remote securely on this Wi-Fi. Companion Remote opens the same interface inside KANTA; no other app is required."));
    Spinner addresses=new Spinner(activity);body.addView(addresses);
    List<String> found=new ArrayList<>();
    Button host=button(activity,"Host on this device",()->{if(!found.isEmpty())session.browserCompanion.start(found.get(addresses.getSelectedItemPosition()));});
    host.setTag("kanta.browser.host");body.addView(host);
    TextView details=label(activity,"");details.setTextIsSelectable(true);body.addView(details);
    Button revoke=button(activity,"Stop hosting / revoke all guests",session.browserCompanion::stop);body.addView(revoke);
    body.addView(label(activity,"Companion Remote · enter details from the host"));
    EditText address=input(activity,"Host private IPv4 address");body.addView(address);
    EditText fingerprint=input(activity,"Host certificate SHA-256 (64 hexadecimal characters)");body.addView(fingerprint);
    fingerprint.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(64)});
    TextView notice=label(activity,"");body.addView(notice);
    body.addView(button(activity,"Open Companion Remote",()->{
      try{
        String hostAddress=address.getText().toString().trim();CompanionProtocol.privateAddress(hostAddress);
        String pin=fingerprint.getText().toString().trim().toLowerCase(Locale.ROOT);
        if(!pin.matches("[a-f0-9]{64}"))throw new IllegalArgumentException();
        showRemote(activity,hostAddress,pin);
      }catch(Exception e){notice.setText("Enter the host's private IPv4 address and exact 64-character certificate fingerprint.");}
    }));
    body.addView(label(activity,"Browser guests: enter the full https:// address shown above. Verify the certificate SHA-256 fingerprint on the host before entering its pairing code. Cancel on a mismatch. Some browsers may require trusting the local certificate before continuing. No system-wide certificate or development workstation is required. Host pairing expires after five minutes, guests after ten minutes, and hosting after thirty minutes or app backgrounding."));
    AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("KANTA Companion").setView(scroll).setPositiveButton("Done",null).create();
    Runnable refresh=()->{
      BrowserCompanion c=session.browserCompanion;
      details.setText(c.status+(c.address.isEmpty()?"":"\n\nBrowser / app address\n"+c.address+"\n\nPairing code (private)\n"+c.code+"\n\nCertificate SHA-256\n"+c.fingerprint));
      host.setEnabled(!found.isEmpty()&&!c.active()&&!c.preparing);revoke.setEnabled(c.active());
    };
    session.browserCompanion.changed=refresh;
    dialog.setOnDismissListener(d->{if(session.browserCompanion.changed==refresh)session.browserCompanion.changed=null;});
    dialog.show();dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);refresh.run();
    session.io.execute(()->{List<String> values=CompanionControl.addresses();session.main.post(()->{if(!dialog.isShowing())return;found.addAll(values);addresses.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,values));refresh.run();});});
    return dialog;
  }
  static Dialog showRemote(Activity activity,String address,String fingerprint){
    String origin="https://"+address+":"+ph.kanta.reborn.core.BrowserRemoteServer.PORT;
    WebView web=new WebView(activity);web.setSaveEnabled(false);
    WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(false);
    settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    settings.setMediaPlaybackRequiresUserGesture(true);settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
    web.setWebChromeClient(new WebChromeClient(){@Override public void onPermissionRequest(PermissionRequest request){request.deny();}});
    final PinnedBrowserTransport transport;
    try{transport=new PinnedBrowserTransport(web,address,fingerprint);}catch(Exception e){web.destroy();return new AlertDialog.Builder(activity).setMessage("Could not prepare secure companion transport.").setPositiveButton("Close",null).show();}
    web.addJavascriptInterface(transport,"KantaSecureTransport");
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !transport.sameOrigin(request.getUrl());}
      @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return transport.resource(request);}
    });
    AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Companion Remote").setView(web).setPositiveButton("Disconnect",null).create();
    Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks(){
      public void onActivityStopped(Activity a){if(a==activity)dialog.dismiss();}
      public void onActivityDestroyed(Activity a){if(a==activity)dialog.dismiss();}
      public void onActivityCreated(Activity a,Bundle state){} public void onActivityStarted(Activity a){} public void onActivityResumed(Activity a){} public void onActivityPaused(Activity a){} public void onActivitySaveInstanceState(Activity a,Bundle state){}
    };
    activity.getApplication().registerActivityLifecycleCallbacks(lifecycle);
    dialog.setOnDismissListener(d->{activity.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);transport.close();web.removeJavascriptInterface("KantaSecureTransport");web.stopLoading();web.loadUrl("about:blank");web.clearHistory();web.removeAllViews();web.destroy();});
    dialog.show();dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);web.loadUrl(origin+"/");return dialog;
  }
  private static TextView label(Activity a,String value){TextView text=new TextView(a);text.setText(value);text.setPadding(0,10,0,10);return text;}
  private static EditText input(Activity a,String hint){EditText value=new EditText(a);value.setHint(hint);value.setSingleLine(true);value.setSaveEnabled(false);value.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);value.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);return value;}
  private static Button button(Activity a,String title,Runnable action){Button value=new Button(a);value.setAllCaps(false);value.setText(title);value.setOnClickListener(v->action.run());return value;}
}
