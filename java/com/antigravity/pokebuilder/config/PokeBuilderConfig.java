package com.antigravity.pokebuilder.config;

import com.antigravity.pokebuilder.messages.PokeBuilderMessages;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class PokeBuilderConfig {

    public Economy economy = new Economy();
    public Prices prices = new Prices();
    public Tiers tiers = new Tiers();
    public BigDecimal confirmThreshold = BigDecimal.valueOf(2000);
    public Features features = new Features();
    public Security security = new Security();
    public Restrictions restrictions = new Restrictions();
    public Sounds sounds = new Sounds();
    public Logging logging = new Logging();
    public Looks looks = new Looks();
    // Locale key for the message base layer. "auto" resolves to the
    // server JVM default, falling back to en_us if unavailable. Any
    // explicit value (e.g. "fr_fr") is resolved first from
    // config/pokebuilder/lang/ then from the bundled resources.
    public String messageLocale = "auto";
    // PokeBuilder messages. ConfigLoader replaces this instance after
    // reading `message-locale` so the base layer matches the resolved
    // locale. Overrides from the HOCON messages{} block are applied on
    // top of whichever base locale was loaded.
    public PokeBuilderMessages messages = new PokeBuilderMessages();

    public static class Economy {
        public String currencyId = "";
        public String displayName = "";
        public String format = "#,##0";
    }

    public static class Prices {
        public IV iv = new IV();
        public EV ev = new EV();
        public BigDecimal nature = BigDecimal.valueOf(1000);
        public Ability ability = new Ability();
        public BigDecimal shinyToggle = BigDecimal.valueOf(5000);
        public BigDecimal genderSwap = BigDecimal.valueOf(1500);
        public BigDecimal ballChange = BigDecimal.valueOf(750);
        public BigDecimal dynamaxLevelPerStep = BigDecimal.valueOf(600);
        public BigDecimal gmaxFactor = BigDecimal.valueOf(4000);
        public BigDecimal teraType = BigDecimal.valueOf(3000);
        // New in 1.3.0
        public BigDecimal nickname = BigDecimal.valueOf(500);
        public BigDecimal friendshipPerStep = BigDecimal.valueOf(50);
        public BigDecimal statusCure = BigDecimal.valueOf(200);
    }

    public static class IV {
        public BigDecimal setSingle31 = BigDecimal.valueOf(500);
        public BigDecimal setSingle0 = BigDecimal.valueOf(100);
        public BigDecimal maxAll = BigDecimal.valueOf(2500);
    }

    public static class EV {
        public BigDecimal setSingle252 = BigDecimal.valueOf(400);
        public BigDecimal setSingle0 = BigDecimal.valueOf(100);
        public BigDecimal resetAll = BigDecimal.valueOf(300);
        public BigDecimal add10 = BigDecimal.valueOf(50);
    }

    public static class Ability {
        public BigDecimal normal = BigDecimal.valueOf(800);
        public BigDecimal hidden = BigDecimal.valueOf(2000);
    }

    // Permission-based price tiers. The entries map tier id → multiplier
    // applied to every sticker price (0.80 = 20% discount). A player gets
    // the first tier they hold `pokebuilder.tier.<id>` for, in the order
    // declared below; absent any match, the price is charged at 1.0×.
    // The "default" multiplier is the baseline used when no tier matches.
    public static class Tiers {
        public java.math.BigDecimal defaultMultiplier = java.math.BigDecimal.ONE;
        // Insertion order is significant — highest-value tiers listed
        // first in the HOCON file are matched first.
        public java.util.LinkedHashMap<String, java.math.BigDecimal> tiers = new java.util.LinkedHashMap<>();
    }

    // Per-feature on/off switches. Disabled features are hidden in the editor
    // GUI and refused by the transaction service, so a player cannot trigger
    // them even by spoofing a slot click. All default to true so existing
    // installs keep working unchanged.
    public static class Features {
        public boolean ivEditor = true;
        public boolean ivMaxAll = true;
        public boolean evEditor = true;
        public boolean evResetAll = true;
        public boolean nature = true;
        public boolean ability = true;
        public boolean abilityHidden = true;
        public boolean shiny = true;
        public boolean gender = true;
        public boolean ball = true;
        public boolean dynamaxLevel = true;
        public boolean gmaxFactor = true;
        public boolean teraType = true;
        // New in 1.3.0
        public boolean nickname = true;
        public boolean friendship = true;
        public boolean statusCure = true;
        // PC access is OFF by default — admins opt in per server because
        // it widens the attack surface to include stored Pokémon.
        public boolean pcAccess = false;
    }

    public static class Security {
        public long clickCooldownMs = 500L;
        public int maxModificationsPerMinute = 20;
        public boolean allowCreativeMode = false;
        public boolean allowFaintedPokemon = true;
        // Master gate for the pokebuilder.bypass.* permission family
        // (bypass.cost, bypass.cooldown). DEFAULT FALSE — even if a
        // permission plugin grants the bypass node (commonly through a
        // wildcard like `pokebuilder.*` on an admin group), the
        // transaction layer treats it as not-granted. Server owners
        // who actually want bypass functionality must opt in by
        // flipping this to true. Prevents the silent "every admin
        // gets free purchases" surprise.
        public boolean allowBypassPermissions = false;
    }

    public static class Restrictions {
        public boolean blockLegendaries = false;
        public boolean blockMythicals = false;
        public boolean blockUltraBeasts = false;
        public boolean blockParadox = false;
        public List<String> blockedSpecies = new ArrayList<>();
        public List<String> blockedModifications = new ArrayList<>();
    }

    public static class Sounds {
        public boolean enabled = true;
        public float volume = 0.7f;
        public float pitch = 1.0f;
    }

    public static class Logging {
        public boolean logToFile = true;
        public String logFile = "logs/pokebuilder-transactions.log";
        public boolean logToConsole = true;
    }

    public static class Looks {
        public int borderColor = 7;
        public int confirmButtonColor = 5;
        public int cancelButtonColor = 14;
    }

    public void validate() {
        if (security.clickCooldownMs < 100) security.clickCooldownMs = 100;
        if (security.clickCooldownMs > 5000) security.clickCooldownMs = 5000;
        if (security.maxModificationsPerMinute < 1) security.maxModificationsPerMinute = 1;
    }
}
