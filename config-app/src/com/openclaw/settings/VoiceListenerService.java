package com.openclaw.settings;
import android.app.*; import android.content.*; import android.media.*; import android.os.*; import android.util.Log;
import org.json.JSONObject; import java.util.concurrent.*;
public class VoiceListenerService extends Service {
    private static final String TAG="OpenClaw.Voice", CHANNEL_ID="oc_voice";
    private static final int NOTIF_ID=1001, SAMPLE_RATE=16000, FRAME_SIZE=1280;
    private AudioRecord rec; private volatile boolean running; private ExecutorService exec;
    private WakeWordDetector wwd; private WhisperASR asr;
    public void onCreate(){super.onCreate();createChannel();exec=Executors.newCachedThreadPool();wwd=new WakeWordDetector(this);asr=new WhisperASR(this);}
    public int onStartCommand(Intent i,int f,int s){startForeground(NOTIF_ID,buildNotif("Listening..."));if(!running){running=true;exec.execute(this::loop);}return START_STICKY;}
    public IBinder onBind(Intent i){return null;}
    public void onDestroy(){running=false;if(rec!=null){rec.stop();rec.release();}exec.shutdownNow();}
    private void loop(){
        int buf=Math.max(AudioRecord.getMinBufferSize(SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT),FRAME_SIZE*2);
        rec=new AudioRecord(MediaRecorder.AudioSource.MIC,SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,buf);
        rec.startRecording(); Log.i(TAG,"listening");
        short[] frame=new short[FRAME_SIZE];
        while(running){
            try{
                int n=rec.read(frame,0,FRAME_SIZE);if(n<0)continue;
                if(wwd.detect(frame)){
                    updateNotif("Transcribing...");
                    short[] cmd=new short[SAMPLE_RATE*3];rec.read(cmd,0,cmd.length);
                    exec.execute(()->{
                        String t=asr.transcribe(cmd);
                        if(t!=null&&!t.trim().isEmpty())sendToBridge(t.trim());
                        updateNotif("Listening...");
                    });
                }
            }catch(Exception e){Log.e(TAG,"loop err",e);}
        }
    }
    private void sendToBridge(String text){
        try{
            JSONObject p=new JSONObject();p.put("text",text);p.put("source","voice");
            String msg=new JSONObject().put("id",(int)(System.currentTimeMillis()%100000)).put("method","Android.voiceCommand").put("params",p).toString();
            int port=getSharedPreferences("openclaw_config",0).getInt("bridge_port",7788);
            BridgeClient.sendOnce("127.0.0.1",port,msg);
        }catch(Exception e){Log.e(TAG,"sendToBridge",e);}
    }
    private void createChannel(){NotificationChannel c=new NotificationChannel(CHANNEL_ID,"OpenClaw",NotificationManager.IMPORTANCE_LOW);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}
    private Notification buildNotif(String t){return new Notification.Builder(this,CHANNEL_ID).setContentTitle("OpenClaw").setContentText(t).setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build();}
    private void updateNotif(String t){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIF_ID,buildNotif(t));}
}
