package neuvillette.libertas.client.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import neuvillette.libertas.Libertas;
import neuvillette.libertas.client.config.ConfigDocument;
import neuvillette.libertas.client.config.ConfigDocument.Entry;
import neuvillette.libertas.client.config.ConfigDocument.Type;
import neuvillette.libertas.client.config.ConfigFiles;

/** File browser and draft editor for this Minecraft instance's local configuration files. */
@SideOnly(Side.CLIENT)
public class GuiClientConfig extends GuiScreen {

    private static final int LIST_TOP = 80;
    private static final int ROW_HEIGHT = 40;
    private static final int ACCENT = 0xFF94E4D3;
    private static final int TEXT = 0xFFE6E8ED;
    private static final int MUTED = 0xFF929AA8;

    private final GuiScreen parent;
    private final boolean serverConfig;
    private ConfigFiles files;
    private Path directory;
    private ConfigDocument document;
    private final List<Path> directoryEntries = new ArrayList<>();
    private final List<Path> visibleFiles = new ArrayList<>();
    private final List<Entry> visibleOptions = new ArrayList<>();
    private final Map<Entry, GuiTextField> valueFields = new IdentityHashMap<>();
    private GuiTextField pathField;
    private GuiTextField searchField;
    private String search = "";
    private String status = "";
    private boolean error;
    private int scroll;
    private boolean draggingScroll;

    public GuiClientConfig(GuiScreen parent) {
        this(parent, false);
    }

    public GuiClientConfig(GuiScreen parent, boolean serverConfig) {
        this.parent = parent;
        this.serverConfig = serverConfig;
    }

    @Override
    public void initGui() {
        if (serverConfig && mc.theWorld != null) {
            mc.displayGuiScreen(parent);
            return;
        }
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        if (files == null) {
            try {
                files = new ConfigFiles(
                    Loader.instance()
                        .getConfigDir()
                        .toPath());
                directory = files.getRoot();
                reloadDirectory();
            } catch (IOException e) {
                showError(e);
            }
        }
        pathField = new GuiTextField(fontRendererObj, 60, 29, width - 120, 18);
        pathField.setMaxStringLength(4096);
        pathField.setText(files == null ? "config/" : files.display(document == null ? directory : document.getFile()));
        pathField.setEnabled(document == null);
        searchField = new GuiTextField(fontRendererObj, 58, 55, width - 70, 18);
        searchField.setMaxStringLength(256);
        searchField.setText(search);
        buttonList.add(new GuiButton(0, 12, 28, 40, 20, tr("root")));
        buttonList.add(new GuiButton(2, width - 52, 28, 40, 20, tr("go")));
        buttonList.add(new GuiButton(3, width / 2 - 105, height - 26, 100, 20, tr("agree")));
        buttonList.add(new GuiButton(4, width / 2 + 5, height - 26, 100, 20, tr("cancel")));
        valueFields.clear();
        if (document != null) {
            for (Entry entry : document.getEntries()) {
                if (entry.getType() == Type.BOOLEAN) continue;
                GuiTextField field = new GuiTextField(fontRendererObj, valueX(), 0, valueWidth(), 18);
                field.setMaxStringLength(
                    Math.max(
                        65536,
                        entry.getValue()
                            .length() + 1024));
                field.setText(entry.getValue());
                valueFields.put(entry, field);
            }
        }
        filter();
        updateButtons();
    }

    private int listBottom() {
        return height - 57;
    }

    private int valueX() {
        return width / 2 + 10;
    }

    private int valueWidth() {
        return width - valueX() - 30;
    }

    private int rowCount() {
        return document == null ? visibleFiles.size() : visibleOptions.size();
    }

    private boolean hasParentDirectory() {
        return files != null && directory != null && !directory.equals(files.getRoot());
    }

    private int entryCount() {
        return rowCount() - (document == null && hasParentDirectory() ? 1 : 0);
    }

    private int maxScroll() {
        return Math.max(0, rowCount() * ROW_HEIGHT - (listBottom() - LIST_TOP));
    }

