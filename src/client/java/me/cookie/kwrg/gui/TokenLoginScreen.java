package me.cookie.kwrg.gui;

import me.cookie.kwrg.util.AccountUtil;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

public class TokenLoginScreen extends Screen {
    private final Screen lastScreen;

    private EditBox currentInfoBox;
    private EditBox tokenField;
    private Button saveButton;

    private Component statusMessage = Component.empty();
    private boolean isVerifying = false;
    private CompletableFuture<AccountUtil.LoginResult> activeLoginFuture = null;

    public TokenLoginScreen(Screen lastScreen) {
        super(Component.literal("Token 账号切换"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.currentInfoBox = new EditBox(
                this.font,
                centerX - 150,
                centerY - 40,
                195,
                20,
                Component.literal("当前登录信息")
        );
        this.currentInfoBox.setMaxLength(4096);
        this.currentInfoBox.setEditable(false);
        this.refreshCurrentInfoWidget();
        this.addRenderableWidget(this.currentInfoBox);

        Button copyInfoBtn = Button.builder(
                Component.literal("复制当前登录信息"),
                button -> this.onCopyCurrentToken()
        ).bounds(centerX + 50, centerY - 40, 100, 20).build();
        this.addRenderableWidget(copyInfoBtn);

        this.tokenField = new EditBox(
                this.font,
                centerX - 150,
                centerY - 5,
                300,
                20,
                Component.literal("Access Token")
        );
        this.tokenField.setMaxLength(4096);
        this.tokenField.setHint(Component.literal("在此粘贴要切换的新 Access Token..."));
        this.addRenderableWidget(this.tokenField);

        this.saveButton = Button.builder(Component.literal("保存"), button -> this.onSave())
                .bounds(centerX - 150, centerY + 25, 145, 20)
                .build();
        this.saveButton.active = !this.isVerifying;
        this.addRenderableWidget(this.saveButton);

        Button backButton = Button.builder(Component.literal("返回"), button -> this.onClose())
                .bounds(centerX + 5, centerY + 25, 145, 20)
                .build();
        this.addRenderableWidget(backButton);

        this.setInitialFocus(this.tokenField);
    }

    private void refreshCurrentInfoWidget() {
        if (this.currentInfoBox == null || this.minecraft == null) return;

        User user = this.minecraft.getUser();
        String name = user.getName();
        String token = user.getAccessToken();
        boolean hasValidToken = token != null && !token.isEmpty() && !"0".equals(token);

        String tokenPreview = hasValidToken
                ? (token.length() > 12 ? token.substring(0, 12) + "..." : token)
                : "离线/无Token";

        this.currentInfoBox.setValue(name + " (" + tokenPreview + ")");
        this.currentInfoBox.moveCursorToStart(false);
    }

    private void onCopyCurrentToken() {
        String currentToken = AccountUtil.getAccessToken();

        if (currentToken == null || currentToken.isEmpty() || "0".equals(currentToken)) {
            this.statusMessage = Component.literal("§c✘ 当前账号 (" + AccountUtil.getUsername() + ") 处于离线模式，无有效 Token");
            return;
        }

        this.minecraft.keyboardHandler.setClipboard(currentToken);
        this.currentInfoBox.setValue(currentToken);
        this.currentInfoBox.moveCursorToStart(false);

        this.statusMessage = Component.literal("§a✔ 已获取并复制 [" +  AccountUtil.getUsername() + "] 的 Token 到剪贴板！");
    }

    private void onSave() {
        if (this.isVerifying) return;

        String token = this.tokenField.getValue().trim();
        if (token.isEmpty()) {
            this.statusMessage = Component.literal("§c请输入有效的 Token！");
            return;
        }

        this.isVerifying = true;
        this.saveButton.active = false;
        this.statusMessage = Component.literal("§e正在校验账号信息，请稍候...");

        CompletableFuture<AccountUtil.LoginResult> future = AccountUtil.loginWithToken(token);
        this.activeLoginFuture = future;

        future.thenAccept(result -> {
            this.minecraft.execute(() -> {
                if (this.minecraft.screen != this || this.activeLoginFuture != future) {
                    return;
                }

                this.isVerifying = false;
                this.saveButton.active = true;
                this.activeLoginFuture = null;

                if (result.success()) {
                    this.refreshCurrentInfoWidget();
                    this.tokenField.setValue("");
                    this.statusMessage = Component.literal("§a✔ " + result.message());
                } else {
                    this.statusMessage = Component.literal("§c✘ " + result.message());
                }
            });
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        graphics.centeredText(this.font, this.title, centerX, centerY - 70, 0xFFFFFFFF);
        graphics.text(this.font, Component.literal("§7当前登录账户信息:"), centerX - 150, centerY - 52, 0xFFAAAAAA);
        graphics.text(this.font, Component.literal("§7输入新 Token 切换账户:"), centerX - 150, centerY - 17, 0xFFAAAAAA);

        if (!this.statusMessage.getString().isEmpty()) {
            graphics.centeredText(this.font, this.statusMessage, centerX, centerY + 55, 0xFFFFFFFF);
        }
    }

    private void invalidatePendingLogin() {
        AccountUtil.cancelPendingLogins();
        if (this.activeLoginFuture != null) {
            this.activeLoginFuture.cancel(true);
            this.activeLoginFuture = null;
        }
        this.isVerifying = false;
    }

    @Override
    public void removed() {
        this.invalidatePendingLogin();
        super.removed();
    }

    @Override
    public void onClose() {
        this.invalidatePendingLogin();
        this.minecraft.setScreen(this.lastScreen);
    }
}