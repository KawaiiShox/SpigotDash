package de.tobias.spigotdash.utils.files;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;

import de.tobias.spigotdash.utils.errorCatcher;
import de.tobias.spigotdash.utils.pluginConsole;
import org.bukkit.configuration.file.YamlConfiguration;

import de.tobias.spigotdash.main;

public class configuration {
	
	public static String current_ver = "0.9";
	
	public static File cfg_file = new File(main.pl.getDataFolder(), "config.yml");
	public static YamlConfiguration yaml_cfg = null;
	
	public static HashMap<String, Object> CFG = new HashMap<>();
	public static boolean dark = false;

	public static HashMap<String, String> exampleHeaders = new HashMap<>();

	public static boolean init() {
		boolean created = false;
		CFG.put("PORT", 9678);
		CFG.put("FILE_VERSION", current_ver);
		CFG.put("WEB_PASSWORD", "PleaseChangeThis");
		CFG.put("UPDATE_REFRESH_TIME", 30);
		CFG.put("PLAYER_RECORD", 0);
		CFG.put("darkMode", true);
		//0.5
		CFG.put("autoUpdate", true);
		CFG.put("autoReloadOnUpdate", true);
		//0.6
		CFG.put("translations", "EN");
		//0.7
		CFG.put("USE_NGROK", false);
		CFG.put("NGROK_AUTH", "");
		//0.8
		CFG.put("NGROK_GET_UPDATES", false);
		CFG.put("NGROK_GET_URL", "https://www.ddnss.de/upd.php?key=<YOUR_KEY>&host=<YOUR_HOST>&ip=%HOST%");
		//0.9
		exampleHeaders.put("X-Auth-Email", "yourEmail@hoster.com");
		CFG.put("NGROK_GET_HEADERS", exampleHeaders);


		pluginConsole.sendMessage("Initializing Config File...");
		if(!cfg_file.exists()) {
			try {
				cfg_file.createNewFile();
				created = true;
				pluginConsole.sendMessage("&6Created new config File!");
			} catch (IOException e) {
				pluginConsole.sendMessage("&c[ERROR] Failed to create Config File: ");
				errorCatcher.catchException(e, false);
				return false;
			}
		}
		
		yaml_cfg = YamlConfiguration.loadConfiguration(cfg_file);

		//LOAD ONLY NEEDED KEYS
		for(String s : CFG.keySet()) {
			if(yaml_cfg.contains(s)) {
				CFG.replace(s, yaml_cfg.get(s));
			} else {
				if(!created) {
					pluginConsole.sendMessage("&6WARNING: Your Config File is missing some values: &b" + s);
				} else {
					yaml_cfg.set(s, CFG.get(s));
				}
			}
		}
		
		//WARN ON WRONG_VERSIONS
		if(!yaml_cfg.getString("FILE_VERSION").equalsIgnoreCase(current_ver)) {
			if(!tryUpgrade(yaml_cfg)) {
				pluginConsole.sendMessage("&6WARNING: Your Config File Version is not the newest (" + current_ver + ")");
				pluginConsole.sendMessage("&cTo fix this, you should delete the current Config and Restart the Server to generate a new one!");	
			}
		}
		
		if(created) {
			save();
		}
		
		pluginConsole.sendMessage("&aConfiguration loaded from File!");
		
		dark = yaml_cfg.getBoolean("darkMode");

		return true;
		
	}
	
	public static boolean tryUpgrade(YamlConfiguration yaml_cfg) {
		boolean migrated = false;
		String currentFileVer = yaml_cfg.getString("FILE_VERSION");
		if(Objects.requireNonNull(yaml_cfg.getString("FILE_VERSION")).equalsIgnoreCase("0.2")) {
			yaml_cfg.set("UPDATE_REFRESH_TIME", 30);
			yaml_cfg.set("FILE_VERSION", "0.3");
			save();
			migrated = true;
		}
		
		if(Objects.requireNonNull(yaml_cfg.getString("FILE_VERSION")).equalsIgnoreCase("0.3")) {
			yaml_cfg.set("PLAYER_RECORD", 0);
			yaml_cfg.set("darkMode", true);
			yaml_cfg.set("FILE_VERSION", "0.4");
			save();
			migrated = true;
		}
		
		if(Objects.requireNonNull(yaml_cfg.getString("FILE_VERSION")).equalsIgnoreCase("0.4")) {
			yaml_cfg.set("autoUpdate", true);
			yaml_cfg.set("autoReloadOnUpdate", true);
			yaml_cfg.set("FILE_VERSION", "0.5");
			save();
			migrated = true;
		}
		
		if(Objects.requireNonNull(yaml_cfg.getString("FILE_VERSION")).equalsIgnoreCase("0.5")) {
			yaml_cfg.set("translations", "EN");
			yaml_cfg.set("FILE_VERSION", "0.6");
			save();
			migrated = true;
		}
		
		if(Objects.requireNonNull(yaml_cfg.getString("FILE_VERSION")).equalsIgnoreCase("0.6")) {
			yaml_cfg.set("USE_NGROK", false);
			yaml_cfg.set("NGROK_AUTH", "");
			yaml_cfg.set("FILE_VERSION", "0.7");
			save();
			migrated = true;
		}

		if(Objects.requireNonNull(yaml_cfg.getString("FILE_VERSION")).equalsIgnoreCase("0.7")) {
			yaml_cfg.set("NGROK_GET_UPDATES", false);
			yaml_cfg.set("NGROK_GET_URL", "https://www.ddnss.de/upd.php?key=<YOUR_KEY>&host=<YOUR_HOST>&ip=%HOST%");
			yaml_cfg.set("FILE_VERSION", "0.8");
			save();
			migrated = true;
		}

		if(Objects.requireNonNull(yaml_cfg.getString("FILE_VERSION")).equalsIgnoreCase("0.8")) {
			yaml_cfg.set("NGROK_GET_HEADERS", exampleHeaders);
			yaml_cfg.set("FILE_VERSION", "0.9");
			save();
			migrated = true;
		}
		
		if(migrated) {
			pluginConsole.sendMessage("&2Migrated Configuration File to new Version &7(&6" + currentFileVer + " --> &b" + yaml_cfg.get("FILE_VERSION") + "&7)&2, ignore the Warnings above!");
		}
		return migrated;
	}
	
	public static boolean save() {
		try {
			yaml_cfg.save(cfg_file);
			return true;
		} catch (IOException e) {
			pluginConsole.sendMessage("&c[ERROR] Cannot save configuration: ");
			errorCatcher.catchException(e, false);
			return false;
		}
	}
}