    private void filter() {
        String query = search.toLowerCase(Locale.ROOT);
        visibleFiles.clear();
        visibleOptions.clear();
        if (document == null) {
            if (hasParentDirectory()) visibleFiles.add(directory.getParent());
            for (Path file : directoryEntries) {
                if (file.getFileName()
                    .toString()
                    .toLowerCase(Locale.ROOT)
                    .contains(query)) visibleFiles.add(file);
            }
        } else {
            for (Entry entry : document.getEntries()) {
                if ((entry.getName() + " " + entry.getComment()).toLowerCase(Locale.ROOT)
                    .contains(query)) {
                    visibleOptions.add(entry);
                }
            }
        }
        scroll = Math.min(scroll, maxScroll());
    }

    private void reloadDirectory() throws IOException {
        List<Path> children = files.list(directory);
        directoryEntries.clear();
        directoryEntries.addAll(children);
    }

    private void navigate(Path path) {
        try {
            Path checked = files.check(path);
            if (Files.isDirectory(checked)) {
                List<Path> children = files.list(checked);
                directory = checked;
                directoryEntries.clear();
                directoryEntries.addAll(children);
                document = null;
            } else {
                ConfigDocument opened = ConfigDocument.open(files, checked);
                List<Path> children = files.list(checked.getParent());
                document = opened;
                directory = checked.getParent();
                directoryEntries.clear();
                directoryEntries.addAll(children);
            }
            status = "";
            error = false;
            search = "";
            scroll = 0;
            initGui();
        } catch (IOException | RuntimeException e) {
            showError(e);
        }
    }

    private void updateButtons() {
        for (GuiButton button : buttonList) {
            if (button.id < 3) button.enabled = files != null && document == null;
            if (button.id == 3) button.enabled = document == null || document.isValid();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0 -> navigate(files.getRoot());
            case 2 -> goToPath();
            case 3 -> accept();
            case 4 -> cancel();
            default -> {}
        }
    }

    private void goToPath() {
        try {
            navigate(files.resolve(pathField.getText()));
        } catch (IOException | RuntimeException e) {
            showError(e);
        }
    }

    private void accept() {
        if (document == null) {
            mc.displayGuiScreen(parent);
            return;
        }
        try {
            document.save();
            document = null;
            search = "";
            scroll = 0;
            reloadDirectory();
            status = "";
            error = false;
            initGui();
        } catch (IOException | RuntimeException e) {
            showError(e);
        }
    }

    private void cancel() {
        if (document == null) mc.displayGuiScreen(parent);
        else {
            document = null;
            search = "";
            status = "";
            error = false;
            scroll = 0;
            initGui();
        }
    }

    private void showError(Exception exception) {
        String key = "libertas.config.error." + exception.getMessage();
        String translated = I18n.format(key);
        status = translated.equals(key) ? tr("error.io") : translated;
        error = true;
        Libertas.LOG.warn("Could not access configuration", exception);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(8, 77, width - 8, listBottom() + 1, 0xE610141C);
        drawRect(8, 77, width - 8, 78, ACCENT);
        String title = tr(serverConfig ? "server.title" : "title")
            + (document != null && document.hasChanges() ? " *" : "");
        drawCenteredString(fontRendererObj, title, width / 2, 9, TEXT);
        fontRendererObj.drawString(tr("search"), 12, 60, MUTED);
        pathField.drawTextBox();
        searchField.drawTextBox();
        drawRows(mouseX, mouseY);
        String message = status.isEmpty() ? tr("count", entryCount()) : status;
        fontRendererObj.drawString(
            fontRendererObj.trimStringToWidth(message, width - 24),
            12,
            height - 47,
            error ? 0xFFFF9292 : status.isEmpty() ? MUTED : ACCENT);
        updateButtons();
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawOptionTooltip(mouseX, mouseY);
    }

