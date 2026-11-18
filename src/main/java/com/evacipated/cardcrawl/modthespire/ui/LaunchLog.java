package com.evacipated.cardcrawl.modthespire.ui;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LaunchLog
{
    private static final Pattern ERROR = Pattern.compile(
        "(?m)^(?:Caused by: .*|ERROR: .*|Exception in thread .*|(?:[\\w$]+\\.)*[\\w$]*(?:Exception|Error)(?::.*)?)$");

    static JPanel actions(JTextArea log)
    {
        JButton analyze = new JButton("Analyze error");
        analyze.addActionListener(event -> {
            JTextArea summary = new JTextArea(summarize(log.getText()), 8, 52);
            summary.setEditable(false);
            summary.setLineWrap(true);
            summary.setWrapStyleWord(true);
            JOptionPane.showMessageDialog(log, new JScrollPane(summary), "Launch log analysis",
                JOptionPane.INFORMATION_MESSAGE);
        });
        JButton save = new JButton("Save log…");
        save.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("ModTheSpire.log"));
            if (chooser.showSaveDialog(log) != JFileChooser.APPROVE_OPTION) return;
            File file = chooser.getSelectedFile();
            if (file.exists() && JOptionPane.showConfirmDialog(log, "Overwrite " + file.getName() + "?",
                "Save log", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            try {
                Files.write(file.toPath(), log.getText().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                JOptionPane.showMessageDialog(log, e.getMessage(), "Could not save log", JOptionPane.ERROR_MESSAGE);
            }
        });
        JPanel actions = new JPanel();
        actions.add(analyze);
        actions.add(save);
        return actions;
    }

    static String summarize(String log)
    {
        Matcher matches = ERROR.matcher(log);
        String lastError = null;
        String rootCause = null;
        while (matches.find()) {
            String line = matches.group();
            if (line.startsWith("Caused by: ")) {
                rootCause = line;
            } else {
                lastError = line;
                rootCause = null;
            }
        }
        if (rootCause != null) return rootCause;
        if (lastError != null) return lastError;
        return "No exception or explicit error was found in the captured log.";
    }
}
