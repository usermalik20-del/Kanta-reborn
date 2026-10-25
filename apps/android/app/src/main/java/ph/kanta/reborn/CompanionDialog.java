package ph.kanta.reborn;

import android.app.*;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Local pairing stays inside the existing Control flow; credentials are never persisted. */
final class CompanionDialog {
  static Dialog show(Activity activity,SessionController session){
    LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
    int pad=(int)(16*activity.getResources().getDisplayMetrics().density);body.setPadding(pad,pad,pad,pad);
    ScrollView scroll=new ScrollView(activity);scroll.addView(body);
    body.addView(button(activity,"Host / Companion Remote · apps and browsers",()->BrowserCompanionDialog.show(activity,session)));
    TextView help=label(activity,"Android companion · Same private Wi-Fi/Ethernet network. The full code grants song search, reservations, cancellation and Play/Pause/Next. Share it only with someone you trust. One companion, 5-minute pairing window, 10-minute session. Leaving either app revokes the connection. iPhone/iPad can control an Android host using encrypted companion pairing. iPhone hosting still uses a separate legacy protocol.");body.addView(help);
    TextView status=label(activity,session.companion.status);body.addView(status);
    TextView pairing=label(activity,"");pairing.setTextIsSelectable(true);body.addView(pairing);
    body.addView(label(activity,"Host on this device"));
    Spinner hostAddress=new Spinner(activity);body.addView(hostAddress);
    List<String> addresses=new ArrayList<>();
    Button host=button(activity,"Start hosting",()->{
      if(!addresses.isEmpty())session.companion.startHost(addresses.get(hostAddress.getSelectedItemPosition()));
    });host.setEnabled(false);host.setTag("kanta.companion.host");body.addView(host);
    body.addView(label(activity,"Or control another Android device"));
    EditText address=input(activity,"Host IPv4 address (for example 192.168.1.20)");body.addView(address);
    EditText code=input(activity,"32-character pairing code");
    code.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    code.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
    code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});
    body.addView(code);
    Button join=button(activity,"Connect",()->{session.companion.join(address.getText().toString().trim(),code.getText().toString().trim());code.setText("");});body.addView(join);
    Button stop=button(activity,"Stop / revoke companion",session.companion::stop);stop.setTag("kanta.companion.stop");body.addView(stop);
    EditText search=input(activity,"Search host songs by title, artist or code");search.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(128)});body.addView(search);
    EditText singer=input(activity,"Singer name (optional)");singer.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});body.addView(singer);
    Button searchButton=button(activity,"Search host",()->session.companion.send("search",search.getText().toString().trim(),""));
    searchButton.setTag("kanta.companion.search");body.addView(searchButton);
    LinearLayout transport=new LinearLayout(activity);
    Button playPause=button(activity,"Play / Pause",()->session.companion.send(session.companion.remote!=null&&session.companion.remote.optBoolean("playing")?"pause":"play","",""));
    Button next=button(activity,"Next",()->session.companion.send("next","",""));
    transport.addView(playPause);transport.addView(next);
    body.addView(transport);
    TextView current=label(activity,"");body.addView(current);
    LinearLayout results=new LinearLayout(activity);results.setOrientation(LinearLayout.VERTICAL);body.addView(results);
    AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Companion control").setView(scroll).setPositiveButton("Close",null).create();
    Runnable refresh=()->{
      playPause.setText(session.companion.remote!=null&&session.companion.remote.optBoolean("playing")?"Pause":"Play");
      status.setText(session.companion.status);pairing.setText(session.companion.pairing.isEmpty()?"":"Private pairing code\n"+session.companion.pairing);
      host.setEnabled(!addresses.isEmpty()&&!session.companion.enabled());join.setEnabled(!session.companion.enabled());
      searchButton.setEnabled(session.companion.client()&&!search.getText().toString().trim().isEmpty());
      playPause.setEnabled(session.companion.client()&&session.companion.remote!=null);
      next.setEnabled(session.companion.client()&&session.companion.remote!=null);
      results.removeAllViews();JSONObject remote=session.companion.remote;
      current.setText(remote==null?"Search and controls above apply only to the connected host.":remote.optString("current")+(remote.optBoolean("playing")?" · Playing":" · Paused"));
      if(remote==null)return;
      JSONArray songs=remote.optJSONArray("songs");
      if(songs!=null)for(int i=0;i<Math.min(songs.length(),10);i++){
        JSONObject song=songs.optJSONObject(i);if(song==null)continue;
        results.addView(label(activity,song.optString("code")+" · "+song.optString("title")+" · "+song.optString("artist")));
        results.addView(button(activity,"Reserve",()->session.companion.send("reserve",song.optString("id"),singer.getText().toString())));
      }
      results.addView(label(activity,"Host Up Next · first 10 reservations"));
      JSONArray queue=remote.optJSONArray("queue");
      if(queue!=null)for(int i=0;i<Math.min(queue.length(),10);i++){
        JSONObject entry=queue.optJSONObject(i);if(entry==null)continue;
        results.addView(label(activity,entry.optString("title")+" · "+entry.optString("singer")));
        results.addView(button(activity,"Cancel reservation",()->session.companion.send("cancel",entry.optString("id"),"")));
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
    dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
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
  private static TextView label(Activity a,String text){TextView v=new TextView(a);v.setText(text);v.setPadding(0,12,0,12);return v;}
  private static EditText input(Activity a,String hint){EditText v=new EditText(a);v.setHint(hint);v.setSingleLine(true);v.setSaveEnabled(false);return v;}
  private static Button button(Activity a,String text,Runnable action){Button b=new Button(a);b.setText(text);b.setAllCaps(false);b.setOnClickListener(v->action.run());return b;}
}
