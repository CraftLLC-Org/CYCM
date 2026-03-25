package org.craftllc.minecraft.mod.cycm.mixin.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.craftllc.minecraft.mod.cycm.CYCMClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Shadow
    @Final
    private List<GuiMessage> allMessages;

    @Shadow
    protected abstract void refreshTrimmedMessages();

    @Invoker("addMessage")
    protected abstract void cycm$addMessage(Component message, MessageSignature signature, GuiMessageSource source,
            GuiMessageTag tag);

    private boolean isInternalCall = false;

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag,
            CallbackInfo ci) {
        if (isInternalCall || CYCMClient.configManager == null || CYCMClient.configManager.getConfig() == null
                || !CYCMClient.configManager.getConfig().isGroupingMessages()) {
            return;
        }

        String currentText = message.getString();

        if (!allMessages.isEmpty()) {
            GuiMessage line = allMessages.get(0);
            String lineText = line.content().getString();

            boolean match = false;
            int count = 1;
            String suffixMarker = " x";

            if (lineText.equals(currentText)) {
                match = true;
            } else if (lineText.contains(suffixMarker)) {
                int lastIndex = lineText.lastIndexOf(suffixMarker);
                String base = lineText.substring(0, lastIndex);
                if (base.equals(currentText)) {
                    match = true;
                    try {
                        count = Integer.parseInt(lineText.substring(lastIndex + suffixMarker.length()).trim());
                    } catch (NumberFormatException e) {
                        count = 1;
                    }
                }
            }

            if (match) {
                allMessages.remove(0);
                refreshTrimmedMessages();

                count++;

                isInternalCall = true;
                Component newMessage = message.copy()
                        .append(Component.literal(" x" + count).withStyle(ChatFormatting.DARK_GRAY));
                cycm$addMessage(newMessage, signature, source, tag);
                isInternalCall = false;
                ci.cancel();
            }
        }
    }
}
