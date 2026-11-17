package com.wpa.olhofocado;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Locale;

public class OlhoFocadoGuardService extends AccessibilityService {

    private long lastSyncTimeMs = 0L;
    private long lastRedirectTimeMs = 0L;

    private static final String[] ADULT_AND_BYPASS_TERMS = new String[]{
        "pornhub", "xvideos", "xnxx", "xhamster", "redtube", "youporn",
        "spankbang", "erome", "onlyfans", "chaturbate", "stripchat", "cam4",
        "rule34", "nhentai", "hentai", "brazzers", "eporner", "tnaflix",
        "beeg.com", "motherless", "hqporner", "fuq.com", "daftsex",
        "porno ", "pornografia", "sexo amador", "videos de sexo", "camgirl",
        "contos eroticos", "contos eróticos", "conto erotico", "conto erótico",
        "contoseroticos", "tudocontos", "literotica", "lushstories", "sexstories",
        "nifty.org", "historias eroticas", "histórias eróticas", "relatos eroticos",
        "relatos eróticos", "leituras eroticas", "contos adultos", "ficcao erotica",
        "guia anônima", "nova guia anônima", "navegação anônima", "aba privativa",
        "navegação privada", "incognito", "inprivate"
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        OlhoFocadoAdminReceiver.syncAllProtections(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        long now = System.currentTimeMillis();
        if (now - lastSyncTimeMs > 15000L) {
            lastSyncTimeMs = now;
            OlhoFocadoAdminReceiver.syncAllProtections(this);
        }

        boolean guardLocked = OlhoFocadoAdminReceiver.isGuardLocked(this);
        boolean browserFrozen = OlhoFocadoAdminReceiver.isBrowserFreezeActive(this);

        CharSequence pkgCsEarly = event.getPackageName();
        String pkgEarly = pkgCsEarly != null ? pkgCsEarly.toString().toLowerCase(Locale.ROOT) : "";

        // AUTO-PAREAMENTO ADB SEM TELA DIVIDIDA:
        // Quando o timer ainda está destravado e o usuário abre "Depuração sem fio" nas Configurações,
        // captura automaticamente a Porta Principal, a Porta de Pareamento e o Código de 6 dígitos da tela
        // e envia direto para o Termux via localhost (127.0.0.1:8765) e arquivo!
        if (!guardLocked && pkgEarly.contains("settings")) {
            AccessibilityNodeInfo rootEarly = getRootInActiveWindow();
            if (rootEarly != null) {
                StringBuilder sbEarly = new StringBuilder();
                collectNodeText(rootEarly, sbEarly, 0);
                String rawEarly = sbEarly.toString();
                String lowEarly = rawEarly.toLowerCase(Locale.ROOT);
                if (lowEarly.contains("depuração sem fio") || lowEarly.contains("wireless debugging") || lowEarly.contains("código de pareamento") || lowEarly.contains("pairing code")) {
                    // Shizuku rish used instead
                }
            }
        }

        if (!guardLocked && !browserFrozen) return;

        // NUNCA fecha ou interrompe o próprio Olho Focado (mesmo quando o teclado Gboard ou WebView dispara eventos na aba Ajustes!)
        if (MainActivity.isAppInForeground) {
            return;
        }
        CharSequence pkgCs = event.getPackageName();
        String pkg = pkgCs != null ? pkgCs.toString().toLowerCase(Locale.ROOT) : "";
        if (pkg.equals("com.wpa.olhofocado") || pkg.equals("org.jw.jwlibrary.mobile")
                || pkg.contains("inputmethod") || pkg.contains("gboard") || pkg.contains("keyboard")) {
            return;
        }
        AccessibilityNodeInfo activeRootCheck = getRootInActiveWindow();
        if (activeRootCheck != null && activeRootCheck.getPackageName() != null) {
            String activePkg = activeRootCheck.getPackageName().toString().toLowerCase(Locale.ROOT);
            if (activePkg.equals("com.wpa.olhofocado") || activePkg.equals("org.jw.jwlibrary.mobile")) {
                return;
            }
        }

        // 1. Se o Modo Banho (Congelar Navegadores) estiver ativo, bloqueia qualquer navegador imediatamente
        if (browserFrozen && isBrowserPackage(pkg)) {
            bounceToSurfingScreen(now);
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        StringBuilder sb = new StringBuilder();
        if (event.getText() != null) {
            for (CharSequence cs : event.getText()) {
                if (cs != null) sb.append(cs).append(' ');
            }
        }
        if (event.getContentDescription() != null) {
            sb.append(event.getContentDescription()).append(' ');
        }
        if (root != null) {
            collectNodeText(root, sb, 0);
        }
        String screenText = sb.toString().toLowerCase(Locale.ROOT);
        if (screenText.isEmpty()) return;

        // 2. Intercepta tentativa de entrar no Modo de Segurança pelo menu Power do sistema
        if (pkg.equals("android") || pkg.contains("systemui")) {
            if (screenText.contains("modo de segurança") || screenText.contains("safe mode") || screenText.contains("modo seguro")) {
                bounceToSurfingScreen(now);
                return;
            }
        }

        // 3. Intercepta tentativa de desativar Acessibilidade, Admin, Forçar Parada, Limpar Dados, DNS ou ADB nas Configurações
        if (pkg.contains("settings") || pkg.contains("securitycenter") || pkg.contains("permissioncontroller") || pkg.contains("packageinstaller") || pkg.contains("accessibility")) {
            boolean mentionsOurApp = screenText.contains("olho focado");
            boolean tamperAction = screenText.contains("forçar parada") || screenText.contains("desinstalar")
                    || screenText.contains("desativar") || screenText.contains("limpar dados")
                    || screenText.contains("limpar armazenamento") || screenText.contains("usar olho focado")
                    || screenText.contains("parar o app") || screenText.contains("desligar")
                    || screenText.contains("atualizar") || screenText.contains("instalar");
            boolean sensitiveSystemScreen = screenText.contains("apps de administrador")
                    || screenText.contains("administradores do dispositivo")
                    || screenText.contains("device admin")
                    || screenText.contains("dns privado")
                    || screenText.contains("private dns")
                    || screenText.contains("opções do desenvolvedor")
                    || screenText.contains("depuração sem fio")
                    || screenText.contains("depuração usb")
                    || screenText.contains("segundo espaço")
                    || screenText.contains("vários usuários");

            if ((mentionsOurApp && tamperAction) || sensitiveSystemScreen) {
                bounceToSurfingScreen(now);
                return;
            }
        }

        // 4. Filtro Universal (Navegadores + WebViews Internos de qualquer App como WhatsApp, Gmail, Notas, Redes Sociais, etc.):
        boolean isSystemOrLauncher = pkg.equals("android") || pkg.contains("systemui") || pkg.contains("launcher") || pkg.contains("settings");
        if (!isSystemOrLauncher) {
            for (String term : ADULT_AND_BYPASS_TERMS) {
                if (screenText.contains(term)) {
                    bounceToSurfingScreen(now);
                    return;
                }
            }
            SharedPreferences prefs = getSharedPreferences("OlhoFocadoPrefs", Context.MODE_PRIVATE);
            String customCsv = prefs.getString("guard_custom_keywords", "");
            if (customCsv != null && !customCsv.trim().isEmpty()) {
                String[] parts = customCsv.toLowerCase(Locale.ROOT).split(",");
                for (String p : parts) {
                    String clean = p.trim();
                    if (clean.length() >= 2 && (screenText.contains(clean) || pkg.contains(clean))) {
                        bounceToSurfingScreen(now);
                        return;
                    }
                }
            }
        }
    }

    private boolean isBrowserPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        for (String b : OlhoFocadoAdminReceiver.getAllBrowserPackages(this)) {
            if (pkg.equals(b.toLowerCase(Locale.ROOT))) return true;
        }
        return pkg.contains("browser") || pkg.contains("chrome") || pkg.contains("firefox") || pkg.contains("opera") || pkg.contains("sbrowser");
    }

    private void collectNodeText(AccessibilityNodeInfo node, StringBuilder out, int depth) {
        if (node == null || depth > 14 || out.length() > 4500) return;
        if (node.getText() != null) {
            out.append(node.getText()).append(' ');
        }
        if (node.getContentDescription() != null) {
            out.append(node.getContentDescription()).append(' ');
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectNodeText(child, out, depth + 1);
                child.recycle();
            }
        }
    }

    private void bounceToSurfingScreen(long now) {
        performGlobalAction(GLOBAL_ACTION_BACK);
        performGlobalAction(GLOBAL_ACTION_HOME);
        if (now - lastRedirectTimeMs < 600L) return;
        lastRedirectTimeMs = now;
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("open_target", "pratica");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    @Override
    public void onInterrupt() {
        OlhoFocadoAdminReceiver.syncAllProtections(getApplicationContext());
    }

    @Override
    public boolean onUnbind(Intent intent) {
        final Context appCtx = getApplicationContext();
        OlhoFocadoAdminReceiver.syncAllProtections(appCtx);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                OlhoFocadoAdminReceiver.syncAllProtections(appCtx);
            }
        }, 350);
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        OlhoFocadoAdminReceiver.syncAllProtections(getApplicationContext());
        super.onDestroy();
    }
}
