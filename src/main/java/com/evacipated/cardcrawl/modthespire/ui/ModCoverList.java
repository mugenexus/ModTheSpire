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
    private final DefaultListModel<ModPanel> withoutCover = new DefaultListModel<>();
    private final JList<ModPanel> rows = new JList<>(withoutCover);
    private final JPanel view = new JPanel(new BorderLayout(0, ThemeManager.scale(6)));
    private final JScrollPane rowScroll = new JScrollPane(rows);
    private final JScrollPane gridScroll = new JScrollPane(this);
    private boolean refreshPending;
    private boolean rebuilding;
    private final Map<String, ImageIcon> thumbnails = new HashMap<>();
    private final ExecutorService downloads = Executors.newFixedThreadPool(3, task -> {
        Thread thread = new Thread(task, "模组封面");
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
        setFixedCellHeight(ThemeManager.scale(138));
        getAccessibleContext().setAccessibleName("模组封面");

        setBackground(ThemeManager.BG_PRIMARY);
        JPanel tile = new JPanel(new BorderLayout(0, ThemeManager.scale(4)));
        tile.setOpaque(false);
        JPanel card = new JPanel(new BorderLayout(0, ThemeManager.scale(5)));
        tile.setBorder(BorderFactory.createEmptyBorder(ThemeManager.scale(3), ThemeManager.scale(3),
            ThemeManager.scale(3), ThemeManager.scale(3)));
        tile.add(card);
        JLabel image = new JLabel("", SwingConstants.CENTER);
        image.setPreferredSize(new Dimension(0, ThemeManager.scale(81)));
        JPanel caption = new JPanel(new BorderLayout(ThemeManager.scale(3), 0));
        caption.setOpaque(false);
        JCheckBox enabled = new JCheckBox();
        enabled.setOpaque(false);
        enabled.setBorder(BorderFactory.createEmptyBorder());
        JTextArea title = new JTextArea(2, 1);
        title.setOpaque(false);
        title.setLineWrap(true);
        title.setWrapStyleWord(true);
        title.setFont(getFont().deriveFont(Font.BOLD));
        title.setMargin(new Insets(0, 0, 0, 0));
        caption.add(enabled, BorderLayout.WEST);
        caption.add(title, BorderLayout.CENTER);
        card.add(image, BorderLayout.CENTER);
        card.add(caption, BorderLayout.SOUTH);
        setCellRenderer((grid, mod, index, selected, focused) -> {
            Color border = selected ? ThemeManager.ACCENT_COLOR : ThemeManager.BORDER_DEFAULT;
            int padding = ThemeManager.scale(5);
            card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(border, 1, true),
                BorderFactory.createEmptyBorder(padding, padding, padding, padding)));
            card.setBackground(selected ? ThemeManager.BG_SELECTED : ThemeManager.BG_SECONDARY);
            enabled.setSelected(mod.isSelected());
            enabled.setEnabled(mod.checkBox.isEnabled());
            title.setText(mod.info.Name);
            title.setForeground(mod.checkBox.isEnabled() ? ThemeManager.TEXT_PRIMARY : ThemeManager.STATUS_ERROR);
            Icon icon = cover(mod);
            image.setIcon(icon);
            return tile;
        });
        rows.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rows.setCellRenderer(list.getCellRenderer());
        rows.getAccessibleContext().setAccessibleName("无封面模组");
        rowScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ThemeManager.BORDER_DEFAULT), "无封面模组"));
        gridScroll.setBorder(BorderFactory.createLineBorder(ThemeManager.BORDER_DEFAULT));
        gridScroll.getVerticalScrollBar().setUnitIncrement(ThemeManager.scale(24));
        view.add(gridScroll, BorderLayout.CENTER);
        attachInteractions(this, list);
        attachInteractions(rows, list);
        source.addListDataListener(new ListDataListener() {
            @Override public void intervalAdded(ListDataEvent event) { refresh(); }
            @Override public void intervalRemoved(ListDataEvent event) { refresh(); }
            @Override public void contentsChanged(ListDataEvent event) { refresh(); }
        });
    }

    JPanel view()
    {
        return view;
    }

    void selectMod(ModPanel mod)
    {
        setSelectedValue(mod, true);
        rows.setSelectedValue(mod, true);
    }

    private void attachInteractions(JList<ModPanel> section, JModPanelCheckBoxList list)
    {
        section.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !rebuilding && section.isShowing() && section.getSelectedValue() != null) {
                (section == rows ? this : rows).clearSelection();
                list.setSelectedValue(section.getSelectedValue(), false);
            }
        });
        section.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggle");
        section.getActionMap().put("toggle", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                toggle(section.getSelectedValue());
            }
        });
        section.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "cover");
        section.getActionMap().put("cover", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (section.getSelectedValue() != null) chooseCover(section.getSelectedValue());
            }
        });
        for (int direction : new int[]{-1, 1}) {
            String action = direction < 0 ? "moveEarlier" : "moveLater";
            int key = direction < 0 ? KeyEvent.VK_UP : KeyEvent.VK_DOWN;
            section.getInputMap().put(KeyStroke.getKeyStroke(key, InputEvent.ALT_DOWN_MASK), action);
            section.getActionMap().put(action, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    int target = section.getSelectedIndex() + direction;
                    if (section.getSelectedValue() != null && target >= 0 && target < section.getModel().getSize()) {
                        move(section.getSelectedValue(), section.getModel().getElementAt(target), list);
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
                    dragged = at(section, event.getPoint());
                    start = event.getPoint();
                    moved = false;
                    if (dragged != null) section.setSelectedValue(dragged, false);
                }
            }

            @Override public void mouseDragged(MouseEvent event) {
                if (dragged != null && start.distance(event.getPoint()) >= java.awt.dnd.DragSource.getDragThreshold()) {
                    moved = true;
                    section.scrollRectToVisible(new Rectangle(event.getPoint()));
                }
            }

            @Override public void mouseReleased(MouseEvent event) {
                if (SwingUtilities.isRightMouseButton(event) && dragged != null) {
                    ModPanel target = at(section, event.getPoint());
                    if (!moved) {
                        chooseCover(dragged);
                    } else if (target != null && target != dragged) {
                        move(dragged, target, list);
                    }
                    dragged = null;
                }
            }

            @Override public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) toggle(at(section, event.getPoint()));
            }
        };
        section.addMouseListener(mouse);
        section.addMouseMotionListener(mouse);
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
        SwingUtilities.invokeLater(() -> selectMod(mod));
        list.publishBoxChecked();
    }

    private void rebuild()
    {
        ModPanel selection = getSelectedValue() == null ? rows.getSelectedValue() : getSelectedValue();
        rebuilding = true;
        try {
            visible.clear();
            withoutCover.clear();
            for (int i = 0; i < source.getSize(); i++) {
                ModPanel mod = source.get(i);
                if (!mod.isFilteredOut()) {
                    (cover(mod) == null ? withoutCover : visible).addElement(mod);
                }
            }
            view.removeAll();
            if (!withoutCover.isEmpty()) {
                int rowHeight = rows.getCellBounds(0, 0).height;
                int height = rowHeight * Math.min(4, withoutCover.size()) + rowScroll.getInsets().top + rowScroll.getInsets().bottom;
                rowScroll.setPreferredSize(new Dimension(0, height));
                view.add(rowScroll, visible.isEmpty() ? BorderLayout.CENTER : BorderLayout.NORTH);
            }
            if (!visible.isEmpty() || withoutCover.isEmpty()) view.add(gridScroll, BorderLayout.CENTER);
            selectMod(selection);
        } finally {
            rebuilding = false;
        }
        view.revalidate();
        view.repaint();
    }

    @Override public String getToolTipText(MouseEvent event)
    {
        ModPanel mod = at(this, event.getPoint());
        return mod == null ? null : "模组：" + mod.info.Name + " — " + (mod.isSelected() ? "已启用" : "未启用");
    }

    private static ModPanel at(JList<ModPanel> section, Point point)
    {
        int index = section.locationToIndex(point);
        return index >= 0 && section.getCellBounds(index, index).contains(point) ? section.getModel().getElementAt(index) : null;
    }

    private void toggle(ModPanel mod)
    {
        if (mod != null) {
            mod.setSelected(!mod.isSelected());
            view.repaint();
        }
    }

    private String coverKey(ModPanel mod)
    {
        return "cover." + (mod.info.ID == null || mod.info.ID.isEmpty()
            ? mod.modFile.getAbsolutePath() : mod.info.ID);
    }

    private void chooseCover(ModPanel mod)
    {
        Object[] options = {"选择图片", "重置封面", "取消"};
        int action = JOptionPane.showOptionDialog(this, "模组：" + mod.info.Name, "模组封面", JOptionPane.DEFAULT_OPTION,
            JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        String key = coverKey(mod);
        String previous = Loader.MTS_CONFIG.getString(key);
        if (action == 0) {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("图片", "png", "jpg", "jpeg", "gif", "bmp"));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File file = chooser.getSelectedFile();
            try {
                ImageIcon icon = thumbnail(file);
                if (icon == null) throw new IOException("图片格式不受支持，或文件超出大小限制。");
                thumbnails.put("file:" + file.getAbsolutePath(), icon);
                Loader.MTS_CONFIG.setString(key, file.getAbsolutePath());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "无法打开封面", JOptionPane.ERROR_MESSAGE);
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
            JOptionPane.showMessageDialog(this, e.getMessage(), "无法保存封面", JOptionPane.ERROR_MESSAGE);
        }
        refresh();
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
        if (source == null) return null;
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
                    if (result != null) refresh();
                });
            });
        }
        return thumbnails.get(source);
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
                    (double) ThemeManager.scale(78) / image.getHeight());
                return new ImageIcon(image.getScaledInstance(Math.max(1, (int)(image.getWidth() * scale)),
                    Math.max(1, (int)(image.getHeight() * scale)), Image.SCALE_SMOOTH));
            } finally {
                reader.dispose();
            }
        }
    }
}
