package net.fluxmc.fluxshop;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.UUID;

/** Money (Vault) and Gems (PlayerPoints) behind one small interface. */
public class Currencies {

    public interface Currency {
        String name();
        String symbol();
        boolean available();
        boolean has(Player p, double amount);
        boolean take(Player p, double amount);
        void give(Player p, double amount);
    }

    private final FluxShop plugin;

    public Currencies(FluxShop plugin) {
        this.plugin = plugin;
    }

    public Currency get(String id) {
        String key = (id == null || id.isEmpty()) ? "vault" : id.toLowerCase(Locale.ROOT);
        boolean isVault = key.equals("vault");
        String base = "currencies." + key + ".";
        String name = plugin.getConfig().getString(base + "name", isVault ? "Money" : key);
        String symbol = plugin.getConfig().getString(base + "symbol", "$");
        String provider = plugin.getConfig().getString(base + "provider", isVault ? "vault" : "playerpoints");
        if (provider.equalsIgnoreCase("playerpoints")) return new PlayerPointsCurrency(name, symbol);
        return new VaultCurrency(name, symbol);
    }

    private class VaultCurrency implements Currency {
        private final String name;
        private final String symbol;

        VaultCurrency(String name, String symbol) {
            this.name = name;
            this.symbol = symbol;
        }

        public String name() { return name; }
        public String symbol() { return symbol; }
        public boolean available() { return plugin.economy() != null; }

        public boolean has(Player p, double amount) {
            return plugin.economy().has(p, amount);
        }

        public boolean take(Player p, double amount) {
            return plugin.economy().withdrawPlayer(p, amount).transactionSuccess();
        }

        public void give(Player p, double amount) {
            plugin.economy().depositPlayer(p, amount);
        }
    }

    /** Talks to PlayerPoints through reflection so it is an optional dependency. */
    private class PlayerPointsCurrency implements Currency {
        private final String name;
        private final String symbol;

        PlayerPointsCurrency(String name, String symbol) {
            this.name = name;
            this.symbol = symbol;
        }

        public String name() { return name; }
        public String symbol() { return symbol; }

        private Object api() {
            Plugin pp = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (pp == null || !pp.isEnabled()) return null;
            try {
                return pp.getClass().getMethod("getAPI").invoke(pp);
            } catch (Exception e) {
                return null;
            }
        }

        public boolean available() {
            return api() != null;
        }

        public boolean has(Player p, double amount) {
            Object api = api();
            if (api == null) return false;
            try {
                Object bal = api.getClass().getMethod("look", UUID.class).invoke(api, p.getUniqueId());
                return ((Number) bal).intValue() >= (int) Math.ceil(amount);
            } catch (Exception e) {
                return false;
            }
        }

        public boolean take(Player p, double amount) {
            Object api = api();
            if (api == null) return false;
            try {
                Object ok = api.getClass().getMethod("take", UUID.class, int.class)
                        .invoke(api, p.getUniqueId(), (int) Math.ceil(amount));
                return Boolean.TRUE.equals(ok);
            } catch (Exception e) {
                return false;
            }
        }

        public void give(Player p, double amount) {
            Object api = api();
            if (api == null) return;
            try {
                api.getClass().getMethod("give", UUID.class, int.class)
                        .invoke(api, p.getUniqueId(), (int) Math.floor(amount));
            } catch (Exception ignored) {
            }
        }
    }
}
