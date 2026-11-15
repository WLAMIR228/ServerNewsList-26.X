package wlamir.servernewslist.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ServerNewsListConfig {
    // Настройка: показывать время в часовом поясе игрока (true) или в UTC (false)
    public boolean show_my_timezone = false;
    // Варианты: "top_left", "bottom_left", "top_right", "bottom_right", "center"
    public String button_pos = "bottom_left";

    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("servernewslist/config.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ServerNewsListConfig INSTANCE;

    public static ServerNewsListConfig getInstance() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, ServerNewsListConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // Если файла нет или он пустой — создаем новый со значениями по умолчанию
        if (INSTANCE == null) {
            INSTANCE = new ServerNewsListConfig();
            save();
        }
    }

    public static void save() {
        try {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}