package com.openclaw.settings;
import android.content.Context; import android.util.Log; import java.io.*;
public class WhisperASR {
    private static final String BIN="/system/bin/whisper-cli", MODEL="/system/etc/openclaw/ggml-base.bin";
    private final Context ctx; private final boolean ok;
    public WhisperASR(Context ctx){this.ctx=ctx;ok=new File(BIN).exists()&&new File(MODEL).exists();}
    public String transcribe(short[] audio){
        if(!ok)return null;
        try{
            File w=new File(ctx.getCacheDir(),"w.wav"); writeWav(audio,w);
            Process p=new ProcessBuilder(BIN,"-m",MODEL,"-f",w.getAbsolutePath(),"-l","auto","--no-timestamps","-t","4").redirectErrorStream(true).start();
            StringBuilder sb=new StringBuilder();
            try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()))){
                String l; while((l=r.readLine())!=null){if(l.contains("-->")){int i=l.lastIndexOf(']');if(i>=0)sb.append(l.substring(i+1).trim()).append(' ');}else if(!l.startsWith("[")&&!l.trim().isEmpty())sb.append(l.trim()).append(' ');}
            }
            p.waitFor();w.delete();String r=sb.toString().trim();return r.isEmpty()?null:r;
        }catch(Exception e){Log.e("WhisperASR","err",e);return null;}
    }
    private void writeWav(short[] pcm,File out)throws IOException{
        int ds=pcm.length*2;
        try(DataOutputStream d=new DataOutputStream(new FileOutputStream(out))){
            d.writeBytes("RIFF");w32(d,36+ds);d.writeBytes("WAVEfmt ");w32(d,16);w16(d,1);w16(d,1);w32(d,16000);w32(d,32000);w16(d,2);w16(d,16);d.writeBytes("data");w32(d,ds);
            for(short s:pcm)w16(d,s);
        }
    }
    void w32(DataOutputStream d,int v)throws IOException{d.write(v&255);d.write((v>>8)&255);d.write((v>>16)&255);d.write((v>>24)&255);}
    void w16(DataOutputStream d,int v)throws IOException{d.write(v&255);d.write((v>>8)&255);}
}
