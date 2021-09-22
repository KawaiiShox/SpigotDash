package de.tobias.spigotdash.web.sockets;

import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tobias.spigotdash.main;
import de.tobias.spigotdash.utils.*;
import de.tobias.spigotdash.utils.files.configuration;
import de.tobias.spigotdash.utils.files.translations;
import de.tobias.spigotdash.utils.plugins.pluginInstaller;
import de.tobias.spigotdash.utils.plugins.pluginManager;
import de.tobias.spigotdash.web.dataprocessing.dataFetcher;
import de.tobias.spigotdash.web.dataprocessing.pageDataFetcher;
import de.tobias.spigotdash.web.dataprocessing.webBundler;
import io.netty.util.internal.ThreadLocalRandom;
import io.socket.socketio.server.SocketIoSocket;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.eclipse.jetty.http.HttpStatus;

import java.io.File;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public class SocketEventHandler {

    public static void handleSocketEvent(SocketIoSocket soc, String eventName, Object[] args) {
        if(eventName.equalsIgnoreCase("AUTH")) {
            authRequest(soc, args);
        }

        if(SocketAuthManager.isAuthed(soc)) {
            if(eventName.equalsIgnoreCase("REQUEST")) {
                request(args, soc);
            }
        } else {
            try {
                SocketRequest req = new SocketRequest(new JsonObject());
                req.setResponse(HttpStatus.UNAUTHORIZED_401, "TEXT", "ERR_REQUIRE_AUTH");
                ((SocketIoSocket.ReceivedByLocalAcknowledgementCallback) args[1]).sendAcknowledgement(req.getResponseAsJson());
            } catch(Exception IGNORE) {}
        }
    }

    public static void request(Object[] args, SocketIoSocket soc) {
        Bukkit.getScheduler().runTask(main.pl, new Runnable() {
                    @Override
                    public void run() {
                        JsonObject jsonobj = new JsonParser().parse(String.valueOf(args[0])).getAsJsonObject();

                        SocketRequest req = new SocketRequest(jsonobj);
                        String type = req.json.get("TYPE").getAsString();

                        try {
                            if(type.equalsIgnoreCase("PAGE")) {
                                handlePageRequest(req);
                            } else if(type.equalsIgnoreCase("WEBFILE")) {
                                handleWebfileRequest(req);
                            } else if(type.equalsIgnoreCase("SYSFILE")) {
                                handleSysfileRequest(req);
                            } else if(type.equalsIgnoreCase("EXECUTE")) {
                                handleExecutionRequest(req);
                            } else if(type.equalsIgnoreCase("DATA")) {
                                handleDataRequest(req);
                            } else if(type.equalsIgnoreCase("PAGEDATA")) {
                                handlePageDataRequest(req);
                            }
                        } catch(Exception ex) {
                            errorCatcher.catchException(ex, false);
                            req.setResponse(500, "TEXT", "INTERNAL_ERROR");
                        }

                        ((SocketIoSocket.ReceivedByLocalAcknowledgementCallback) args[1]).sendAcknowledgement(req.getResponseAsJson());
                    }
        });
    }

    public static void authRequest(SocketIoSocket soc, Object[] args) {
        JsonObject json = new JsonParser().parse(String.valueOf(args[0])).getAsJsonObject();
        SocketRequest req = new SocketRequest(json);

        String method = req.json.get("METHOD").getAsString();
        if(method.equalsIgnoreCase("STATE")) {
            req.setResponse(200, "BOOLEAN", SocketAuthManager.isAuthed(soc));
        } else if(method.equalsIgnoreCase("AUTHENTICATE")) {
            if (json.has("USERNAME") && json.has("PASSWORD")) {
                SocketAuthManager.authSocket(json.get("USERNAME").getAsString(), json.get("PASSWORD").getAsString(), soc, req);
            } else {
                req.setResponse(400, "TEXT", "ERR_MISSING_NAME_OR_PASSWORD");
            }
        }

        ((SocketIoSocket.ReceivedByLocalAcknowledgementCallback) args[1]).sendAcknowledgement(req.getResponseAsJson());
    }

    public static void handlePageRequest(SocketRequest req) {
        if(req.json.has("PAGE")) {
            req.setResponse(200, "TEXT", webBundler.getBundledPage("pages/" + req.json.get("PAGE").getAsString()));
            return;
        } else {
            req.setResponse(400, "TEXT", "ERR_MISSING_PAGE");
            return;
        }
    }

    public static void handleWebfileRequest(SocketRequest req) throws Exception {
        if(req.json.has("PATH")) {
            String file = req.json.get("PATH").getAsString();
            URL res = main.pl.getClass().getResource("/www/" + file);

            if(file.toLowerCase().indexOf(".html") > 0 || file.toLowerCase().indexOf(".css") > 0 || file.toLowerCase().indexOf(".js") > 0) {
                req.setResponse(200, "TEXT", translations.replaceTranslationsInString(Resources.toString(res, StandardCharsets.UTF_8)));
                return;
            } else {
                req.setResponse(200, "RESOURCE", res);
                return;
            }
        } else {
            req.setResponse(400, "TEXT", "ERR_MISSING_FILE");
        }
    }

    public static void handleSysfileRequest(SocketRequest req) {
        if(req.json.has("PATH")) {
            String file = req.json.get("PATH").getAsString();
            File f = dataFetcher.getFileWithPath(file);
            if (f.exists()) {
                if (f.isFile()) {
                    req.setResponse(200, "FILE", f);
                } else {
                    req.setResponse(400, "TEXT","ERR_FILE_IS_DIR");
                }
                return;
            } else {
                req.setResponse(404, "TEXT", "ERR_FILE_NOT_FOUND");
                return;
            }
        } else {
            req.setResponse(400, "TEXT", "ERR_MISSING_FILE");
            return;
        }
    }

    public static void handleExecutionRequest(SocketRequest req) {
        JsonObject json = req.json;
        String method = json.get("METHOD").getAsString();

        if (method.equalsIgnoreCase("EXEC_COMMAND")) {
            if (json.has("COMMAND")) {
                try {
                    pluginConsole.sendMessage("Executing: &6/" + json.get("COMMAND").getAsString());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), json.get("COMMAND").getAsString());
                    req.setResponse(200, "TEXT", "EXECUTED");
                    return;
                } catch (Exception ex) {
                    req.setResponse(500, "TEXT", "ERR_EXEC_FAILED");
                    return;
                }
            } else {
                req.setResponse(400, "TEXT", "ERR_MISSING_COMMAND");
                return;
            }
        }

        if (method.equalsIgnoreCase("TOGGLE_PLUGIN")) {
            if (json.has("PLUGIN")) {
                Plugin pl = pluginManager.getPlugin(json.get("PLUGIN").getAsString());
                if (pl.isEnabled()) {
                    boolean suc = pluginManager.disablePlugin(pl);
                    req.setResponse(suc ? 200 : 500, "TEXT", suc ? "SUCCESS" : "ERROR");
                    return;
                } else {
                    boolean suc = pluginManager.load(json.get("PLUGIN").getAsString());
                    req.setResponse(suc ? 200 : 500, "TEXT", suc ? "SUCCESS" : "ERROR");
                    return;
                }
            } else {
                req.setResponse(400, "TEXT", "ERR_MISSING_PLUGIN");
                return;
            }
        }

        if(method.equalsIgnoreCase("CONTROL")) {
            if(json.has("ACTION")) {
                String action = json.get("ACTION").getAsString();
                if(action.equalsIgnoreCase("STOP")) {
                    req.setResponse(200, "TEXT", "SUCCESS");
                    Bukkit.shutdown();
                    return;
                }

                if(action.equalsIgnoreCase("RELOAD")) {
                    req.setResponse(200, "TEXT", "SUCCESS");
                    Bukkit.reload();
                    return;
                }

                if(action.equalsIgnoreCase("TOGGLE_NETHER")) {
                    boolean current = Boolean.parseBoolean(dataFetcher.getServerPropertie("allow-nether"));
                    dataFetcher.setServerPropertie("allow-nether", String.valueOf(!current));
                    boolean suc = true;
                    notificationManager.setNeedReload(true);
                    req.setResponse(suc ? 200 : 500, "TEXT", suc ? "SUCCESS" : "ERROR");
                    return;
                }

                if(action.equalsIgnoreCase("TOGGLE_WHITELIST")) {
                    boolean current = Bukkit.hasWhitelist();

                    Bukkit.setWhitelist(!current);
                    dataFetcher.setServerPropertie("white-list", String.valueOf(!current));
                    boolean suc = true;
                    Bukkit.reloadWhitelist();
                    req.setResponse(suc ? 200 : 500, "TEXT", suc ? "SUCCESS" : "ERROR");
                    return;
                }

                if(action.equalsIgnoreCase("WHITELIST_ADD")) {
                    if (json.has("PLAYER")) {
                        String uuid = json.get("PLAYER").getAsString();
                        Bukkit.getOfflinePlayer(uuid).setWhitelisted(true);
                        req.setResponse(200, "TEXT", "SUCCESS");
                        return;
                    } else {
                        req.setResponse(400, "TEXT", "ERR_MISSING_PLAYER");
                        return;
                    }
                }

                if(action.equalsIgnoreCase("WHITELIST_REMOVE")) {
                    if (json.has("PLAYER")) {
                        String uuid = json.get("PLAYER").getAsString();
                        UUID uuidObj = dataFetcher.uuidFromUUIDWithoutDashes(uuid.replaceAll("-", ""));
                        Bukkit.getOfflinePlayer(uuidObj).setWhitelisted(false);
                        req.setResponse(200, "TEXT", "SUCCESS");
                        return;
                    } else {
                        req.setResponse(400, "TEXT", "ERR_MISSING_PLAYER");
                        return;
                    }
                }

                if(action.equalsIgnoreCase("OPERATOR_ADD")) {
                    if (json.has("PLAYER")) {
                        String uuid = json.get("PLAYER").getAsString();
                        Bukkit.getOfflinePlayer(uuid).setOp(true);
                        req.setResponse(200, "TEXT", "SUCCESS");
                        return;
                    } else {
                        req.setResponse(400, "TEXT", "ERR_MISSING_PLAYER");
                        return;
                    }
                }

                if(action.equalsIgnoreCase("OPERATOR_REMOVE")) {
                    if (json.has("PLAYER")) {
                        String uuid = json.get("PLAYER").getAsString();
                        UUID uuidObj = dataFetcher.uuidFromUUIDWithoutDashes(uuid.replaceAll("-", ""));
                        Bukkit.getOfflinePlayer(uuidObj).setOp(false);
                        req.setResponse(200, "TEXT","SUCCESS");
                        return;
                    } else {
                        req.setResponse(400, "TEXT", "ERR_MISSING_PLAYER");
                        return;
                    }
                }

                if(action.equalsIgnoreCase("TOGGLE_END")) {
                    boolean current = (Bukkit.getWorld("world_the_end") != null);
                    boolean suc = dataFetcher.modifyBukkitPropertie("settings.allow-end", !current);
                    notificationManager.setNeedReload(true);
                    req.setResponse(suc ? 200 : 500, "TEXT", suc ? "SUCCESS" : "ERROR");
                    return;
                }

            } else {
                req.setResponse(400, "TEXT", "ERR_MISSING_ACTION");
                return;
            }
        }

        if(method.equalsIgnoreCase("CONTROL_WORLD")) {
            if(json.has("WORLD")) {
                if(json.has("ACTION")) {
                    String action = json.get("ACTION").getAsString();
                    World w = Bukkit.getWorld(json.get("WORLD").getAsString());
                    if(w != null) {

                        if(action.equalsIgnoreCase("WEATHER")) {
                            if(json.has("WEATHER")) {
                                String weather = json.get("WEATHER").getAsString();
                                final boolean thundering = weather.equalsIgnoreCase("Thunder") ? true : false;
                                final boolean storming = weather.equalsIgnoreCase("Rain") || weather.equalsIgnoreCase("Thunder") ? true : false;

                                w.setStorm(storming);
                                w.setThundering(thundering);

                                if(thundering) {
                                    w.setThunderDuration(ThreadLocalRandom.current().nextInt(20*60*3, 20*60*13 + 1));
                                }

                                if(storming) {
                                    w.setThunderDuration((int) ThreadLocalRandom.current().nextDouble(24000*0.5, 24000*0.75 + 1));
                                }
                                req.setResponse(200, "TEXT", "SUCCESS");
                                return;
                            } else {
                                req.setResponse(400, "TEXT", "ERR_MISSING_WEATHER");
                                return;
                            }
                        }

                        if(action.equalsIgnoreCase("TIME")) {
                            if(json.has("TIME")) {
                                final Long time = json.get("TIME").getAsLong();

                                w.setTime(time);
                                req.setResponse(200, "TEXT", "SUCCESS");
                                return;
                            } else {
                                req.setResponse(400, "TEXT", "ERR_MISSING_TIME");
                                return;
                            }
                        }

                        if(action.equalsIgnoreCase("KILL_ENTITY_TYPE")) {
                            if(json.has("TYPE")) {
                                EntityType entType = EntityType.valueOf(json.get("TYPE").getAsString());

                                if (entType != null) {
                                    for (Entity e : w.getEntities()) {
                                        if (e.getType() == entType)
                                            e.remove();
                                    }
                                    req.setResponse(200, "TEXT", "KILLED");
                                    return;
                                } else {
                                    req.setResponse(400, "TEXT", "ERR_INVALID_ENTITYTYPE");
                                    return;
                                }
                            } else {
                                req.setResponse(400, "TEXT", "ERR_MISSING_TYPE");
                                return;
                            }
                        }

                    } else {
                        req.setResponse(400, "TEXT", "ERR_NOTFOUND_WORLD");
                        return;
                    }
                } else {
                    req.setResponse(400, "TEXT", "ERR_MISSING_ACTION");
                    return;
                }
            } else {
                req.setResponse(400, "TEXT", "ERR_MISSING_WORLD");
                return;
            }
        }

        if (method.equalsIgnoreCase("PLAYER_ACTION")) {
            if (json.has("PLAYER")) {
                String uuid = json.get("PLAYER").getAsString();
                Player p = Bukkit.getPlayer(UUID.fromString(uuid));

                if (p != null && p.isOnline()) {
                    if (json.has("ACTION")) {
                        String action = json.get("ACTION").getAsString();

                        if (action.equalsIgnoreCase("MESSAGE")) {
                            if (json.has("MESSAGE")) {
                                String message = json.get("MESSAGE").getAsString();
                                message = "&cServer &a--> &6You: &7" + message;
                                message = ChatColor.translateAlternateColorCodes('&', message);
                                p.sendMessage(message);

                                req.setResponse(200, "TEXT", "SUCCESS");
                                return;
                            } else {
                                req.setResponse(400, "TEXT", "ERR_MISSING_MESSAGE");
                                return;
                            }
                        }

                        if (action.equalsIgnoreCase("KICK")) {
                            if (json.has("MESSAGE")) {
                                String message = json.get("MESSAGE").getAsString();
                                message = "&cKicked from the Server:\n&b" + message;
                                message = ChatColor.translateAlternateColorCodes('&', message);
                                p.kickPlayer(message);

                                req.setResponse(200, "TEXT", "SUCCESS");
                                return;
                            } else {
                                req.setResponse(400, "TEXT", "ERR_MISSING_MESSAGE");
                                return;
                            }
                        }
                    } else {
                        req.setResponse(400, "TEXT", "ERR_MISSING_ACTION");
                        return;
                    }
                } else {
                    req.setResponse(400, "TEXT", "ERR_PLAYER_NOT_FOUND");
                    return;
                }
            } else {
                req.setResponse(400, "TEXT", "ERR_MISSING_PLAYER");
                return;
            }
        }

        if (method.equalsIgnoreCase("INSTALL_PLUGIN")) {
            if (json.has("ID")) {
                String install_state = pluginInstaller.installPlugin(json.get("ID").getAsString());
                int code = install_state.equalsIgnoreCase("INSTALLED") ? 200 : 500;
                req.setResponse(code, "TEXT", install_state);
                return;
            } else {
                req.setResponse(400, "TEXT", "ERR_MISSING_NOTIFICATION_UUID");
                return;
            }
        }

        if (method.equalsIgnoreCase("NOTIFICATION_CLOSED")) {
            if (json.has("UUID")) {
                notificationManager.closeNotification(json.get("UUID").getAsString());
                req.setResponse(200, "TEXT", "REMOVED");
                return;
            } else {
                req.setResponse(400, "TEXT", "ERR_MISSING_NOTIFICATION_UUID");
                return;
            }
        }

        if (method.equalsIgnoreCase("TOGGLE_PLUGIN")) {
            if (json.has("PLUGIN")) {
                Plugin pl = pluginManager.getPlugin(json.get("PLUGIN").getAsString());
                if (pl.isEnabled()) {
                    boolean suc = pluginManager.disablePlugin(pl);
                    req.setResponse(suc ? 200 : 500, "TEXT", suc ? "SUCCESS" : "ERROR");
                    return;
                } else {
                    boolean suc = pluginManager.load(json.get("plugin").getAsString());
                    req.setResponse(suc ? 200 : 500, "TEXT", suc ? "SUCCESS" : "ERROR");
                    return;
                }
            } else {
                req.setResponse(400, "TEXT", "ERR_MISSING_PLUGIN");
                return;
            }
        }
    }

    public static void handleDataRequest(SocketRequest req) {
        JsonObject json = req.json;
        String method = json.get("METHOD").getAsString();

        if (method.equalsIgnoreCase("GET_FILES_IN_PATH")) {
            if (json.has("PATH")) {
                String path = json.get("PATH").getAsString();
                req.setResponse(200, "TEXT", dataFetcher.getFilesInPath(path));
                return;
            } else {
                req.setResponse(400, "TEXT", "ERR_MISSING_PATH");
                return;
            }
        }

        if(method.equalsIgnoreCase("GET_WORLD")) {
            if (json.has("WORLD")) {
                if(Bukkit.getWorld(json.get("WORLD").getAsString()) == null) {
                    req.setResponse(200,"TEXT", "ERR_NOTFOUND_WORLD");
                    return;
                }

                req.setResponse(200, "TEXT", dataFetcher.getWorldForWeb(Bukkit.getWorld(json.get("WORLD").getAsString())));
                return;
            } else {
                req.setResponse(400, "TEXT", "ERR_MISSING_WORLD");
                return;
            }
        }

        if(method.equalsIgnoreCase("GET_NOTIFICATIONS")) {
            req.setResponse(200, "TEXT", notificationManager.notifications);
            return;
        }

        if(method.equalsIgnoreCase("THEME"))  {
            req.setResponse(200, "TEXT", (configuration.yaml_cfg.getBoolean("darkMode") ? "dark" : "light"));
            return;
        }
    }

    public static void handlePageDataRequest(SocketRequest req) {
        if(req.json.has("PAGE")) {
            String page = req.json.get("PAGE").getAsString();
            if(page.equalsIgnoreCase("OVERVIEW")) {
                req.setResponse(200, "TEXT", pageDataFetcher.GET_PAGE_OVERVIEW());
                return;
            }

            if(page.equalsIgnoreCase("GRAPHS")) {
                req.setResponse(200, "TEXT", pageDataFetcher.GET_PAGE_GRAPHS());
                return;
            }

            if(page.equalsIgnoreCase("WORLDS")) {
                req.setResponse(200, "TEXT", pageDataFetcher.GET_PAGE_WORLDS());
                return;
            }

            if(page.equalsIgnoreCase("CONSOLE")) {
                req.setResponse(200, "TEXT", dataFetcher.getLog(100));
                return;
            }

            if(page.equalsIgnoreCase("CONTROLS")) {
                req.setResponse(200, "TEXT", pageDataFetcher.GET_PAGE_CONTROLS());
                return;
            }

            if(page.equalsIgnoreCase("PLUGINS")) {
                req.setResponse(200, "TEXT", pageDataFetcher.GET_PAGE_PLUGINS());
                return;
            }

            if(page.equalsIgnoreCase("PLAYERS")) {
                req.setResponse(200, "TEXT", pageDataFetcher.GET_PAGE_PLAYERS());
                return;
            }
        } else {
            req.setResponse(400, "TEXT", "ERR_PAGE");
            return;
        }
    }
}