package com.evacipated.cardcrawl.modthespire.ui;

import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.steam.SteamSearch;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.net.ssl.HttpsURLConnection;
import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ModCoverList extends JList<ModPanel>
{
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private final DefaultListModel<ModPanel> source;
    private final DefaultListModel<ModPanel> visible = new DefaultListModel<>();
    private boolean refreshPending;
    private final Map<String, ImageIcon> thumbnails = new HashMap<>();
    private final ExecutorService downloads = Executors.newFixedThreadPool(3, task -> {
        Thread thread = new Thread(task, "Mod cover");
        thread.setDaemon(true);
        return thread;
    });

    ModCoverList(DefaultListModel<ModPanel> source, JModPanelCheckBoxList list)
    {
        this.source = source;
        setModel(visible);
        setLayoutOrientation(HORIZONTAL_WRAP);
        setVisibleRowCount(0);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setFixedCellWidth(ThemeManager.scale(122));
        setFixedCellHeight(ThemeManager.scale(126));
        getAccessibleContext().setAccessibleName("Mod covers");

        JLabel tile = new JLabel();
        tile.setOpaque(true);
        tile.setHorizontalAlignment(SwingConstants.CENTER);
        tile.setHorizontalTextPosition(SwingConstants.CENTER);
        tile.setVerticalTextPosition(SwingConstants.BOTTOM);
        tile.setIconTextGap(ThemeManager.scale(5));
        setCellRenderer((grid, mod, index, selected, focused) -> {
            String name = mod.info.Name;
            int maxWidth = getFixedCellWidth() - ThemeManager.scale(12);
            FontMetrics metrics = tile.getFontMetrics(tile.getFont());
            String label = name;
            while (!label.isEmpty() && metrics.stringWidth(label + "\u2026") > maxWidth) {
                label = label.substring(0, label.offsetByCodePoints(label.length(), -1));
            }
            tile.setText((mod.isSelected() ? "\u2611 " : "\u2610 ") + label + (label.equals(name) ? "" : "\u2026"));
            tile.putClientProperty("html.disable", true);
            tile.setForeground(selected ? ThemeManager.TEXT_PRIMARY : mod.checkBox.isEnabled()
                ? ThemeManager.TEXT_SECONDARY : ThemeManager.STATUS_ERROR);
            tile.setBackground(selected ? ThemeManager.BG_SELECTED : ThemeManager.BG_SECONDARY);
            tile.setBorder(BorderFactory.createLineBorder(ThemeManager.BORDER_DEFAULT));
            tile.setIcon(cover(mod));
            return tile;
        });
        setToolTipText("");
        addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && isShowing()) {
                list.setSelectedValue(getSelectedValue(), false);
            }
        });
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggle");
        getActionMap().put("toggle", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                toggle(getSelectedValue());
            }
        });
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "cover");
        getActionMap().put("cover", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (getSelectedValue() != null) chooseCover(getSelectedValue());
            }
        });
        for (int direction : new int[]{-1, 1}) {
            String action = direction < 0 ? "moveEarlier" : "moveLater";
            int key = direction < 0 ? KeyEvent.VK_UP : KeyEvent.VK_DOWN;
            getInputMap().put(KeyStroke.getKeyStroke(key, InputEvent.ALT_DOWN_MASK), action);
            getActionMap().put(action, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    int target = getSelectedIndex() + direction;
                    if (getSelectedValue() != null && target >= 0 && target < visible.size()) {
                        move(getSelectedValue(), visible.get(target), list);
                    }
                }
            });
        }
        MouseAdapter mouse = new MouseAdapter() {
            private ModPanel dragged;
            private Point start;
            private boolean moved;

            @Override public void mousePressed(MouseEvent event) {
                if (SwingUtilities.isRightMouseButton(event)) {
                    dragged = at(event.getPoint());
                    start = event.getPoint();
                    moved = false;
                    if (dragged != null) setSelectedValue(dragged, false);
                }
            }

            @Override public void mouseDragged(MouseEvent event) {
                if (dragged != null && start.distance(event.getPoint()) >= java.awt.dnd.DragSource.getDragThreshold()) {
                    moved = true;
                    scrollRectToVisible(new Rectangle(event.getPoint()));
                }
            }

            @Override public void mouseReleased(MouseEvent event) {
                if (SwingUtilities.isRightMouseButton(event) && dragged != null) {
                    ModPanel target = at(event.getPoint());
                    if (!moved) {
                        chooseCover(dragged);
                    } else if (target != null && target != dragged) {
                        move(dragged, target, list);
                    }
                    dragged = null;
                }
            }

            @Override public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) toggle(at(event.getPoint()));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        source.addListDataListener(new ListDataListener() {
            @Override public void intervalAdded(ListDataEvent event) { refresh(); }
            @Override public void intervalRemoved(ListDataEvent event) { refresh(); }
            @Override public void contentsChanged(ListDataEvent event) { refresh(); }
        });
    }

    void refresh()
    {
        if (refreshPending) return;
        refreshPending = true;
        SwingUtilities.invokeLater(() -> {
            refreshPending = false;
            rebuild();
        });
    }

    private void move(ModPanel mod, ModPanel target, JModPanelCheckBoxList list)
    {
        int to = source.indexOf(target);
        source.removeElement(mod);
        source.add(to, mod);
        SwingUtilities.invokeLater(() -> setSelectedValue(mod, true));
        list.publishBoxChecked();
    }

    private void rebuild()
    {
        ModPanel selection = getSelectedValue();
        visible.clear();
        for (int i = 0; i < source.getSize(); i++) {
            ModPanel mod = source.get(i);
            if (!mod.isFilteredOut()) visible.addElement(mod);
        }
        setSelectedValue(selection, false);
    }

    @Override public String getToolTipText(MouseEvent event)
    {
        ModPanel mod = at(event.getPoint());
        return mod == null ? null : "Mod: " + mod.info.Name + " — " + (mod.isSelected() ? "Enabled" : "Disabled");
    }

    private ModPanel at(Point point)
    {
        int index = locationToIndex(point);
        return index >= 0 && getCellBounds(index, index).contains(point) ? visible.get(index) : null;
    }

    private void toggle(ModPanel mod)
    {
        if (mod != null) {
            mod.setSelected(!mod.isSelected());
            repaint();
        }
    }

    private String coverKey(ModPanel mod)
    {
        return "cover." + (mod.info.ID == null || mod.info.ID.isEmpty()
            ? mod.modFile.getAbsolutePath() : mod.info.ID);
    }

    private void chooseCover(ModPanel mod)
    {
        Object[] options = {"Choose image…", "Reset cover", "Cancel"};
        int action = JOptionPane.showOptionDialog(this, "Mod: " + mod.info.Name, "Mod cover", JOptionPane.DEFAULT_OPTION,
            JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        String key = coverKey(mod);
        String previous = Loader.MTS_CONFIG.getString(key);
        if (action == 0) {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "gif", "bmp"));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File file = chooser.getSelectedFile();
            try {
                ImageIcon icon = thumbnail(file);
                if (icon == null) throw new IOException("Unsupported image or image exceeds the size limit.");
                thumbnails.put("file:" + file.getAbsolutePath(), icon);
                Loader.MTS_CONFIG.setString(key, file.getAbsolutePath());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "Could not open cover", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else if (action == 1) {
            Loader.MTS_CONFIG.setString(key, "");
        } else {
            return;
        }
        try {
            Loader.MTS_CONFIG.save();
        } catch (IOException e) {
            Loader.MTS_CONFIG.setString(key, previous == null ? "" : previous);
            JOptionPane.showMessageDialog(this, e.getMessage(), "Could not save cover", JOptionPane.ERROR_MESSAGE);
        }
        repaint();
    }

    private Icon cover(ModPanel mod)
    {
        String custom = Loader.MTS_CONFIG.getString(coverKey(mod));
        File file = custom == null || custom.isEmpty() ? null : new File(custom);
        String source = file != null && file.isFile() ? "file:" + custom : null;
        if (source == null && mod.info.isWorkshop && Loader.getWorkshopInfos() != null) {
            Path jar = mod.modFile.toPath().toAbsolutePath().normalize();
            for (SteamSearch.WorkshopInfo item : Loader.getWorkshopInfos()) {
                if (item.previewUrl != null && !item.previewUrl.isEmpty()
                    && jar.startsWith(new File(item.getInstallPath()).toPath().toAbsolutePath().normalize())) {
                    source = item.previewUrl;
                    break;
                }
            }
        }
        if (source == null) return ModSelectWindow.ICON_WORKSHOP;
        if (!thumbnails.containsKey(source)) {
            String location = source;
            thumbnails.put(location, null);
            downloads.execute(() -> {
                ImageIcon image = null;
                try {
                    image = location.startsWith("file:") ? thumbnail(new File(location.substring(5)))
                        : download(location);
                } catch (IOException ignored) {
                    // Covers are optional. Keep the placeholder when a source becomes unavailable.
                }
                ImageIcon result = image;
                SwingUtilities.invokeLater(() -> {
                    if (thumbnails.get(location) == null) thumbnails.put(location, result);
                    repaint();
                });
            });
        }
        ImageIcon icon = thumbnails.get(source);
        return icon == null ? ModSelectWindow.ICON_WORKSHOP : icon;
    }

    private ImageIcon download(String location) throws IOException
    {
        URL url = new URL(location);
        String host = url.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equals(url.getProtocol()) || url.getUserInfo() != null || url.getPort() != -1
            || !(host.endsWith(".steamusercontent.com") || host.endsWith(".steamstatic.com"))) return null;
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(4000);
        try {
            if (connection.getResponseCode() != 200 || connection.getContentLengthLong() > MAX_IMAGE_BYTES) return null;
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > MAX_IMAGE_BYTES) return null;
                    bytes.write(buffer, 0, count);
                }
                return thumbnail(new ByteArrayInputStream(bytes.toByteArray()));
            }
        } finally {
            connection.disconnect();
        }
    }

    private ImageIcon thumbnail(Object input) throws IOException
    {
        if (input instanceof File && ((File) input).length() > MAX_IMAGE_BYTES) return null;
        try (ImageInputStream stream = ImageIO.createImageInputStream(input)) {
            if (stream == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 8192 || height > 8192) return null;
                javax.imageio.ImageReadParam params = reader.getDefaultReadParam();
                int step = Math.max(1, Math.max(width, height) / 256);
                params.setSourceSubsampling(step, step, 0, 0);
                BufferedImage image = reader.read(0, params);
                double scale = Math.min((double) ThemeManager.scale(108) / image.getWidth(),
                    (double) ThemeManager.scale(88) / image.getHeight());
                return new ImageIcon(image.getScaledInstance(Math.max(1, (int)(image.getWidth() * scale)),
                    Math.max(1, (int)(image.getHeight() * scale)), Image.SCALE_SMOOTH));
            } finally {
                reader.dispose();
            }
        }
    }
}
