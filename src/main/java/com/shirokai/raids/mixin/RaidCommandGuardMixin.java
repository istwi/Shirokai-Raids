package com.shirokai.raids.mixin;

import com.mojang.brigadier.ParseResults;
import com.shirokai.raids.ShirokaiRaids;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public abstract class RaidCommandGuardMixin {
    @Inject(method="performCommand", at=@At("HEAD"), cancellable=true)
    private void shirokaiGuardCommand(ParseResults<CommandSourceStack> parse, String command, CallbackInfo ci) {
        if (ShirokaiRaids.blockCommand(parse.getContext().getSource(), command)) ci.cancel();
    }
}
