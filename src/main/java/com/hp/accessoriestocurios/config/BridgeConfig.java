package com.hp.accessoriestocurios.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class BridgeConfig {
    public static final Common COMMON;
    public static final ModConfigSpec COMMON_SPEC;

    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        Pair<Common, ModConfigSpec> commonPair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = commonPair.getLeft();
        COMMON_SPEC = commonPair.getRight();

        Pair<Client, ModConfigSpec> clientPair = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = clientPair.getLeft();
        CLIENT_SPEC = clientPair.getRight();
    }

    private BridgeConfig() {
    }

    public static final class Common {
        public final ModConfigSpec.BooleanValue disableAccessoriesMenu;
        public final ModConfigSpec.BooleanValue autoRegisterUsedSlots;
        public final ModConfigSpec.BooleanValue migrateLegacyData;
        public final ModConfigSpec.ConfigValue<List<? extends String>> customSlotMappings;

        private Common(ModConfigSpec.Builder builder) {
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
                            "自动检测被模组实际使用的 Accessories 槽位并同步注册绑定到 Curios。",
                            "Automatically detect used Accessories slots and register/bind them into Curios."
                    )
                    .translation("accessories_to_curios.configuration.autoRegisterUsedSlots")
                    .define("autoRegisterUsedSlots", true);

            this.migrateLegacyData = builder
                    .comment(
                            "自动将旧存档中已装备在 Accessories 容器内的饰品、装饰栏与持久槽位修饰符无损迁移到 Curios。",
                            "Automatically migrate equipped items, cosmetics, and persistent slot modifiers from pre-bridge Accessories saves into Curios."
                    )
                    .translation("accessories_to_curios.configuration.migrateLegacyData")
                    .define("migrateLegacyData", true);

            this.customSlotMappings = builder
                    .comment(
                            "自定义槽位映射覆盖列表，格式为 accessories_slot=curios_slot（例如 shoes=feet）。",
                            "Custom slot mapping overrides in the format accessories_slot=curios_slot (e.g. shoes=feet)."
                    )
                    .translation("accessories_to_curios.configuration.customSlotMappings")
                    .defineListAllowEmpty(
                            "customSlotMappings",
                            List::of,
                            () -> "hat=head",
                            obj -> obj instanceof String entry && entry.indexOf('=') > 0 && entry.indexOf('=') < entry.length() - 1
                    );

            builder.pop();
        }
    }

    public static final class Client {
        public final ModConfigSpec.BooleanValue hideAccessoriesUi;
        public final ModConfigSpec.BooleanValue redirectOpenToCurios;
        public final ModConfigSpec.BooleanValue enableDefaultAccessoryRenderer;

        private Client(ModConfigSpec.Builder builder) {
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
