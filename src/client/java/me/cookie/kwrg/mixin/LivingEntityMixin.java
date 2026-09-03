package me.cookie.kwrg.mixin;

import me.cookie.kwrg.irc.manager.HideArmorManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "getItemBySlot", at = @At("HEAD"), cancellable = true)
    private void onGetItemBySlot(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        if ((Object) this instanceof Player player) {
            if (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET) {
                if (HideArmorManager.shouldHideArmor(player)) {
                    cir.setReturnValue(ItemStack.EMPTY);
                }
            }
        }
    }
}