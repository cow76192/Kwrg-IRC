package me.cookie.kwrg.module.modules.functions;

import me.cookie.kwrg.gui.component.PanelItemComponent;
import me.cookie.kwrg.gui.component.TertiaryPanelComponent;
import me.cookie.kwrg.gui.component.pane.SliderPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInfoPaneComponent;
import me.cookie.kwrg.gui.component.pane.TextInputPaneComponent;
import me.cookie.kwrg.gui.notification.NotificationType;
import me.cookie.kwrg.irc.config.ModuleConfig;
import me.cookie.kwrg.module.Category;
import me.cookie.kwrg.module.Module;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.window.capture.WindowCapture;
import me.cookie.kwrg.window.render.WorldWindow;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.ArrayList;

public final class WindowModule extends Module {
    public static final ModuleConfig<Settings> CONFIG = new ModuleConfig<>(Settings.class, Settings::sanitize);

    public static final class Settings {
        public Options window = new Options();

        public void sanitize() {
            if (window == null) window = new Options();
            window.sanitize();
        }
    }

    public static final class Options {
        public String process = "", title = "";
        public boolean followCamera, throughWalls, curved, gameInput;
        public float width = 4, distance = 4, offsetX, offsetY;

        public void sanitize() {
            if (process == null) process = "";
            if (title == null) title = "";
            width = Float.isFinite(width) ? Math.clamp(width, 0.5f, 16f) : 4;
            distance = Float.isFinite(distance) ? Math.clamp(distance, 0.5f, 32f) : 4;
            offsetX = Float.isFinite(offsetX) ? Math.clamp(offsetX, -10f, 10f) : 0;
            offsetY = Float.isFinite(offsetY) ? Math.clamp(offsetY, -10f, 10f) : 0;
        }
    }

    private WindowCapture.Window selected;
    private List<WindowCapture.Window> windows = List.of();
    private boolean pickerOpen, pickerClosing, loading;
    private final List<PanelItemComponent> pickerItems = new ArrayList<>();
    private String listError = "";
    private int request;

    public WindowModule() {
        super("window", Category.FUNCTIONS, PanelItemComponent.ItemType.TRIGGER, WorldWindow::exists, CONFIG);
    }

    @Override
    public void onExecute() {
    }

    @Override
    protected void onConfigLoaded() {
        if (CONFIG.get().window == null) CONFIG.get().window = new Options();
        CONFIG.get().window.sanitize();
        selected = null;
        request++;
        loading = false;
        pickerClosing = false;
        pickerItems.clear();
        WorldWindow.destroy();
    }

    @Override
    public void buildTertiaryUI(TertiaryPanelComponent panel) {
        pickerOpen = false;
        pickerClosing = false;
        refresh(panel);
    }

    private void refresh(TertiaryPanelComponent panel) {
        if (!WindowCapture.supported()) {
            listError = "窗口投影仅支持 Windows x64";
            render(panel);
            return;
        }

        loading = true;
        listError = "";
        int current = ++request;
        if (!pickerOpen) render(panel);

        WindowCapture.windows().whenComplete((result, failure) -> Minecraft.getInstance().execute(() -> {
            if (current != request) return;

            loading = false;
            if (failure != null) {
                listError = "获取窗口失败：" + failure.getCause().getMessage();
                windows = List.of();
            } else {
                windows = result;
                Options options = CONFIG.get().window;
                if (selected != null && windows.stream().noneMatch(w -> w.handle() == selected.handle() && w.pid() == selected.pid()))
                    selected = null;
                if (selected == null) {
                    List<WindowCapture.Window> matches = windows.stream()
                            .filter(w -> w.process().equals(options.process) && w.title().equals(options.title)).toList();
                    if (matches.size() == 1) selected = matches.getFirst();
                }
            }
            render(panel);
        }));
    }

    private void closePicker(TertiaryPanelComponent panel) {
        pickerOpen = false;
        loading = false;
        int current = ++request;
        if (pickerItems.isEmpty()) {
            render(panel);
            return;
        }

        pickerClosing = true;
        int[] remaining = {pickerItems.size()};
        for (PanelItemComponent item : List.copyOf(pickerItems)) {
            item.dismiss(() -> {
                if (current == request && --remaining[0] == 0) render(panel);
            });
        }
    }

