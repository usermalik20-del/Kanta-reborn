package ph.kanta.reborn;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.util.*;
import ph.kanta.reborn.core.BrowserPairingQr;
import ph.kanta.reborn.core.BrowserRemoteServer;
import ph.kanta.reborn.core.CompanionProtocol;
import ph.kanta.reborn.core.connect.QrEncoder;
import ph.kanta.reborn.stage.QrCodeView;

/** Local HTTPS hosting and the in-app remote that checks the host fingerprint. */
final class BrowserCompanionDialog {
  static Dialog show(Activity activity,SessionController session){
    LinearLayout body=MenuDialogStyle.body(activity);
    LinearLayout warningCard=MenuDialogStyle.card(body,null);
    TextView warning=label(activity,activity.getString(R.string.browser_remote_warning));
    warning.setTextColor(ph.kanta.reborn.ui.KantaTheme.WARNING);
    warningCard.addView(warning);

    LinearLayout hosting=MenuDialogStyle.card(body,activity.getString(R.string.browser_remote_host_title), R.drawable.kanta_menu_phone_radio);
    Spinner addresses=new Spinner(activity);
    addresses.setMinimumHeight(MenuDialogStyle.dp(activity,48));
    hosting.addView(addresses);
    List<String> found=new ArrayList<>();
    Button host=MenuDialogStyle.button(activity,activity.getString(R.string.browser_remote_start_host),R.drawable.kanta_submenu_power,true,()->{if(!found.isEmpty())session.browserCompanion.start(found.get(addresses.getSelectedItemPosition()));});
    host.setTag("kanta.browser.host");MenuDialogStyle.addControl(hosting,host);
    TextView status=label(activity,"");
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    hosting.addView(status);
    Button revoke=MenuDialogStyle.button(activity,activity.getString(R.string.browser_remote_stop_host),R.drawable.kanta_submenu_stop,false,session.browserCompanion::stop);
    MenuDialogStyle.addControl(hosting,revoke);
    LinearLayout hostDetails=MenuDialogStyle.card(body,activity.getString(R.string.browser_remote_qr_title), R.drawable.kanta_submenu_qr);
    hostDetails.setVisibility(View.GONE);
    hostDetails.addView(label(activity,activity.getString(R.string.browser_remote_qr_help)));
    QrCodeView addressQr=new QrCodeView(activity);
    addressQr.setTag("kanta.browser.address-qr");
    addressQr.setContentDescription(activity.getString(R.string.browser_remote_qr_description));
    int size=Math.round(176*activity.getResources().getDisplayMetrics().density);
    LinearLayout.LayoutParams qrLayout=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,size);
    qrLayout.gravity=Gravity.CENTER_HORIZONTAL;
    hostDetails.addView(addressQr,qrLayout);
    TextView details=label(activity,"");details.setTextIsSelectable(true);hostDetails.addView(details);
    hostDetails.addView(label(activity,activity.getString(R.string.browser_remote_expiry)));
    LinearLayout connecting=MenuDialogStyle.card(body,activity.getString(R.string.browser_remote_client_title), R.drawable.kanta_submenu_link);
    MenuDialogStyle.addControl(connecting,MenuDialogStyle.button(activity,activity.getString(R.string.browser_remote_scan),R.drawable.kanta_submenu_scan,true,
        ()->activity.startActivity(new Intent(activity,BrowserPairingScanner.class))));
    connecting.addView(label(activity,activity.getString(R.string.browser_remote_client_help)));
    EditText address=input(activity,"Host private IPv4 address");MenuDialogStyle.addControl(connecting,address);
    String fingerprintLabel="Host certificate SHA-256 (64 hexadecimal characters)";
    TextView fingerprintCaption=label(activity,fingerprintLabel);
    EditText fingerprint=input(activity,"SHA-256 fingerprint");
    fingerprint.setId(View.generateViewId());
    fingerprintCaption.setLabelFor(fingerprint.getId());
    connecting.addView(fingerprintCaption);
    MenuDialogStyle.addControl(connecting,fingerprint);
    fingerprint.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(64)});
    TextView notice=label(activity,"");
    notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    notice.setTextColor(ph.kanta.reborn.ui.KantaTheme.WARNING);
    notice.setVisibility(View.GONE);
    MenuDialogStyle.addControl(connecting,MenuDialogStyle.button(activity,activity.getString(R.string.browser_remote_open_local),R.drawable.kanta_submenu_link,true,()->{
      try{
        String hostAddress=address.getText().toString().trim();CompanionProtocol.privateAddress(hostAddress);
        String pin=fingerprint.getText().toString().trim().toLowerCase(Locale.ROOT);
        if(!pin.matches("[a-f0-9]{64}"))throw new IllegalArgumentException();
        showRemote(activity,hostAddress,pin);
      }catch(Exception e){notice.setText(R.string.label_enter_the_host_s_private_ipv4_address_and_exact_64_char);notice.setVisibility(View.VISIBLE);}
    }));
    connecting.addView(notice);
    AlertDialog dialog=MenuDialogStyle.dialog(activity,R.string.browser_remote_title,R.drawable.kanta_menu_phone_radio,body).setPositiveButton("Done",null).create();
    dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    Runnable refresh=()->{
      BrowserCompanion c=session.browserCompanion;
      boolean active=c.active();
      boolean hasAddress=c.pairingOpen()&&!c.address.isEmpty();
      status.setText(c.status);
      hostDetails.setVisibility(hasAddress ? View.VISIBLE : View.GONE);
      if(hasAddress){
        String hostAddress=c.address.substring("https://".length(),c.address.lastIndexOf(':'));
        String payload=BrowserPairingQr.encode(hostAddress,c.fingerprint,c.code);
        if(addressQr.symbol()==null||!payload.equals(addressQr.symbol().text))addressQr.setSymbol(QrEncoder.encode(payload));
        details.setText(activity.getString(R.string.browser_remote_connection_details,c.address,c.code,c.fingerprint));
      }else{
        addressQr.setSymbol(null);
        details.setText("");
      }
      host.setEnabled(!found.isEmpty()&&!active&&!c.preparing);revoke.setEnabled(active);
    };
    Runnable update=new Runnable(){public void run(){
      status.removeCallbacks(this);
      if(dialog.isShowing()){refresh.run();if(session.browserCompanion.active())status.postDelayed(this,1000);}
    }};
    session.browserCompanion.changed=update;
    dialog.setOnDismissListener(d->{status.removeCallbacks(update);if(session.browserCompanion.changed==update)session.browserCompanion.changed=null;});
    dialog.show();MenuDialogStyle.styleShownDialog(dialog);update.run();
    session.io.execute(()->{List<String> values=CompanionControl.addresses();session.main.post(()->{if(!dialog.isShowing())return;found.addAll(values);addresses.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,values));refresh.run();});});
    return dialog;
  }
  static Dialog showRemote(Activity activity,String address,String fingerprint){
    return showRemote(activity,address,fingerprint,"",()->{});
  }
  // Required by the bundled remote UI. Every resource is intercepted by the pinned
  // transport; file/content/mixed access is disabled and external navigation denied.
  @android.annotation.SuppressLint("SetJavaScriptEnabled")
  static Dialog showRemote(Activity activity,String address,String fingerprint,String bootstrapCode,Runnable onClose){
    String origin="https://"+address+":"+BrowserRemoteServer.PORT;
    WebView web=new WebView(activity);web.setSaveEnabled(false);
    WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(false);
    settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    settings.setMediaPlaybackRequiresUserGesture(true);settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
    web.setWebChromeClient(new WebChromeClient(){@Override public void onPermissionRequest(PermissionRequest request){request.deny();}});
    final PinnedBrowserTransport transport;
    try{transport=new PinnedBrowserTransport(web,address,fingerprint,bootstrapCode);}catch(Exception e){
      web.destroy();return new AlertDialog.Builder(activity).setMessage("Could not prepare secure companion transport.")
          .setPositiveButton("Close",null).setOnDismissListener(d->onClose.run()).show();
    }
    web.addJavascriptInterface(transport,"KantaSecureTransport");
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !transport.sameOrigin(request.getUrl());}
      @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return transport.resource(request);}
    });
    AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(R.string.browser_remote_title).setView(web).setPositiveButton("Disconnect",null).create();
    dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks(){
      public void onActivityStopped(Activity a){}
      public void onActivityDestroyed(Activity a){if(a==activity)dialog.dismiss();}
      public void onActivityCreated(Activity a,Bundle state){} public void onActivityStarted(Activity a){} public void onActivityResumed(Activity a){} public void onActivityPaused(Activity a){} public void onActivitySaveInstanceState(Activity a,Bundle state){}
    };
    activity.getApplication().registerActivityLifecycleCallbacks(lifecycle);
    dialog.setOnDismissListener(d->{activity.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);transport.disconnect();web.removeJavascriptInterface("KantaSecureTransport");web.stopLoading();web.loadUrl("about:blank");web.clearHistory();web.removeAllViews();web.destroy();onClose.run();});
    dialog.show();web.loadUrl(origin+"/");return dialog;
  }
  private static TextView label(Activity a,String value){return MenuDialogStyle.label(a,value);}
  private static EditText input(Activity a,String hint){EditText value=new EditText(a);value.setHint(hint);value.setSingleLine(true);value.setSaveEnabled(false);value.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);value.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);MenuDialogStyle.styleInput(value);return value;}
}
