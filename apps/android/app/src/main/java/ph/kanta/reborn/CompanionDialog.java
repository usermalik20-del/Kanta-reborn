package ph.kanta.reborn;

import android.app.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Local pairing stays inside the existing Control flow; credentials are never persisted. */
final class CompanionDialog {
  static Dialog show(Activity activity,SessionController session){
    LinearLayout body=MenuDialogStyle.body(activity);
    LinearLayout guidance=MenuDialogStyle.card(body,null);
    guidance.addView(label(activity,activity.getString(R.string.android_companion_help)));
    TextView status=label(activity,session.companion.status);
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    guidance.addView(status);

    LinearLayout hosting=MenuDialogStyle.card(body,activity.getString(R.string.browser_remote_host_title),R.drawable.kanta_menu_phone_radio);
    Spinner hostAddress=new Spinner(activity);
    hostAddress.setMinimumHeight(MenuDialogStyle.dp(activity,48));
    hosting.addView(hostAddress);
    List<String> addresses=new ArrayList<>();
    Button host=MenuDialogStyle.button(activity,"Start hosting",R.drawable.kanta_submenu_power,true,()->{
      if(!addresses.isEmpty())session.companion.startHost(addresses.get(hostAddress.getSelectedItemPosition()));
    });host.setEnabled(false);host.setTag("kanta.companion.host");MenuDialogStyle.addControl(hosting,host);
    TextView pairing=label(activity,"");pairing.setTextIsSelectable(true);hosting.addView(pairing);

    LinearLayout connecting=MenuDialogStyle.card(body,"Or control another Android device",R.drawable.kanta_submenu_link);
    EditText address=input(activity,"Host IPv4 address (for example 192.168.1.20)");MenuDialogStyle.addControl(connecting,address);
    EditText code=input(activity,"32-character pairing code");
    code.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    code.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
    code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});
    MenuDialogStyle.addControl(connecting,code);
    Button join=MenuDialogStyle.button(activity,"Connect",R.drawable.kanta_submenu_link,true,()->{session.companion.join(address.getText().toString().trim(),code.getText().toString().trim());code.setText("");});MenuDialogStyle.addControl(connecting,join);
    Button stop=MenuDialogStyle.button(activity,"Stop / revoke companion",R.drawable.kanta_submenu_stop,false,session.companion::stop);stop.setTag("kanta.companion.stop");MenuDialogStyle.addControl(connecting,stop);

    LinearLayout playback=MenuDialogStyle.card(body,"Now playing");
    TextView current=label(activity,"");playback.addView(current);
    Button playPause=button(activity,"Play / Pause",()->session.companion.send(session.companion.remote!=null&&session.companion.remote.optBoolean("playing")?"pause":"play","",""));
    Button next=button(activity,"Next",()->session.companion.send("next","",""));
    MenuDialogStyle.addControl(playback,playPause);MenuDialogStyle.addControl(playback,next);

    LinearLayout searchCard=MenuDialogStyle.card(body,"Search host songs");
    EditText search=input(activity,"Search host songs by title, artist or code");search.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(128)});MenuDialogStyle.addControl(searchCard,search);
    EditText singer=input(activity,"Singer name (optional)");singer.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});MenuDialogStyle.addControl(searchCard,singer);
    Button searchButton=button(activity,"Search host",()->session.companion.send("search",search.getText().toString().trim(),""));
    searchButton.setTag("kanta.companion.search");MenuDialogStyle.addControl(searchCard,searchButton);
    LinearLayout results=MenuDialogStyle.column(activity);searchCard.addView(results);
    LinearLayout queueCard=MenuDialogStyle.card(body,"Host Up Next · first 10 reservations");
    LinearLayout reservations=MenuDialogStyle.column(activity);queueCard.addView(reservations);
    AlertDialog dialog=MenuDialogStyle.dialog(activity,R.string.android_companion_title,R.drawable.kanta_menu_phone_radio,body).setPositiveButton("Close",null).create();
    dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    Runnable refresh=()->{
      playPause.setText(session.companion.remote!=null&&session.companion.remote.optBoolean("playing")?"Pause":"Play");
      status.setText(session.companion.status);pairing.setText(session.companion.pairing.isEmpty()?"":"Private pairing code\n"+session.companion.pairing);
      host.setEnabled(!addresses.isEmpty()&&!session.companion.enabled());join.setEnabled(!session.companion.enabled());
      searchButton.setEnabled(session.companion.client()&&!search.getText().toString().trim().isEmpty());
      playPause.setEnabled(session.companion.client()&&session.companion.remote!=null);
      next.setEnabled(session.companion.client()&&session.companion.remote!=null);
      pairing.setVisibility(session.companion.pairing.isEmpty()?View.GONE:View.VISIBLE);
      results.removeAllViews();reservations.removeAllViews();JSONObject remote=session.companion.remote;
      queueCard.setVisibility(remote==null?View.GONE:View.VISIBLE);
      current.setText(remote==null?"Search and playback controls apply only to the connected host.":remote.optString("current")+(remote.optBoolean("playing")?" · Playing":" · Paused"));
      if(remote==null)return;
      JSONArray songs=remote.optJSONArray("songs");
      if(songs!=null)for(int i=0;i<Math.min(songs.length(),10);i++){
        JSONObject song=songs.optJSONObject(i);if(song==null)continue;
        results.addView(label(activity,song.optString("code")+" · "+song.optString("title")+" · "+song.optString("artist")));
        MenuDialogStyle.addControl(results,button(activity,"Reserve",()->session.companion.send("reserve",song.optString("id"),singer.getText().toString())));
      }
      JSONArray queue=remote.optJSONArray("queue");
      if(queue!=null)for(int i=0;i<Math.min(queue.length(),10);i++){
        JSONObject entry=queue.optJSONObject(i);if(entry==null)continue;
        reservations.addView(label(activity,entry.optString("title")+" · "+entry.optString("singer")));
        MenuDialogStyle.addControl(reservations,button(activity,"Cancel reservation",()->session.companion.send("cancel",entry.optString("id"),"")));
      }
    };
    search.addTextChangedListener(new android.text.TextWatcher(){
      public void beforeTextChanged(CharSequence text,int start,int count,int after){}
      public void onTextChanged(CharSequence text,int start,int before,int count){refresh.run();}
      public void afterTextChanged(android.text.Editable text){}
    });
    session.companion.changed=refresh;
    dialog.setOnDismissListener(d->{if(session.companion.changed==refresh)session.companion.changed=null;code.setText("");});
    dialog.show();
    MenuDialogStyle.styleShownDialog(dialog);
    refresh.run();
    session.io.execute(()->{
      List<String> found=CompanionControl.addresses();
      session.main.post(()->{
        if(!dialog.isShowing())return;addresses.addAll(found);
        hostAddress.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,found));refresh.run();
      });
    });
    return dialog;
  }
  private static TextView label(Activity a,String text){return MenuDialogStyle.label(a,text);}
  private static EditText input(Activity a,String hint){EditText v=new EditText(a);v.setHint(hint);v.setSingleLine(true);v.setSaveEnabled(false);MenuDialogStyle.styleInput(v);return v;}
  private static Button button(Activity a,String text,Runnable action){return MenuDialogStyle.button(a,text,false,action);}
}