    private void render(TertiaryPanelComponent panel) {
        pickerClosing = false;
        pickerItems.clear();
        panel.clearChildren();
        Options options = CONFIG.get().window;
        String label = selected == null ? "点击以选择窗口" : selected.title();
        TextInputPaneComponent pickerPane = new TextInputPaneComponent(label, null).asButton(label, () -> {
            if (pickerClosing) return;
            if (pickerOpen) closePicker(panel);
            else {
                pickerOpen = true;
                refresh(panel);
            }
        });
        panel.child(pickerPane);

        if (pickerOpen) {
            if (loading) pickerItems.add(new PanelItemComponent("正在获取窗口…").withSpawnAnimation());
            else if (windows.isEmpty()) pickerItems.add(new PanelItemComponent("暂无可用窗口").withSpawnAnimation());
            for (WindowCapture.Window window : windows) {
                pickerItems.add(new PanelItemComponent(window.label(), PanelItemComponent.ItemType.TRIGGER, () -> {
                    selected = window;
                    CONFIG.update(s -> {
                        s.window.process = window.process();
                        s.window.title = window.title();
                    });

                    pickerPane.setText(window.title());
                    closePicker(panel);
                }, null, () -> selected != null && selected.handle() == window.handle()).withSpawnAnimation());
            }
            for (PanelItemComponent item : pickerItems) panel.child(item);
        }

        if (!listError.isEmpty()) panel.child(TextInfoPaneComponent.header(listError));

        if (selected == null) {
            if (!options.title.isEmpty())
                panel.child(TextInfoPaneComponent.header("上次选择：" + options.title + "（需重新匹配）"));
            return;
        }

        panel.child(new PanelItemComponent("跟随摄像机", PanelItemComponent.ItemType.TOGGLE,
                () -> CONFIG.update(s -> s.window.followCamera = !s.window.followCamera),
                null, () -> CONFIG.get().window.followCamera));

        panel.child(SliderPaneComponent.ofFloat("大小（宽度/方块）", 0.5f, 16, 4, 0.1f,
                () -> CONFIG.get().window.width, v -> CONFIG.get().window.width = v, v -> String.format("%.1f", v)));

        panel.child(SliderPaneComponent.ofFloat("距离（方块）", 0.5f, 32, 4, 0.1f,
                () -> CONFIG.get().window.distance, v -> CONFIG.get().window.distance = v, v -> String.format("%.1f", v)));

        panel.child(SliderPaneComponent.ofFloat("左右偏移（右为正）", -10, 10, 0, 0.1f,
                () -> CONFIG.get().window.offsetX, v -> CONFIG.get().window.offsetX = v, v -> String.format("%.1f", v)));

        panel.child(SliderPaneComponent.ofFloat("上下偏移（上为正）", -10, 10, 0, 0.1f,
                () -> CONFIG.get().window.offsetY, v -> CONFIG.get().window.offsetY = v, v -> String.format("%.1f", v)));

        panel.child(new PanelItemComponent("隔墙显示", PanelItemComponent.ItemType.TOGGLE,
                () -> CONFIG.update(s -> s.window.throughWalls = !s.window.throughWalls),
                null, () -> CONFIG.get().window.throughWalls));

        panel.child(new PanelItemComponent("曲面屏", PanelItemComponent.ItemType.TOGGLE,
                () -> CONFIG.update(s -> s.window.curved = !s.window.curved),
                null, () -> CONFIG.get().window.curved));

        panel.child(new PanelItemComponent("游戏控制（原生输入）", PanelItemComponent.ItemType.TOGGLE,
                () -> CONFIG.update(s -> s.window.gameInput = !s.window.gameInput),
                null, () -> CONFIG.get().window.gameInput));
        panel.child(new TextInfoPaneComponent("输入方式", () -> CONFIG.get().window.gameInput
                ? "目标游戏获得焦点 · 无投影鼠标 · Alt+G 返回 · 请使用窗口化/无边框"
                : "保持 Minecraft 焦点 · 桌面窗口控制"));
        panel.child(TextInfoPaneComponent.header("右键屏幕控制 · Alt+G 进入/退出"));

        panel.child(new TextInfoPaneComponent("投影状态", WorldWindow::status, false, false));

        if (WorldWindow.exists()) panel.child(new PanelItemComponent("销毁投影", () -> {
            WorldWindow.destroy();
            render(panel);
        }));

        panel.child(new TextInputPaneComponent("创建", null).asButton("创建", () -> {
            try {
                options.sanitize();
                CONFIG.update(s -> {
                });
                WorldWindow.create(selected);
                Minecraft.getInstance().gui.setScreen(null);
                ChatNotifier.send("§a[Window] 已创建投影，右键或 Alt+G 控制，再按 Alt+G 退出", NotificationType.SUCCESS);
            } catch (Exception e) {
                ChatNotifier.send("§c[Window] " + e.getMessage(), NotificationType.ERR);
            }
        }));
    }
}
