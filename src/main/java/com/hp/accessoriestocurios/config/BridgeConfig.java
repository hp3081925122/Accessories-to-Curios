package com.hp.accessoriestocurios.config;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class BridgeConfig {
    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        Pair<Common, ForgeConfigSpec> commonPair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = commonPair.getLeft();
        COMMON_SPEC = commonPair.getRight();

        Pair<Client, ForgeConfigSpec> clientPair = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = clientPair.getLeft();
        CLIENT_SPEC = clientPair.getRight();
    }

    private BridgeConfig() {
    }

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue disableAccessoriesMenu;
        public final ForgeConfigSpec.BooleanValue autoRegisterUsedSlots;
        public final ForgeConfigSpec.BooleanValue migrateLegacyData;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customSlotMappings;

        private Common(ForgeConfigSpec.Builder builder) {
            builder.push("bridge");

            this.disableAccessoriesMenu = builder
                    .comment(
                            "将服务端打开 Accessories 饰品界面的请求重定向到 Curios 饰品栏界面。",
                            "Redirect server-side requests opening the Accessories menu to the Curios menu."
                    )
                    .translation("accessories_to_curios.configuration.disableAccessoriesMenu")
                    .define("disableAccessoriesMenu", true);

            this.autoRegisterUsedSlots = builder
                    .comment(
                            "自动检测被模组实际使用的 Accessories 栏位并同步注册绑定到 Curios。",
                            "Automatically detect used Accessories slots and register/bind them into Curios."
                    )
                    .translation("accessories_to_curios.configuration.autoRegisterUsedSlots")
                    .define("autoRegisterUsedSlots", true);

            this.migrateLegacyData = builder
                    .comment(
                            "自动将旧存档中已装备在 Accessories 容器内的饰品、装饰栏与持久栏位修饰符无损迁移到 Curios。",
                            "Automatically migrate equipped items, cosmetics, and persistent slot modifiers from pre-bridge Accessories saves into Curios."
                    )
                    .translation("accessories_to_curios.configuration.migrateLegacyData")
                    .define("migrateLegacyData", true);

            this.customSlotMappings = builder
                    .comment(
                            "自定义栏位映射覆盖列表，格式为 accessories_slot=curios_slot（例如 shoes=feet）。",
                            "Custom slot mapping overrides in the format accessories_slot=curios_slot (e.g. shoes=feet)."
                    )
                    .translation("accessories_to_curios.configuration.customSlotMappings")
                    .defineListAllowEmpty(
                            List.of("customSlotMappings"),
                            List::of,
                            obj -> obj instanceof String entry && entry.indexOf('=') > 0 && entry.indexOf('=') < entry.length() - 1
                    );

            builder.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue hideAccessoriesUi;
        public final ForgeConfigSpec.BooleanValue redirectOpenToCurios;
        public final ForgeConfigSpec.BooleanValue enableDefaultAccessoryRenderer;

        private Client(ForgeConfigSpec.Builder builder) {
            builder.push("client");

            this.hideAccessoriesUi = builder
                    .comment(
                            "移除生存与创造背包界面上的 Accessories 按钮，并取消注册其独立快捷键。",
                            "Remove the Accessories button from survival/creative inventory screens and omit registering its standalone keymapping."
                    )
                    .translation("accessories_to_curios.configuration.hideAccessoriesUi")
                    .define("hideAccessoriesUi", true);

            this.redirectOpenToCurios = builder
                    .comment(
                            "将客户端调用打开 Accessories 界面的行为重定向为打开 Curios 界面。",
                            "Redirect client-side calls opening the Accessories screen to open the Curios screen."
                    )
                    .translation("accessories_to_curios.configuration.redirectOpenToCurios")
                    .define("redirectOpenToCurios", true);

            this.enableDefaultAccessoryRenderer = builder
                    .comment(
                            "允许未单独注册 3D 渲染器的原生 Accessories 饰品使用 Accessories 默认佩戴渲染器。",
                            "Allow genuine Accessories items without a custom 3D renderer to use Accessories' DefaultAccessoryRenderer."
                    )
                    .translation("accessories_to_curios.configuration.enableDefaultAccessoryRenderer")
                    .define("enableDefaultAccessoryRenderer", true);

            builder.pop();
        }
    }
}
