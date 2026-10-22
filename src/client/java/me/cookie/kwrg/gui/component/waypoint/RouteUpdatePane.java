package me.cookie.kwrg.gui.component.waypoint;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.cookie.kwrg.gui.component.BaseGuiComponent;
import me.cookie.kwrg.gui.component.route.RouteButtonComponent;
import me.cookie.kwrg.gui.util.AnimationUtil;
import me.cookie.kwrg.gui.util.RenderUtil;
import me.cookie.kwrg.util.PointEntry;
import me.cookie.kwrg.util.WaypointRouteUpdate;
import me.cookie.kwrg.util.ChatNotifier;
import me.cookie.kwrg.gui.notification.NotificationType;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Batch or per-node decisions; grows from the source update button and shrinks back into it. */
public final class RouteUpdatePane {
    private final RouteButtonComponent source;
    private final RouteButtonComponent.Bounds origin;
    private final Color originColor;
    private final WaypointRouteUpdate plan;
    private final List<WaypointRouteUpdate.Issue> issues;
    private final Runnable applied;
    private final Map<Integer, Boolean> decisions = new HashMap<>();
    private final List<RouteButtonComponent> follow = new ArrayList<>(), keep = new ArrayList<>();
    private final RouteButtonComponent all = new RouteButtonComponent(), keepAll = new RouteButtonComponent();
    private final RouteButtonComponent individual = new RouteButtonComponent(), cancel = new RouteButtonComponent();
    private long startedAt = System.currentTimeMillis();
    private boolean closing, perNode;
    private float closingFrom;
    private int x, y, width, height, scroll;
    private String error = "";

    public RouteUpdatePane(RouteButtonComponent source, WaypointRouteUpdate plan, Runnable applied) {
        this.source = source;
        origin = source.bounds();
        originColor = source.color();
        this.plan = plan;
        this.applied = applied;
        this.issues = plan.issues();

        for (int i = 0; i < issues.size(); i++) {
            follow.add(new RouteButtonComponent());
            keep.add(new RouteButtonComponent());
        }
    }

    public RouteButtonComponent source() { return source; }

    public boolean finished() { return closing && System.currentTimeMillis() - startedAt >= 250; }

    private float progress() {
        float p = AnimationUtil.easeOutCubic((System.currentTimeMillis() - startedAt) / 250f);
        return closing ? closingFrom * (1 - p) : p;
    }

    public void close() {
        if (!closing) {
            closingFrom = progress();
            closing = true;
            startedAt = System.currentTimeMillis();
        }
    }

    private void apply() {
        try {
            var summary = plan.apply(decisions);
            applied.run();
            ChatNotifier.send("§a[Waypoint] 更新完成 | " + summary.describe(), NotificationType.SUCCESS);
            close();
        } catch (Exception e) {
            error = e.getMessage() == null ? "更新失败，请重新打开窗口" : e.getMessage();
        }
    }

    private void chooseAll(boolean route) {
        for (int i = 0; i < issues.size(); i++) {
            decisions.put(i, route);
        }
        apply();
    }

