package com.darkmidnight.audioworkbench;

import com.darkmidnight.eden.JacksonConverter;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/**
 * Provides a thread to read input from the command line.
 * @author anthony
 */
public class UserInputThread extends Thread {

    private RecordThread rt;
    private String filterFile = "";

    public static void main(String[] args) {
        UserInputThread uit = new UserInputThread();
        uit.start();
    }

    public UserInputThread() {
        try {
            filterFile = "/home/chip/filter.json"; // Default load file.
            rt = new RecordThread(listLineDevices().get(0));
            rt.start();
            reload();
        } catch (LineUnavailableException | IOException ex) {
            Logger.getLogger(UserInputThread.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void run() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Starting input loop");
        while (true) {
            try {
                String aLine = reader.readLine();
                String[] bits = aLine.split("=");
                switch (bits[0]) {
                    case "load":
                        filterFile = bits[1];
                        reload();
                        break;
                    case "reload":
                        reload();
                    case "exit":
                        System.exit(0);
                        break;
                    case "playmode":
                        try {
                            rt.setPlayMode(RecordThread.PlayModes.valueOf(bits[1].toUpperCase()));
                            System.out.println("Play Mode Set");
                        } catch (IllegalArgumentException ex) {
                            System.out.println("Couldn't set play modes.");
                        }
                    case "trigger":
                        rt.setTriggerOutput(Boolean.parseBoolean(bits[1]));
                    default:
                        break;
                }
            } catch (IOException | NumberFormatException ex) {
                Logger.getLogger(UserInputThread.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void reload() throws IOException {
        String json = Files.readAllLines(new File(filterFile).toPath()).stream().collect(Collectors.joining("\n"));
        CombinationFilter cf = JacksonConverter.fromJSON(json, CombinationFilter.class);
        rt.setCf(cf);
    }

    public List<TargetDataLine> listLineDevices() throws LineUnavailableException {
        List<TargetDataLine> lineList = new ArrayList<>();
        Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();
        for (int i = 0; i < mixerInfo.length; i++) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo[i]);
            Line.Info[] targetLineInfo = mixer.getTargetLineInfo();
            if (targetLineInfo.length > 0) {
                if (mixer.getLine(targetLineInfo[0]) instanceof TargetDataLine) {

                    lineList.add((TargetDataLine) mixer.getLine(targetLineInfo[0]));
                }
            }
        }
        return lineList;
    }
}
