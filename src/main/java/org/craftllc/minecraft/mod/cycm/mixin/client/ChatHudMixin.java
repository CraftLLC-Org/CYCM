package org.craftllc.minecraft.mod.cycm.mixin.client;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.craftllc.minecraft.mod.cycm.CYCMClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Shadow
    @Final
    private List<ChatHudLine> messages;
    @Shadow
    @Final
    private List<ChatHudLine.Visible> visibleMessages;

    @Shadow
    protected abstract void addMessage(Text message, MessageSignatureData signature, MessageIndicator indicator);

    @Shadow
    public abstract void refresh();

    private boolean isInternalCall = false;

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Text message, MessageSignatureData signature, MessageIndicator indicator,
            CallbackInfo ci) {
        if (isInternalCall || CYCMClient.configManager == null || CYCMClient.configManager.getConfig() == null
                || !CYCMClient.configManager.getConfig().isGroupingMessages()) {
            return;
        }

        String currentText = message.getString();

        // Only group consecutive messages (check only the most recent message)
        if (!messages.isEmpty()) {
            ChatHudLine line = messages.get(0);
            String lineText = line.content().getString();

            boolean match = false;
            int count = 1;

            if (lineText.equals(currentText)) {
                match = true;
            } else if (lineText.contains(" §8x")) {
                int lastIndex = lineText.lastIndexOf(" §8x");
                String base = lineText.substring(0, lastIndex);
                if (base.equals(currentText)) {
                    match = true;
                    try {
                        count = Integer.parseInt(lineText.substring(lastIndex + 4).trim());
                    } catch (Exception e) {
                        count = 1;
                    }
                }
            }

            if (match) {
                // Remove the actual logic message
                messages.remove(0);
                // Refresh visible messages to remove all fragments of the old message
                refresh();

                count++;

                isInternalCall = true;
                Text newMessage = message.copy().append(Text.literal(" §8x" + count));
                // Call the original addMessage shadowed method
                addMessage(newMessage, signature, indicator);
                isInternalCall = false;
                ci.cancel();
                return;
            }
        }
    }
}
