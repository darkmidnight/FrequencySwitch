package com.darkmidnight.audioworkbench;

import com.darkmidnight.audioworkbench.CombinationFilter.BandPassFilter;
import com.darkmidnight.materia.BinaryUtils;
import com.darkmidnight.materia.NewBitClass;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

public class RecordThread extends Thread {

    private final int sampleSizeInBits = 16;      // 16 bits per sample.
    private final int samplesPerRead = 4096;      // Samples per buffer
    private final float sampleRate = 44100.0F;    //8000,11025,16000,22050,44100
    private final int channels = 1;               //1,2
    private final boolean signed = true;          //true,false
    private final boolean bigEndian = true;       //true,false

    private final int sampleSizeInBytes = (sampleSizeInBits / 8);

    private final double freqBinSize = sampleRate / samplesPerRead;
    private final int byteBufferSize = samplesPerRead * sampleSizeInBytes;

    private final AudioFormat audioFormat;
    private final FastFourierTransformer transformer;
    private final TargetDataLine targetDataLine;
    private SourceDataLine sdline;

    private CombinationFilter cf;
    private PlayModes playMode;
    private boolean triggerOutput;

    public synchronized void setCf(CombinationFilter cf) {
        this.cf = cf;
    }

    public synchronized void setPlayMode(PlayModes playMode) {
        this.playMode = playMode;
    }

    public void setTriggerOutput(boolean triggerOutput) {
        this.triggerOutput = triggerOutput;
    }

    public RecordThread(TargetDataLine targetDataLine) {
        cf = new CombinationFilter();
        this.targetDataLine = targetDataLine;
        transformer = new FastFourierTransformer(DftNormalization.STANDARD);
        audioFormat = new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
        try {
            sdline = AudioSystem.getSourceDataLine(audioFormat);
            sdline.open(audioFormat);
            sdline.start();
        } catch (LineUnavailableException ex) {
            System.out.println("Unable to start output. Quitting");
            System.exit(0);
        }
        playMode = PlayModes.PLAY_UNFILTERED;
        triggerOutput = true;
    }

    @Override
    public void run() {
        byte[] readBuffer = new byte[byteBufferSize];
        double[] doubleReadBuffer = new double[samplesPerRead];
        int i = 0;
        try {
            targetDataLine.open(audioFormat);
            targetDataLine.start();
            while (targetDataLine.read(readBuffer, 0, readBuffer.length) == readBuffer.length) {
                ByteBuffer buf = ByteBuffer.wrap(readBuffer);
                buf.order(ByteOrder.BIG_ENDIAN);
                i = 0;

                while (buf.remaining() > 2) {
                    short s = buf.getShort();
                    doubleReadBuffer[i] = (new Short(s)).doubleValue();
                    i++;
                }
                play(buf.array(), transform(doubleReadBuffer));
            }
        } catch (LineUnavailableException | IOException ex) {
            ex.printStackTrace();
        }

    }

    enum PlayModes {
        PLAY_FILTERED, PLAY_UNFILTERED, NO_PLAY
    }

    public void play(byte[] buf, Complex[] inv) throws LineUnavailableException, IOException {
        NewBitClass nbc = new NewBitClass();
        boolean isFound = false;
        for (int i = 0; i < inv.length; i++) {
            short x = new Double(inv[i].getReal()).shortValue();
            nbc.addBytes(BinaryUtils.toByteArray(x));
            isFound = isFound | x != 0;
        }
        switch (playMode) {
            case PLAY_FILTERED:

                sdline.write(nbc.toByteArray(), 0, byteBufferSize);
                break;
            case PLAY_UNFILTERED:
                sdline.write(buf, 0, buf.length);
                break;
        }
        if (isFound) {
            System.out.println("Triggered " + System.currentTimeMillis() + "\t" + cf.toString());
            if (triggerOutput) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Process p = Runtime.getRuntime().exec("sh /home/chip/triggergpio.sh");
                            p.waitFor();
                        } catch (IOException ex) {
                            Logger.getLogger(RecordThread.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(RecordThread.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }).start();
            }
        }
    }

    private Complex[] transform(double[] input) {
        try {
            Complex[] complx = transformer.transform(input, TransformType.FORWARD);
            int idx = 0;
            for (Complex c : complx) {
                double aFreq = freqBinSize * idx;
                for (BandPassFilter bpf : cf.getFilters()) {
                    if (((freqBinSize * (idx + 1) > bpf.getStart() || aFreq >= bpf.getStart()) && aFreq <= bpf.getEnd())) {
                        if (Math.sqrt(c.getReal() * c.getReal()) > bpf.getThreshold()) {
                            complx[idx] = c;
                        } else {
                            complx[idx] = Complex.ZERO;
                        }
                    } else {
                        complx[idx] = Complex.ZERO;
                    }
                }
                idx++;
            }
            Complex[] inv = transformer.transform(complx, TransformType.INVERSE);
            return inv;
        } catch (IllegalArgumentException e) {
            System.out.println(e);
        }
        return null;
    }

}
