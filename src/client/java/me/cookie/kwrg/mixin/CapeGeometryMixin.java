package me.cookie.kwrg.mixin;

import me.cookie.kwrg.irc.IrcConfig;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.model.player.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(PlayerCapeModel.class)
public abstract class CapeGeometryMixin {

    @Overwrite
    public static LayerDefinition createCapeLayer() {
        int canvas = IrcConfig.get().capeCanvasSize;
        float texScale = 64.0F / canvas;

        MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
        PartDefinition root = mesh.getRoot().clearRecursively();
        PartDefinition body = root.getChild("body");
        body.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, 0.0F, -1.0F, 10.0F, 16.0F, 1.0F, CubeDeformation.NONE, texScale, texScale), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, (float) Math.PI, 0.0F));
        return LayerDefinition.create(mesh, canvas, canvas);
    }
}