    private void drawRows(int mouseX, int mouseY) {
        int scale = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
            10 * scale,
            (height - listBottom()) * scale,
            (width - 20) * scale,
            Math.max(0, listBottom() - LIST_TOP) * scale);
        try {
            for (int index = scroll / ROW_HEIGHT; index < rowCount(); index++) {
                int y = LIST_TOP + index * ROW_HEIGHT - scroll;
                if (y >= listBottom()) break;
                boolean hovered = mouseX >= 12 && mouseX < width - 22
                    && mouseY >= Math.max(y, LIST_TOP)
                    && mouseY < Math.min(y + ROW_HEIGHT - 2, listBottom());
                drawRect(12, y, width - 22, y + ROW_HEIGHT - 2, hovered ? 0xFF242E3A : 0xFF1A212B);
                if (document == null) drawFile(visibleFiles.get(index), y);
                else drawOption(visibleOptions.get(index), y, mouseX, mouseY);
            }
            if (rowCount() == 0) drawCenteredString(fontRendererObj, tr("empty"), width / 2, LIST_TOP + 20, MUTED);
        } finally {
            GL11.glPopAttrib();
        }
        if (maxScroll() > 0) {
            int viewport = listBottom() - LIST_TOP;
            int thumb = Math.max(16, viewport * viewport / (rowCount() * ROW_HEIGHT));
            int top = LIST_TOP + scroll * (viewport - thumb) / maxScroll();
            drawRect(width - 17, LIST_TOP, width - 13, listBottom(), 0xFF242E3A);
            drawRect(width - 17, top, width - 13, top + thumb, ACCENT);
        }
    }

    private void drawFile(Path file, int y) {
        boolean directory = Files.isDirectory(file);
        boolean parentDirectory = file.equals(this.directory.getParent());
        int color = directory ? 0xFFE6C779 : ConfigFiles.isSupported(file) ? ACCENT : MUTED;
        drawRect(22, y + 12, 35, y + 26, color);
        if (directory) drawRect(22, y + 9, 29, y + 12, color);
        else drawRect(25, y + 15, 32, y + 16, 0xFF1A212B);
        fontRendererObj.drawString(
            fontRendererObj.trimStringToWidth(
                parentDirectory ? "..."
                    : file.getFileName()
                        .toString(),
                width - 82),
            44,
            y + (parentDirectory ? 15 : 7),
            directory || ConfigFiles.isSupported(file) ? TEXT : MUTED);
        if (parentDirectory) return;
        String detail = tr(directory ? "directory" : ConfigFiles.isSupported(file) ? "file" : "unsupported");
        fontRendererObj.drawString(detail, 44, y + 23, MUTED);
    }

    private void drawOption(Entry entry, int y, int mouseX, int mouseY) {
        int labelWidth = valueX() - 30;
        String invalid = entry.hasChanges() ? entry.getError() : null;
        String label = (entry.hasChanges() ? "* " : "") + entry.getName();
        fontRendererObj.drawString(
            fontRendererObj.trimStringToWidth(label, labelWidth),
            20,
            y + 7,
            invalid != null ? 0xFFFF9292 : TEXT);
        String detail = invalid != null ? tr("error." + invalid)
            : entry.getComment()
                .isEmpty()
                    ? tr(
                        "type." + entry.getType()
                            .name())
                    : entry.getComment()
                        .split("\n")[0];
        fontRendererObj.drawString(fontRendererObj.trimStringToWidth(detail, labelWidth), 20, y + 23, MUTED);
        if (entry.getType() == Type.BOOLEAN) {
            GuiButton toggle = new GuiButton(
                -1,
                valueX(),
                y + 10,
                valueWidth(),
                20,
                I18n.format(Boolean.parseBoolean(entry.getValue()) ? "options.on" : "options.off"));
            toggle.drawButton(mc, mouseX, mouseY);
        } else {
            GuiTextField field = valueFields.get(entry);
            field.yPosition = y + 10;
            field.setTextColor(invalid == null ? 0xE6E8ED : 0xFF9292);
            field.drawTextBox();
        }
    }

    private void drawOptionTooltip(int mouseX, int mouseY) {
        if (document == null || mouseY < LIST_TOP || mouseY >= listBottom() || mouseX < 12 || mouseX >= valueX() - 6)
            return;
        int index = (mouseY - LIST_TOP + scroll) / ROW_HEIGHT;
        if (index >= visibleOptions.size()) return;
        Entry entry = visibleOptions.get(index);
        List<String> tooltip = new ArrayList<>();
        tooltip.addAll(fontRendererObj.listFormattedStringToWidth(entry.getName(), Math.min(300, width - 40)));
        String text = entry.getComment();
        if (entry.getType() == Type.ARRAY) text += "\n" + tr("arrayHint");
        if (entry.hasChanges() && entry.getError() != null) text += "\n" + tr("error." + entry.getError());
        for (String line : text.split("\n")) {
            if (!line.isEmpty())
                tooltip.addAll(fontRendererObj.listFormattedStringToWidth("\u00a77" + line, Math.min(300, width - 40)));
        }
        int maxLines = Math.max(2, (height - 32) / 10);
        if (tooltip.size() > maxLines) {
            tooltip = new ArrayList<>(tooltip.subList(0, maxLines - 1));
            tooltip.add("...");
        }
        drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        pathField.mouseClicked(mouseX, mouseY, button);
        searchField.mouseClicked(mouseX, mouseY, button);
        for (GuiTextField field : valueFields.values()) field.setFocused(false);
        if (mouseY >= LIST_TOP && mouseY < listBottom() && mouseX >= 12 && mouseX < width - 12) {
            if (mouseX >= width - 22 && button == 0) {
                draggingScroll = true;
                dragScroll(mouseY);
                return;
            }
            int index = (mouseY - LIST_TOP + scroll) / ROW_HEIGHT;
            if (index < rowCount()) {
                if (document == null && button == 0) {
                    navigate(visibleFiles.get(index));
                    return;
                }
                if (document != null) {
                    Entry entry = visibleOptions.get(index);
                    if (mouseX >= valueX()) {
                        if (entry.getType() == Type.BOOLEAN && button == 0) {
                            entry.setValue(Boolean.toString(!Boolean.parseBoolean(entry.getValue())));
                        } else if (valueFields.containsKey(entry)) {
                            GuiTextField field = valueFields.get(entry);
                            field.yPosition = LIST_TOP + index * ROW_HEIGHT - scroll + 10;
                            field.mouseClicked(mouseX, mouseY, button);
                        }
                        status = "";
                        error = false;
                    }
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int delta = Mouse.getEventDWheel();
        if (delta != 0 && mouseY >= LIST_TOP && mouseY < listBottom()) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - Integer.signum(delta) * ROW_HEIGHT));
        }
    }

    private void dragScroll(int mouseY) {
        scroll = Math
            .max(0, Math.min(maxScroll(), (mouseY - LIST_TOP) * maxScroll() / Math.max(1, listBottom() - LIST_TOP)));
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long elapsed) {
        if (draggingScroll) dragScroll(mouseY);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        draggingScroll = false;
        super.mouseMovedOrUp(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            cancel();
            return;
        }
        if (pathField.isFocused() && document == null) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) goToPath();
            else pathField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        if (searchField.isFocused()) {
            if (searchField.textboxKeyTyped(typedChar, keyCode)) {
                search = searchField.getText();
                scroll = 0;
                filter();
            }
            return;
        }
        for (Map.Entry<Entry, GuiTextField> editor : valueFields.entrySet()) {
            if (editor.getValue()
                .isFocused()
                && editor.getValue()
                    .textboxKeyTyped(typedChar, keyCode)) {
                editor.getKey()
                    .setValue(
                        editor.getValue()
                            .getText());
                status = "";
                error = false;
                updateButtons();
                return;
            }
        }
    }

    @Override
    public void updateScreen() {
        pathField.updateCursorCounter();
        searchField.updateCursorCounter();
        for (GuiTextField field : valueFields.values()) {
            if (field.isFocused()) field.updateCursorCounter();
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private static String tr(String key, Object... arguments) {
        return I18n.format("libertas.config." + key, arguments);
    }
}
