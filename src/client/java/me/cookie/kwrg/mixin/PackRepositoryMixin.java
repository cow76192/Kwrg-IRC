package me.cookie.kwrg.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {

    @Shadow @Final @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void addKwrgFolderSource(RepositorySource[] sources, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        Path resourcePacksDir = mc.getResourcePackDirectory();
        if (resourcePacksDir == null) return;

        Path kwrgDir = resourcePacksDir.resolve("kwrg");

        Set<RepositorySource> mutableSources = new LinkedHashSet<>(this.sources);
        mutableSources.add(new FolderRepositorySource(
                kwrgDir,
                PackType.CLIENT_RESOURCES,
                PackSource.DEFAULT,
                mc.directoryValidator()
        ));
        this.sources = mutableSources;
    }
}