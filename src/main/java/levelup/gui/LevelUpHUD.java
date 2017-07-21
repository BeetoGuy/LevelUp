package levelup.gui;

import levelup.ClassBonus;
import levelup.LevelUp;
import levelup.player.PlayerExtendedProperties;
import levelup.event.FMLEventHandler;
import levelup.event.PlayerEventHandler;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;

import java.awt.*;
import java.util.List;

public final class LevelUpHUD extends Gui {
    public static final LevelUpHUD INSTANCE = new LevelUpHUD();
    private float val, valIncr;

    private LevelUpHUD() {
        val = 0.7F;
        valIncr = 0.005F;
    }

    public void addToText(List<String> left) {
        byte playerClass = PlayerExtendedProperties.getPlayerClass(LevelUp.proxy.getPlayer());
        if (playerClass != 0) {
            if (!LevelUp.renderExpBar) {
                int skillXP = PlayerExtendedProperties.from(LevelUp.proxy.getPlayer()).getSkillFromIndex("XP");
                if (skillXP > 0) {
                    left.add(I18n.format("hud.skill.text1", skillXP));
                }
            }
            left.add(I18n.format("hud.skill.text2", I18n.format("class" + playerClass + ".name")));
        } else if (canSelectClass()) {
            if (!LevelUp.renderExpBar)
                left.add(I18n.format("hud.skill.select"));
        }
    }

    @SubscribeEvent
    public void renderLvlUpHUD(RenderGameOverlayEvent.Pre event) {
        if (LevelUp.allowHUD && LevelUp.proxy.getPlayer() != null) {
            if (LevelUp.renderTopLeft && event.getType() == ElementType.TEXT)
                addToText(((RenderGameOverlayEvent.Text) event).getLeft());
            if (LevelUp.renderExpBar && event.getType() == ElementType.EXPERIENCE)
                addToExpBar(event.getResolution());
        }
    }

    @SubscribeEvent
    public void onFOV(FOVUpdateEvent event){
        if(!LevelUp.changeFOV && !event.getEntity().isUser()) {
            int skill = 0;
            if(event.getEntity().isSneaking()){
                skill = 2 * FMLEventHandler.getSkill(event.getEntity(), 8);
            }else if(event.getEntity().isSprinting()){
                skill = FMLEventHandler.getSkill(event.getEntity(), 6);
            }
            if(skill > 0){
                event.setNewfov(event.getFov() - 0.5F);
                event.setNewfov(event.getNewfov() * 1/(1.0F + skill / 100F));
                event.setNewfov(event.getNewfov() + 0.5F);
            }
        }
    }

    private void addToExpBar(ScaledResolution res) {
        val += valIncr;
        if (val >= 1.0F || val <= 0.4F) {
            valIncr *= -1F;
        }
        if (val > 1.0F) {
            val = 1.0F;
        }
        if (val < 0.4F) {
            val = 0.4F;
        }
        String text = null;
        if (canShowSkills()) {
            int skillXP = PlayerExtendedProperties.from(LevelUp.proxy.getPlayer()).getSkillFromIndex("XP");
            if (skillXP > 0 && PlayerExtendedProperties.from(LevelUp.proxy.getPlayer()).getSkillPoints() < getTotalSkillPoints())
                text = I18n.format("hud.skill.text1", skillXP);
        } else if (canSelectClass())
            text = I18n.format("hud.skill.select");
        int x = (res.getScaledWidth() - Minecraft.getMinecraft().fontRendererObj.getStringWidth(text)) / 2;
        int y = res.getScaledHeight() - 29;
        if (text != null) {
            int col = Color.HSBtoRGB(0.2929688F, 1.0F, val) & 0xffffff;
            Minecraft.getMinecraft().fontRendererObj.drawString(text, x, y, col);
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(Gui.ICONS);//Icons texture reset
    }

    public static boolean canSelectClass() {
        if (LevelUp.proxy.getPlayer().experienceLevel >= PlayerEventHandler.minLevel)
            return true;
        else {
            int points = PlayerExtendedProperties.from(LevelUp.proxy.getPlayer()).getSkillPoints();
            return points > PlayerEventHandler.minLevel * PlayerEventHandler.xpPerLevel || points > ClassBonus.getBonusPoints();
        }
    }

    public static boolean canShowSkills() {
        return PlayerExtendedProperties.from(LevelUp.proxy.getPlayer()).hasClass();
    }

    private static int getTotalSkillPoints() {
        return ClassBonus.getMaxSkillPoints() * (ClassBonus.skillNames.length - 1);
    }
}