    public void render(OwoUIGraphics graphics, int mouseX, int mouseY, int panelX, int panelY, int panelW, int panelH) {
        width = Math.min(460, panelW - 24);
        height = Math.min(320, panelH - 24);
        x = panelX + (panelW - width) / 2;
        y = panelY + (panelH - height) / 2;

        int half = (width - 44) / 2;
        all.bounds(x + 16, y + height - 70, half, 24);
        keepAll.bounds(x + 28 + half, y + height - 70, half, 24);
        individual.bounds(x + 16, y + height - 38, half, 24);
        cancel.bounds(x + 28 + half, y + height - 38, half, 24);

        int listH = height - 146;
        scroll = Mth.clamp(scroll, 0, Math.max(0, issues.size() * 62 - listH));
        for (int i = 0; i < follow.size(); i++) {
            int top = y + 52 + i * 62 - scroll;
            follow.get(i).bounds(x + width - 138, top + 18, 56, 22);
            keep.get(i).bounds(x + width - 74, top + 18, 56, 22);
        }

        float p = progress(), currentX = AnimationUtil.lerp(p, origin.x(), x), currentY = AnimationUtil.lerp(p, origin.y(), y);
        float currentW = AnimationUtil.lerp(p, origin.width(), width), currentH = AnimationUtil.lerp(p, origin.height(), height);

        RenderUtil.drawSubpixelRoundedPanel(
                graphics,
                currentX, currentY, currentW, currentH,
                AnimationUtil.lerp(p, 5, 10),
                AnimationUtil.lerpColor(p, originColor, Color.ofArgb(AnimationUtil.opaqueArgb(BaseGuiComponent.cardBg()))),
                Color.ofArgb(AnimationUtil.argb(BaseGuiComponent.regionFill(), 0x88))
        );

        float labelAlpha = 1 - Mth.clamp(p * 4, 0, 1);
        if (labelAlpha > 0.005f) {
            RenderUtil.drawCenteredSmoothText(
                    RenderUtil.withOpacity(graphics, labelAlpha),
                    Component.literal("更新"),
                    currentX + currentW / 2, currentY + currentH / 2,
                    0.8f, AnimationUtil.opaqueArgb(BaseGuiComponent.textBody())
            );
        }

        float alpha = Mth.clamp((p - 0.2f) / 0.8f, 0, 1);
        if (alpha <= 0.005f) {
            return;
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate(currentX, currentY);
        graphics.pose().scale(currentW / width, currentH / height);
        graphics.pose().translate(-x, -y);
        graphics.enableScissor(x, y, x + width, y + height);

        OwoUIGraphics content = RenderUtil.withOpacity(graphics, alpha);
        RenderUtil.drawSmoothText(content, "更新点位集 · " + (perNode ? "逐个决定" : "确认变更"), x + 16, y + 15, AnimationUtil.opaqueArgb(BaseGuiComponent.textStrong()));
        long deletions = issues.stream().filter(issue -> issue.kind() == WaypointRouteUpdate.Kind.DELETED).count();

        if (perNode) {
            graphics.enableScissor(x + 12, y + 50, x + width - 12, y + 50 + listH);
            for (int i = 0; i < issues.size(); i++) {
                int top = y + 52 + i * 62 - scroll;
                if (top + 60 < y + 50 || top > y + 50 + listH) {
                    continue;
                }
                var issue = issues.get(i);
                boolean deleted = issue.kind() == WaypointRouteUpdate.Kind.DELETED;

                RenderUtil.drawSmoothText(
                        content,
                        RenderUtil.fitText((deleted ? "路线已删除 · " : "点位冲突 · ") + issue.local().name, width - 170, 0.85f),
                        x + 16, top + 3,
                        deleted ? AnimationUtil.opaqueArgb(BaseGuiComponent.danger()) : AnimationUtil.opaqueArgb(BaseGuiComponent.warning())
                );
                RenderUtil.drawSmoothText(
                        content,
                        RenderUtil.fitText("本地 " + describe(issue.local()), width - 170, 0.85f),
                        x + 16, top + 23,
                        AnimationUtil.opaqueArgb(BaseGuiComponent.textMuted())
                );
                if (!deleted) {
                    RenderUtil.drawSmoothText(
                            content,
                            RenderUtil.fitText("路线 " + describe(issue.incoming()), width - 170, 0.85f),
                            x + 16, top + 40,
                            AnimationUtil.opaqueArgb(BaseGuiComponent.textMuted())
                    );
                }

                follow.get(i).draw(content, deleted ? "删除" : "用路线", Boolean.TRUE.equals(decisions.get(i)), mouseX, mouseY);
                keep.get(i).draw(content, "保留", Boolean.FALSE.equals(decisions.get(i)), mouseX, mouseY);
            }
            graphics.disableScissor();
        } else {
            RenderUtil.drawSmoothText(
                    content,
                    RenderUtil.fitText(plan.targetPath().getFileName().toString(), width - 32, 0.85f),
                    x + 16, y + 50,
                    AnimationUtil.opaqueArgb(BaseGuiComponent.accentSoft())
            );
            RenderUtil.drawSmoothText(content, "路线删除 " + deletions + " 个节点 · 点位冲突 " + (issues.size() - deletions) + " 个", x + 16, y + 80, AnimationUtil.opaqueArgb(BaseGuiComponent.warning()));
            RenderUtil.drawSmoothText(content, "另有 " + plan.additions() + " 个新增节点，将随确认结果加入", x + 16, y + 106, AnimationUtil.opaqueArgb(BaseGuiComponent.textMuted()));
            RenderUtil.drawSmoothText(content, "可全部采用路线、全部保留本地，或逐个决定", x + 16, y + 132, AnimationUtil.opaqueArgb(BaseGuiComponent.textMuted()));
        }

        if (!error.isEmpty()) {
            RenderUtil.drawSmoothText(content, RenderUtil.fitText(error, width - 32, 0.85f), x + 16, y + height - 90, AnimationUtil.opaqueArgb(BaseGuiComponent.danger()));
        }

        String allLabel = deletions == issues.size() ? "全部删除" : "全部采用路线";

        all.draw(content, perNode ? "应用更新 (" + decisions.size() + "/" + issues.size() + ")" : allLabel, false, mouseX, mouseY);
        keepAll.draw(content, perNode ? "返回批量选择" : "全部保留本地", false, mouseX, mouseY);
        individual.draw(content, perNode ? "全部选择路线" : "逐个决定", false, mouseX, mouseY);
        cancel.draw(content, "取消更新", false, mouseX, mouseY);
        graphics.disableScissor();
        graphics.pose().popMatrix();
    }

    public void mouseClicked(MouseButtonEvent click) {
        if (closing) {
            return;
        }

        if (click.button() == 1) {
            close();
            return;
        }

        if (click.button() != 0 || progress() < 0.999f) {
            return;
        }

        if (cancel.contains(click.x(), click.y())) {
            cancel.press();
            close();
        } else if (all.contains(click.x(), click.y())) {
            all.press();
            if (perNode) {
                apply();
            } else {
                chooseAll(true);
            }
        } else if (keepAll.contains(click.x(), click.y())) {
            keepAll.press();
            if (perNode) {
                perNode = false;
            } else {
                chooseAll(false);
            }
        } else if (individual.contains(click.x(), click.y())) {
            individual.press();
            if (perNode) {
                for (int i = 0; i < issues.size(); i++) {
                    decisions.put(i, true);
                }
            } else {
                perNode = true;
            }
        } else if (perNode && click.y() >= y + 50 && click.y() < y + height - 96) {
            for (int i = 0; i < follow.size(); i++) {
                if (follow.get(i).contains(click.x(), click.y())) {
                    follow.get(i).press();
                    decisions.put(i, true);
                    error = "";
                    return;
                }

                if (keep.get(i).contains(click.x(), click.y())) {
                    keep.get(i).press();
                    decisions.put(i, false);
                    error = "";
                    return;
                }
            }
        }
    }

    public void mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!closing && perNode && progress() >= 0.999f && mouseX >= x && mouseX < x + width && mouseY >= y + 50 && mouseY < y + height - 96) {
            scroll -= (int) (amount * 32);
        }
    }

    public void keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
        }
    }

    private static String describe(PointEntry point) {
        return (point.visible ? "显示 · " : "隐藏 · ") + point.name + " · " + point.x + ", " + point.y + ", " + point.z;
    }
}
