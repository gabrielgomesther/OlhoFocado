package com.wpa.olhofocado;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;

public class WebAppInterface {
    private final Context context;

    public WebAppInterface(Context context) {
        this.context = context;
    }

    @JavascriptInterface
    public void updateHeatmapData(String jsonPayload) {
        SharedPreferences prefs = context.getSharedPreferences("OlhoFocadoPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("state_json", jsonPayload).apply();
        HeatmapWidgetProvider.updateAllWidgets(context);
        NotificationScheduler.scheduleAllFromJson(context, jsonPayload);
    }

    @JavascriptInterface
    public void triggerImmediateNotification(String title, String message) {
        AlarmReceiver.showNotification(context, title, message, 999);
    }

    @JavascriptInterface
    public void saveNativeBackupVault(String vaultJson) {
        SharedPreferences prefs = context.getSharedPreferences("OlhoFocadoPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("backup_vault_json", vaultJson).apply();
        try {
            java.io.File outFile = new java.io.File(context.getFilesDir(), "olho_focado_backup_vault.json");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
            fos.write(vaultJson.getBytes("UTF-8"));
            fos.close();
        } catch (Exception ignored) {}
    }

    @JavascriptInterface
    public String loadNativeBackupVault() {
        try {
            java.io.File inFile = new java.io.File(context.getFilesDir(), "olho_focado_backup_vault.json");
            if (inFile.exists()) {
                java.io.FileInputStream fis = new java.io.FileInputStream(inFile);
                byte[] data = new byte[(int) inFile.length()];
                fis.read(data);
                fis.close();
                return new String(data, "UTF-8");
            }
        } catch (Exception ignored) {}
        SharedPreferences prefs = context.getSharedPreferences("OlhoFocadoPrefs", Context.MODE_PRIVATE);
        return prefs.getString("backup_vault_json", "[]");
    }

    @JavascriptInterface
    public String saveFileToDownloads(String filename, String content, String mimeType) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename);
                values.put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType != null ? mimeType : "text/plain");
                values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);

                android.content.ContentResolver resolver = context.getContentResolver();
                android.net.Uri uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    java.io.OutputStream os = resolver.openOutputStream(uri);
                    if (os != null) {
                        os.write(content.getBytes("UTF-8"));
                        os.flush();
                        os.close();
                        return "Download/" + filename;
                    }
                }
            }
            java.io.File dlDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            if (!dlDir.exists()) dlDir.mkdirs();
            java.io.File outFile = new java.io.File(dlDir, filename);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
            fos.write(content.getBytes("UTF-8"));
            fos.flush();
            fos.close();
            return outFile.getAbsolutePath();
        } catch (Exception e) {
            try {
                java.io.File fallback = new java.io.File(context.getExternalFilesDir(null), filename);
                java.io.FileOutputStream fos = new java.io.FileOutputStream(fallback);
                fos.write(content.getBytes("UTF-8"));
                fos.flush();
                fos.close();
                return fallback.getAbsolutePath();
            } catch (Exception ignored) {}
            return "";
        }
    }

    @JavascriptInterface
    public boolean openInJwLibraryOnly(String bibleCode) {
        try {
            String url = "https://www.jw.org/finder?srcid=jwlshare&wtlocale=T&prefer=lang&bible=" + bibleCode + "&pub=nwtsty";
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
            intent.setPackage("org.jw.jwlibrary.mobile");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public void pickBackupFile() {
        if (context instanceof MainActivity) {
            ((MainActivity) context).launchNativeJsonPicker();
        }
    }

    @JavascriptInterface
    public void shareTextContent(String title, String content) {
        try {
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, title);
            sendIntent.putExtra(Intent.EXTRA_TEXT, content);
            sendIntent.setType("text/plain");
            Intent chooser = Intent.createChooser(sendIntent, "Exportar / Compartilhar " + title);
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Exception ignored) {}
    }

    @JavascriptInterface
    public String getGuardStatusJson() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = OlhoFocadoAdminReceiver.getComponentName(context);
            String pkg = context.getPackageName();
            boolean isOwner = dpm != null && dpm.isDeviceOwnerApp(pkg);
            boolean isAdmin = dpm != null && dpm.isAdminActive(admin);

            String enabledServices = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            boolean isAcc = enabledServices != null && enabledServices.contains(pkg);

            SharedPreferences prefs = context.getSharedPreferences("OlhoFocadoPrefs", Context.MODE_PRIVATE);
            long lockUntil = prefs.getLong("guard_lock_until_ms", 0L);
            long freezeUntil = prefs.getLong("browser_freeze_until_ms", 0L);
            boolean lockAdb = prefs.getBoolean("guard_lock_adb", true);
            String customWords = prefs.getString("guard_custom_keywords", "");

            JSONObject obj = new JSONObject();
            obj.put("isDeviceOwner", isOwner);
            obj.put("isDeviceAdmin", isAdmin);
            obj.put("isAccessibilityEnabled", isAcc);
            obj.put("lockUntilMs", lockUntil);
            obj.put("browserFreezeUntilMs", freezeUntil);
            obj.put("lockAdb", lockAdb);
            obj.put("forceCleanDns", prefs.getBoolean("guard_force_clean_dns", true));
            obj.put("nightCurfew", prefs.getBoolean("guard_night_curfew", true));
            obj.put("customKeywords", customWords != null ? customWords : "");
            return obj.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    @JavascriptInterface
    public String activateGuardLock(long durationMinutes, boolean lockAdb, String customKeywords) {
        return activateGuardLockFull(durationMinutes, lockAdb, true, true, customKeywords);
    }

    @JavascriptInterface
    public String activateGuardLockFull(long durationMinutes, boolean lockAdb, boolean forceCleanDns, boolean nightCurfew, String customKeywords) {
        SharedPreferences prefs = context.getSharedPreferences("OlhoFocadoPrefs", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long currentUntil = prefs.getLong("guard_lock_until_ms", 0L);
        long requestedUntil = now + (durationMinutes * 60000L);
        long finalUntil = Math.max(currentUntil, requestedUntil);

        // REGRA CUMULATIVA INVIOLÁVEL NO JAVA:
        // Enquanto o Timer estiver ativo, nenhuma palavra/site já salvo pode ser apagado ou removido!
        String existingWords = prefs.getString("guard_custom_keywords", "");
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        if (currentUntil > now && existingWords != null && !existingWords.trim().isEmpty()) {
            for (String p : existingWords.split(",")) {
                String cl = p.trim().toLowerCase(java.util.Locale.ROOT);
                if (cl.length() >= 2) merged.add(cl);
            }
        }
        if (customKeywords != null && !customKeywords.trim().isEmpty()) {
            for (String p : customKeywords.split(",")) {
                String cl = p.trim().toLowerCase(java.util.Locale.ROOT);
                if (cl.length() >= 2) merged.add(cl);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String item : merged) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(item);
        }

        boolean finalCleanDns = (currentUntil > now && prefs.getBoolean("guard_force_clean_dns", true)) ? true : forceCleanDns;
        boolean finalCurfew = (currentUntil > now && prefs.getBoolean("guard_night_curfew", true)) ? true : nightCurfew;

        prefs.edit()
                .putLong("guard_lock_until_ms", finalUntil)
                .putBoolean("guard_lock_adb", lockAdb)
                .putBoolean("guard_force_clean_dns", finalCleanDns)
                .putBoolean("guard_night_curfew", finalCurfew)
                .putString("guard_custom_keywords", sb.toString())
                .apply();

        OlhoFocadoAdminReceiver.syncAllProtections(context);
        return getGuardStatusJson();
    }

    @JavascriptInterface
    public String activateBrowserFreeze(long durationMinutes) {
        SharedPreferences prefs = context.getSharedPreferences("OlhoFocadoPrefs", Context.MODE_PRIVATE);
        long currentFreeze = prefs.getLong("browser_freeze_until_ms", 0L);
        long currentLock = prefs.getLong("guard_lock_until_ms", 0L);
        long requestedUntil = System.currentTimeMillis() + (durationMinutes * 60000L);
        long finalFreeze = Math.max(currentFreeze, requestedUntil);
        long finalLock = Math.max(currentLock, finalFreeze);

        prefs.edit()
                .putLong("browser_freeze_until_ms", finalFreeze)
                .putLong("guard_lock_until_ms", finalLock)
                .apply();

        OlhoFocadoAdminReceiver.syncAllProtections(context);
        return getGuardStatusJson();
    }

    @JavascriptInterface
    public String getInstalledAppsJson() {
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = OlhoFocadoAdminReceiver.getComponentName(context);
            boolean isOwner = dpm != null && dpm.isDeviceOwnerApp(context.getPackageName());

            SharedPreferences prefs = context.getSharedPreferences("OlhoFocadoPrefs", Context.MODE_PRIVATE);
            String frozenCsv = prefs.getString("guard_frozen_packages_csv", "");
            java.util.Set<String> frozenSet = new java.util.HashSet<>();
            if (frozenCsv != null && !frozenCsv.trim().isEmpty()) {
                for (String p : frozenCsv.split(",")) {
                    if (!p.trim().isEmpty()) frozenSet.add(p.trim());
                }
            }

            java.util.Set<String> browserSet = OlhoFocadoAdminReceiver.getAllBrowserPackages(context);

            // Descobre todos os pacotes com Activity de Launcher (mesmo desativados/ocultos)
            java.util.Set<String> launchablePkgs = new java.util.HashSet<>();
            try {
                Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                java.util.List<android.content.pm.ResolveInfo> ril = pm.queryIntentActivities(
                        mainIntent,
                        android.content.pm.PackageManager.MATCH_ALL | android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS | android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
                );
                if (ril != null) {
                    for (android.content.pm.ResolveInfo ri : ril) {
                        if (ri != null && ri.activityInfo != null && ri.activityInfo.packageName != null) {
                            launchablePkgs.add(ri.activityInfo.packageName);
                        }
                    }
                }
            } catch (Exception ignored) {}

            java.util.List<android.content.pm.ApplicationInfo> apps = pm.getInstalledApplications(
                    android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES | android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS
            );
            java.util.List<JSONObject> items = new java.util.ArrayList<>();
            java.util.Set<String> seenPkgs = new java.util.HashSet<>();

            for (android.content.pm.ApplicationInfo ai : apps) {
                try {
                    if (ai == null || ai.packageName == null) continue;
                    String p = ai.packageName;
                    if (seenPkgs.contains(p)) continue;
                    if (p.equals(context.getPackageName()) || p.equals("android") || p.equals("com.android.systemui")
                            || p.equals("com.android.settings") || p.equals("org.jw.jwlibrary.mobile")
                            || p.contains("inputmethod") || p.contains("gboard") || p.contains("launcher3")
                            || p.equals("com.android.phone") || p.equals("com.google.android.dialer")) {
                        continue;
                    }
                    boolean isHidden = frozenSet.contains(p);
                    if (isOwner) {
                        try {
                            if (dpm.isApplicationHidden(admin, p)) isHidden = true;
                        } catch (Exception ignored) {}
                    }
                    boolean isUserApp = (ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
                            || (ai.flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                    boolean hasLaunch = launchablePkgs.contains(p) || (pm.getLaunchIntentForPackage(p) != null);
                    boolean isBrowser = browserSet.contains(p);
                    boolean isCommonTarget = p.equals("com.android.vending") || p.contains("termux") || p.contains("shizuku")
                            || p.contains("youtube") || p.contains("instagram") || p.contains("twitter")
                            || p.contains("reddit") || p.contains("telegram") || p.contains("facebook") || p.contains("tiktok")
                            || p.contains("chrome") || p.contains("browser") || p.contains("files") || p.contains("documentsui");

                    if (!hasLaunch && !isHidden && !isBrowser && !isCommonTarget && !isUserApp) {
                        continue;
                    }
                    seenPkgs.add(p);
                    CharSequence labelCs = pm.getApplicationLabel(ai);
                    String name = labelCs != null ? labelCs.toString().trim() : p;
                    if (name.isEmpty()) name = p;
                    JSONObject o = new JSONObject();
                    o.put("name", name);
                    o.put("pkg", p);
                    o.put("isBrowser", isBrowser);
                    o.put("frozen", isHidden);
                    items.add(o);
                } catch (Exception ignored) {}
            }

            java.util.Collections.sort(items, new java.util.Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject a, JSONObject b) {
                    boolean fa = a.optBoolean("frozen", false);
                    boolean fb = b.optBoolean("frozen", false);
                    if (fa != fb) return fa ? -1 : 1;
                    boolean ba = a.optBoolean("isBrowser", false);
                    boolean bb = b.optBoolean("isBrowser", false);
                    if (ba != bb) return ba ? -1 : 1;
                    return a.optString("name", "").compareToIgnoreCase(b.optString("name", ""));
                }
            });

            org.json.JSONArray arr = new org.json.JSONArray();
            for (JSONObject item : items) {
                arr.put(item);
            }
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    @JavascriptInterface
    public String toggleFreezeApp(String targetPkg, boolean shouldFreeze) {
        if (targetPkg == null || targetPkg.trim().isEmpty()) return getInstalledAppsJson();
        String cl = targetPkg.trim();
        if (cl.equals(context.getPackageName()) || cl.equals("com.android.settings") || cl.equals("com.android.systemui")) {
            return getInstalledAppsJson();
        }
        SharedPreferences prefs = context.getSharedPreferences("OlhoFocadoPrefs", Context.MODE_PRIVATE);
        boolean locked = OlhoFocadoAdminReceiver.isGuardLocked(context);
        // Regra anti-burla: durante o Timer ativo, permite congelar novos apps a qualquer momento, mas NUNCA descongelar!
        if (locked && !shouldFreeze) {
            return getInstalledAppsJson();
        }

        String frozenCsv = prefs.getString("guard_frozen_packages_csv", "");
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        if (frozenCsv != null && !frozenCsv.trim().isEmpty()) {
            for (String p : frozenCsv.split(",")) {
                if (!p.trim().isEmpty()) set.add(p.trim());
            }
        }
        if (shouldFreeze) {
            set.add(cl);
        } else {
            set.remove(cl);
        }
        StringBuilder sb = new StringBuilder();
        for (String item : set) {
            if (sb.length() > 0) sb.append(",");
            sb.append(item);
        }
        prefs.edit().putString("guard_frozen_packages_csv", sb.toString()).apply();

        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = OlhoFocadoAdminReceiver.getComponentName(context);
            if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                dpm.setApplicationHidden(admin, cl, shouldFreeze);
            }
        } catch (Exception ignored) {}

        return getInstalledAppsJson();
    }

    @JavascriptInterface
    public void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {}
    }

    @JavascriptInterface
    public void showAdbPairNotification() {
        // Shizuku rish used instead
    }

    @JavascriptInterface
    public void openDeviceAdminSettings() {
        try {
            ComponentName admin = OlhoFocadoAdminReceiver.getComponentName(context);
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Protege o Olho Focado contra desinstalação, Modo de Segurança e Forçar Parada enquanto o Timer do Modo Guardião estiver ativo.");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {}
    }
}
