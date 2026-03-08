package com.openclaw.settings;
import android.app.Activity; import android.content.*; import android.os.Bundle;
import android.view.View; import android.widget.*;
public class MainActivity extends Activity {
    public static final String PREFS="openclaw_config";
    public static final String KEY_MODEL_PROVIDER="model_provider",KEY_API_KEY="api_key",
        KEY_MODEL_NAME="model_name",KEY_SERVER_HOST="server_host",KEY_SERVER_PORT="server_port",
        KEY_GATEWAY_TOKEN="gateway_token",KEY_WAKE_WORD="wake_word",
        KEY_VOICE_ENABLED="voice_enabled",KEY_BRIDGE_PORT="bridge_port";
    private SharedPreferences prefs;
    private Spinner spinnerProvider;
    private EditText etApiKey,etModelName,etServerHost,etServerPort,etGatewayToken,etWakeWord,etBridgePort;
    private Switch switchVoice; private TextView tvStatus; private Button btnSave,btnTest;
    protected void onCreate(Bundle b){
        super.onCreate(b);setContentView(R.layout.activity_main);
        prefs=getSharedPreferences(PREFS,0);
        bindViews();loadSettings();
        btnSave.setOnClickListener(v->{saveSettings();if(switchVoice.isChecked())startVoice();else stopService(new Intent(this,VoiceListenerService.class));});
        btnTest.setOnClickListener(v->testConn());
        updateStatus();
    }
    private void bindViews(){
        spinnerProvider=findViewById(R.id.spinnerProvider);etApiKey=findViewById(R.id.etApiKey);
        etModelName=findViewById(R.id.etModelName);etServerHost=findViewById(R.id.etServerHost);
        etServerPort=findViewById(R.id.etServerPort);etGatewayToken=findViewById(R.id.etGatewayToken);
        etWakeWord=findViewById(R.id.etWakeWord);etBridgePort=findViewById(R.id.etBridgePort);
        switchVoice=findViewById(R.id.switchVoice);tvStatus=findViewById(R.id.tvStatus);
        btnSave=findViewById(R.id.btnSave);btnTest=findViewById(R.id.btnTest);
        spinnerProvider.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,
            new String[]{"Anthropic (Claude)","OpenAI (GPT)","Local / Custom"}));
    }
    private void loadSettings(){
        String p=prefs.getString(KEY_MODEL_PROVIDER,"anthropic");
        spinnerProvider.setSelection("openai".equals(p)?1:"local".equals(p)?2:0);
        etApiKey.setText(prefs.getString(KEY_API_KEY,""));
        etModelName.setText(prefs.getString(KEY_MODEL_NAME,"claude-sonnet-4-5"));
        etServerHost.setText(prefs.getString(KEY_SERVER_HOST,"127.0.0.1"));
        etServerPort.setText(String.valueOf(prefs.getInt(KEY_SERVER_PORT,18789)));
        etGatewayToken.setText(prefs.getString(KEY_GATEWAY_TOKEN,""));
        etWakeWord.setText(prefs.getString(KEY_WAKE_WORD,"hey openclaw"));
        etBridgePort.setText(String.valueOf(prefs.getInt(KEY_BRIDGE_PORT,7788)));
        switchVoice.setChecked(prefs.getBoolean(KEY_VOICE_ENABLED,false));
    }
    private void saveSettings(){
        String[] ps={"anthropic","openai","local"};
        SharedPreferences.Editor e=prefs.edit();
        e.putString(KEY_MODEL_PROVIDER,ps[spinnerProvider.getSelectedItemPosition()]);
        e.putString(KEY_API_KEY,etApiKey.getText().toString().trim());
        e.putString(KEY_MODEL_NAME,etModelName.getText().toString().trim());
        e.putString(KEY_SERVER_HOST,etServerHost.getText().toString().trim());
        e.putInt(KEY_SERVER_PORT,pi(etServerPort.getText().toString(),18789));
        e.putString(KEY_GATEWAY_TOKEN,etGatewayToken.getText().toString().trim());
        e.putString(KEY_WAKE_WORD,etWakeWord.getText().toString().trim());
        e.putInt(KEY_BRIDGE_PORT,pi(etBridgePort.getText().toString(),7788));
        e.putBoolean(KEY_VOICE_ENABLED,switchVoice.isChecked());
        e.apply(); Toast.makeText(this,"Saved",Toast.LENGTH_SHORT).show();
    }
    private void startVoice(){startForegroundService(new Intent(this,VoiceListenerService.class));}
    private void testConn(){
        btnTest.setEnabled(false);
        new Thread(()->{
            boolean ok=false; try{java.net.Socket s=new java.net.Socket();s.connect(new java.net.InetSocketAddress("127.0.0.1",prefs.getInt(KEY_BRIDGE_PORT,7788)),2000);s.close();ok=true;}catch(Exception e){}
            boolean f=ok; runOnUiThread(()->{Toast.makeText(this,f?"✅ Bridge OK":"❌ Bridge not running",Toast.LENGTH_SHORT).show();btnTest.setEnabled(true);updateStatus();});
        }).start();
    }
    private void updateStatus(){
        new Thread(()->{
            boolean ok=false;try{java.net.Socket s=new java.net.Socket();s.connect(new java.net.InetSocketAddress("127.0.0.1",prefs.getInt(KEY_BRIDGE_PORT,7788)),1000);s.close();ok=true;}catch(Exception e){}
            boolean f=ok;runOnUiThread(()->{tvStatus.setText(f?"✅ Bridge: Running":"❌ Bridge: Stopped");});
        }).start();
    }
    private int pi(String s,int d){try{return Integer.parseInt(s.trim());}catch(Exception e){return d;}}
    public static String getConfig(Context ctx,String k,String d){return ctx.getSharedPreferences(PREFS,0).getString(k,d);}
    public static int getConfigInt(Context ctx,String k,int d){return ctx.getSharedPreferences(PREFS,0).getInt(k,d);}
}
