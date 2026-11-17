package com.wpa.olhofocado;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.UserManager;

public class OlhoFocadoAdminReceiver extends DeviceAdminReceiver {

    public static final String[] BROWSER_PACKAGES = new String[]{
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.brave.browser",
        "org.mozilla.firefox",
        "org.mozilla.fenix",
        "org.mozilla.focus",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.opera.gx",
        "com.sec.android.app.sbrowser",
        "com.microsoft.emmx",
        "com.duckduckgo.mobile.android",
        "com.UCMobile.intl",
        "com.kiwibrowser.browser",
        "com.vivaldi.browser",
        "mark.via.gp",
        "com.mi.globalbrowser"
    };

    public static java.util.Set<String> getAllBrowserPackages(Context context) {
        java.util.Set<String> set = new java.util.LinkedHashSet<>();
        for (String b : BROWSER_PACKAGES) {
            set.add(b);
        }
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            Intent webIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://www.example.com"));
            java.util.List<android.content.pm.ResolveInfo> list = pm.queryIntentActivities(webIntent, android.content.pm.PackageManager.MATCH_ALL);
            if (list != null) {
                for (android.content.pm.ResolveInfo ri : list) {
                    if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                        String p = ri.activityInfo.packageName;
                        if (!p.equals(context.getPackageName()) && !p.equals("org.jw.jwlibrary.mobile") && !p.equals("android")) {
                            set.add(p);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return set;
    }

    public static ComponentName getComponentName(Context context) {
        return new ComponentName(context.getApplicationContext(), OlhoFocadoAdminReceiver.class);
    }

    public static boolean isGuardLocked(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("OlhoFocadoPrefs", Context.MODE_PRIVATE);
        long until = prefs.getLong("guard_lock_until_ms", 0L);
        return until > System.currentTimeMillis();
    }

    public static boolean isBrowserFreezeActive(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("OlhoFocadoPrefs", Context.MODE_PRIVATE);
        long until = prefs.getLong("browser_freeze_until_ms", 0L);
        if (until > System.currentTimeMillis()) return true;
        if (isGuardLocked(context) && prefs.getBoolean("guard_night_curfew", true)) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
            if (hour >= 22 || hour < 6) {
                return true;
            }
        }
        return false;
    }

    public static void syncAllProtections(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null) return;
            ComponentName admin = getComponentName(context);
            String pkg = context.getPackageName();
            if (!dpm.isDeviceOwnerApp(pkg)) return;

            SharedPreferences prefs = context.getSharedPreferences("OlhoFocadoPrefs", Context.MODE_PRIVATE);
            boolean locked = isGuardLocked(context);
            boolean freezeBrowsers = isBrowserFreezeActive(context);
            boolean lockAdb = prefs.getBoolean("guard_lock_adb", true);

            boolean forceCleanDns = prefs.getBoolean("guard_force_clean_dns", true);
            String customWordsCsv = prefs.getString("guard_custom_keywords", "");

            if (locked) {
                dpm.setUninstallBlocked(admin, pkg, true);
                dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT);
                dpm.addUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL);
                dpm.addUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS);
                dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER);
                dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET);
                // Garante Data e Hora Automáticas da rede antes de bloquear alteração manual do relógio
                try {
                    dpm.setGlobalSetting(admin, android.provider.Settings.Global.AUTO_TIME, "1");
                    dpm.setGlobalSetting(admin, android.provider.Settings.Global.AUTO_TIME_ZONE, "1");
                } catch (Exception ignored) {}
                long maxAllowedFuture = System.currentTimeMillis() + (31L * 24L * 60L * 60L * 1000L);
                if (prefs.getLong("guard_lock_until_ms", 0L) > maxAllowedFuture) {
                    prefs.edit().putLong("guard_lock_until_ms", System.currentTimeMillis() + (30L * 24L * 60L * 60L * 1000L)).apply();
                }
                dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME);
                dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
                if (Build.VERSION.SDK_INT >= 29) {
                    dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS);
                    dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY);
                    if (forceCleanDns) {
                        final DevicePolicyManager fDpm = dpm;
                        final ComponentName fAdmin = admin;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    fDpm.setGlobalPrivateDnsModeSpecifiedHost(fAdmin, "family-filter-dns.cleanbrowsing.org");
                                } catch (Exception ignored) {}
                            }
                        }).start();
                    }
                }
                if (lockAdb) {
                    dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES);
                }

                // Injeção de Políticas Corporativas direto no motor do Chrome, Brave, Edge, Opera e Vivaldi:
                // 1) Remove "Nova guia anônima" do menu (IncognitoModeAvailability = 1)
                // 2) Força Google SafeSearch Estrito (ForceGoogleSafeSearch = true) e YouTube Restrito
                // 3) Bloqueia URLs nativamente no navegador antes de carregar (URLBlocklist)
                try {
                    android.os.Bundle browserPolicy = new android.os.Bundle();
                    browserPolicy.putInt("IncognitoModeAvailability", 1);
                    browserPolicy.putBoolean("ForceGoogleSafeSearch", true);
                    browserPolicy.putInt("ForceYouTubeRestrict", 2);

                    java.util.List<String> blockUrls = new java.util.ArrayList<>();
                    String[] baseBlockedDomains = new String[]{
                        "*pornhub*", "*xvideos*", "*xnxx*", "*xhamster*", "*redtube*", "*youporn*",
                        "*spankbang*", "*erome*", "*onlyfans*", "*chaturbate*", "*stripchat*", "*cam4*",
                        "*rule34*", "*nhentai*", "*hentai*", "*brazzers*", "*eporner*", "*tnaflix*",
                        "*beeg.com*", "*motherless*", "*hqporner*", "*fuq.com*", "*daftsex*",
                        "*contoseroticos*", "*tudocontos*", "*literotica*", "*lushstories*", "*sexstories*", "*nifty.org*"
                    };
                    for (String bd : baseBlockedDomains) blockUrls.add(bd);
                    if (customWordsCsv != null && !customWordsCsv.trim().isEmpty()) {
                        for (String cw : customWordsCsv.split(",")) {
                            String cl = cw.trim().toLowerCase(java.util.Locale.ROOT);
                            if (cl.length() >= 2) {
                                blockUrls.add("*" + cl + "*");
                            }
                        }
                    }
                    browserPolicy.putStringArray("URLBlocklist", blockUrls.toArray(new String[0]));

                    String[] chromiumBrowsers = new String[]{
                        "com.android.chrome", "com.chrome.beta", "com.chrome.dev",
                        "com.brave.browser", "com.microsoft.emmx", "com.opera.browser",
                        "com.vivaldi.browser", "com.sec.android.app.sbrowser", "com.kiwibrowser.browser"
                    };
                    for (String cbPkg : chromiumBrowsers) {
                        try {
                            dpm.setApplicationRestrictions(admin, cbPkg, browserPolicy);
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            } else {
                dpm.setUninstallBlocked(admin, pkg, false);
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT);
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL);
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS);
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_ADD_USER);
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET);
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME);
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
                if (Build.VERSION.SDK_INT >= 29) {
                    dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS);
                    dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY);
                }
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES);
                String[] chromiumBrowsers = new String[]{
                    "com.android.chrome", "com.chrome.beta", "com.chrome.dev",
                    "com.brave.browser", "com.microsoft.emmx", "com.opera.browser",
                    "com.vivaldi.browser", "com.sec.android.app.sbrowser", "com.kiwibrowser.browser"
                };
                for (String cbPkg : chromiumBrowsers) {
                    try {
                        dpm.setApplicationRestrictions(admin, cbPkg, new android.os.Bundle());
                    } catch (Exception ignored) {}
                }
            }

            // Verifica se a Acessibilidade está ativa; se foi desligada durante o Timer, tenta auto-reativar via DeviceOwner e aciona o Fail-Safe!
            String enabledAcc = android.provider.Settings.Secure.getString(
                    context.getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            boolean isAccActive = enabledAcc != null && enabledAcc.contains(pkg);
            if (locked && !isAccActive) {
                try {
                    String svcName = pkg + "/" + OlhoFocadoGuardService.class.getName();
                    String updatedAcc = (enabledAcc == null || enabledAcc.isEmpty()) ? svcName : (enabledAcc + ":" + svcName);
                    dpm.setSecureSetting(admin, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updatedAcc);
                    dpm.setSecureSetting(admin, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, "1");
                    isAccActive = true;
                } catch (Exception ignored) {}
            }

            // Mantém congelados todos os pacotes escolhidos pelo usuário na lista de 1 toque
            String frozenCsv = prefs.getString("guard_frozen_packages_csv", "");
            java.util.Set<String> userFrozenSet = new java.util.HashSet<>();
            if (frozenCsv != null && !frozenCsv.trim().isEmpty()) {
                for (String p : frozenCsv.split(",")) {
                    String cl = p.trim();
                    if (!cl.isEmpty()) {
                        userFrozenSet.add(cl);
                        try {
                            dpm.setApplicationHidden(admin, cl, true);
                        } catch (Exception ignored) {}
                    }
                }
            }

            // FAIL-SAFE ("Interruptor de Homem Morto"):
            // Congela/oculta TODOS os navegadores detectados no sistema (inclusive recém-baixados) e a Play Store se a Acessibilidade for desligada durante o Timer!
            java.util.Set<String> allBrowsers = getAllBrowserPackages(context);
            boolean shouldHideBrowsers = freezeBrowsers || (locked && !isAccActive);
            boolean wasHidden = prefs.getBoolean("browsers_currently_hidden", false);
            if (shouldHideBrowsers) {
                for (String bPkg : allBrowsers) {
                    try {
                        dpm.setApplicationHidden(admin, bPkg, true);
                    } catch (Exception ignored) {}
                }
                if (locked && !isAccActive) {
                    // Se a Acessibilidade foi desligada durante o Timer, também congela a Play Store na hora para impedir baixar novos apps!
                    try {
                        dpm.setApplicationHidden(admin, "com.android.vending", true);
                    } catch (Exception ignored) {}
                }
                prefs.edit().putBoolean("browsers_currently_hidden", true).apply();
            } else if (wasHidden) {
                for (String bPkg : allBrowsers) {
                    if (!userFrozenSet.contains(bPkg)) {
                        try {
                            dpm.setApplicationHidden(admin, bPkg, false);
                        } catch (Exception ignored) {}
                    }
                }
                if (!userFrozenSet.contains("com.android.vending")) {
                    try {
                        dpm.setApplicationHidden(admin, "com.android.vending", false);
                    } catch (Exception ignored) {}
                }
                prefs.edit().putBoolean("browsers_currently_hidden", false).apply();
            }
        } catch (Exception ignored) {}
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        if (isGuardLocked(context)) {
            Intent launch = new Intent(context, MainActivity.class);
            launch.putExtra("open_target", "pratica");
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(launch);
            return "MODO GUARDIÃO ATIVO: O bloqueio por timer não pode ser desativado antes do prazo.";
        }
        return "Desativar o Modo Guardião deixará o aparelho desprotegido.";
    }
}
