package wlamir.servernewslist;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
// Правильные импорты для Minecraft 26.3
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

public class ModGameRules {

    // Правило: "news_lifetime_hours", по умолчанию 720 часов (30 дней)
    public static final GameRule<Integer> NEWS_LIFETIME_HOURS = GameRuleBuilder
            .forInteger(336)
            .category(GameRuleCategory.MISC)
            .buildAndRegister(wlamir.servernewslist.ServerNewsList.id("news_lifetime_hours"));

    public static final GameRule<Boolean> SAFE_DELETE = GameRuleBuilder
            .forBoolean(false)
            .category(GameRuleCategory.MISC)
            .buildAndRegister(wlamir.servernewslist.ServerNewsList.id("safe_delete"));
}