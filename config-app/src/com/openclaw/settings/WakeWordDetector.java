package com.openclaw.settings;
import android.content.Context; import android.content.res.AssetFileDescriptor; import android.util.Log;
import org.tensorflow.lite.Interpreter; import java.io.*; import java.nio.MappedByteBuffer; import java.nio.channels.FileChannel;
public class WakeWordDetector {
    private Interpreter tflite; private final float[] in=new float[1280]; private final float[][] out=new float[1][1]; private boolean ok;
    public WakeWordDetector(Context ctx){
        try{AssetFileDescriptor fd=ctx.getAssets().openFd("wakeword.tflite");
        MappedByteBuffer m=new FileInputStream(fd.getFileDescriptor()).getChannel().map(FileChannel.MapMode.READ_ONLY,fd.getStartOffset(),fd.getDeclaredLength());
        Interpreter.Options o=new Interpreter.Options();o.setNumThreads(2);tflite=new Interpreter(m,o);ok=true;
        }catch(Exception e){Log.w("WakeWord","not loaded: "+e.getMessage());}
    }
    public boolean detect(short[] frame){
        if(!ok)return false;
        try{for(int i=0;i<Math.min(frame.length,1280);i++)in[i]=frame[i]/32768f;tflite.run(new float[][]{in},out);return out[0][0]>=0.5f;}
        catch(Exception e){return false;}
    }
}
