package com.wpa.olhofocado;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.widget.RemoteViews;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class HeatmapWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
            updateSingleWidget(context, appWidgetManager, appWidgetId, options);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        updateSingleWidget(context, appWidgetManager, appWidgetId, newOptions);
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(context, HeatmapWidgetProvider.class));
        for (int id : ids) {
            Bundle opts = mgr.getAppWidgetOptions(id);
            updateSingleWidget(context, mgr, id, opts);
        }
    }

    private static PendingIntent buildTargetPendingIntent(Context context, String target, int reqCode) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("open_target", target);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static void updateSingleWidget(Context context, AppWidgetManager mgr, int appWidgetId, Bundle options) {
        int minW = options != null ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180) : 180;
        int minH = options != null ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180) : 180;

        float density = context.getResources().getDisplayMetrics().density;
        int widthPx = Math.max(240, (int) (minW * density));
        int heightPx = Math.max(200, (int) (Math.max(120, minH - 36) * density));

        SharedPreferences prefs = context.getSharedPreferences("OlhoFocadoPrefs", Context.MODE_PRIVATE);
        String jsonStr = prefs.getString("state_json", "{}");
        JSONObject daysObj = new JSONObject();
        boolean isDarkMode = false;
        try {
            JSONObject root = new JSONObject(jsonStr);
            isDarkMode = root.optBoolean("darkMode", false);
            if (root.has("days")) {
                daysObj = root.getJSONObject("days");
            }
        } catch (Exception ignored) {}

        Calendar todayCal = Calendar.getInstance();
        SimpleDateFormat sdfIso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat sdfShort = new SimpleDateFormat("dd/MM", Locale.US);
        String todayIso = sdfIso.format(todayCal.getTime());
        String todayShort = sdfShort.format(todayCal.getTime());

        String hojeLabel = "Hoje " + todayShort + " · ○ Pendente";
        int hojeBgColor = Color.parseColor(isDarkMode ? "#26211B" : "#F3EDE0");
        int hojeTextColor = Color.parseColor(isDarkMode ? "#F3EDE0" : "#231F1A");

        if (daysObj.has(todayIso)) {
            try {
                JSONObject tObj = daysObj.getJSONObject(todayIso);
                String st = tObj.optString("status", "");
                int surfMin = tObj.optInt("surfingMinutes", 0);
                int surfSes = tObj.optInt("surfingSessions", 0);
                boolean hasSurf = (surfMin >= 10 || surfSes >= 1);
                boolean hasRpd = tObj.optBoolean("rpdCompleted", false) || (tObj.optJSONArray("rpdEntries") != null && tObj.optJSONArray("rpdEntries").length() > 0);
                boolean hasDiary = tObj.optBoolean("journalCompleted", false) || (tObj.optJSONArray("journalEntries") != null && tObj.optJSONArray("journalEntries").length() > 0);

                if ("won".equals(st)) {
                    if (hasSurf && hasRpd && hasDiary) {
                        hojeLabel = "Hoje " + todayShort + " · ✓ Pleno";
                        hojeBgColor = Color.parseColor("#2D6A4F");
                        hojeTextColor = Color.parseColor("#FFFFFF");
                    } else {
                        hojeLabel = "Hoje " + todayShort + " · ✓ Suave";
                        hojeBgColor = Color.parseColor("#84B097");
                        hojeTextColor = Color.parseColor("#16291E");
                    }
                } else if ("lost".equals(st)) {
                    hojeLabel = "Hoje " + todayShort + " · Registrado";
                    hojeBgColor = Color.parseColor("#B83B2A");
                    hojeTextColor = Color.parseColor("#FFFFFF");
                }
            } catch (Exception ignored) {}
        }

        Bitmap bmp = renderPureHeatmapBitmap(daysObj, isDarkMode, todayCal, todayIso, widthPx, heightPx);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_heatmap);
        views.setInt(R.id.widget_root_container, "setBackgroundColor", Color.parseColor(isDarkMode ? "#161310" : "#FAF6EE"));
        views.setTextViewText(R.id.widget_btn_hoje, hojeLabel);
        views.setInt(R.id.widget_btn_hoje, "setBackgroundColor", hojeBgColor);
        views.setTextColor(R.id.widget_btn_hoje, hojeTextColor);

        views.setOnClickPendingIntent(R.id.widget_btn_pratica, buildTargetPendingIntent(context, "pratica", 101));
        views.setOnClickPendingIntent(R.id.widget_btn_hoje, buildTargetPendingIntent(context, "hoje", 102));
        views.setOnClickPendingIntent(R.id.widget_heatmap_image, buildTargetPendingIntent(context, "mapa", 103));

        views.setImageViewBitmap(R.id.widget_heatmap_image, bmp);
        mgr.updateAppWidget(appWidgetId, views);
    }

    private static Bitmap renderPureHeatmapBitmap(JSONObject daysObj, boolean isDarkMode, Calendar todayCal, String todayIso, int widthPx, int heightPx) {
        Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor(isDarkMode ? "#1E1A16" : "#FAF6EE"));
        RectF bgRect = new RectF(0, 0, widthPx, heightPx);
        canvas.drawRoundRect(bgRect, 18f, 18f, bgPaint);

        int pad = (int) (widthPx * 0.03f);
        int availW = widthPx - (pad * 2);

        int cols = 7;
        int gap = Math.max(4, availW / 55);
        int cellSize = (availW - (gap * (cols - 1))) / cols;

        // Reserva faixa superior para o Mês Vigente e os 7 Dias da Semana (Calendário Invertido)
        float monthTitleSize = Math.max(12f, cellSize * 0.36f);
        float dowTextSize = Math.max(10f, cellSize * 0.30f);
        int headerHeight = (int) (monthTitleSize + dowTextSize + (gap * 3.5f));

        String[] monthsPt = new String[]{
            "JANEIRO", "FEVEREIRO", "MARÇO", "ABRIL", "MAIO", "JUNHO",
            "JULHO", "AGOSTO", "SETEMBRO", "OUTUBRO", "NOVEMBRO", "DEZEMBRO"
        };
        String monthTitle = monthsPt[todayCal.get(Calendar.MONTH)] + " " + todayCal.get(Calendar.YEAR);

        Paint monthPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        monthPaint.setTextAlign(Paint.Align.CENTER);
        monthPaint.setFakeBoldText(true);
        monthPaint.setTextSize(monthTitleSize);
        monthPaint.setColor(Color.parseColor(isDarkMode ? "#DFB976" : "#7A5C32"));
        float monthY = pad + monthTitleSize;
        canvas.drawText(monthTitle, widthPx / 2f, monthY, monthPaint);

        String[] dowLabels = new String[]{"DOM", "SEG", "TER", "QUA", "QUI", "SEX•", "SÁB•"};
        Paint dowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dowPaint.setTextAlign(Paint.Align.CENTER);
        dowPaint.setFakeBoldText(true);
        dowPaint.setTextSize(dowTextSize);
        dowPaint.setColor(Color.parseColor(isDarkMode ? "#C2B6A3" : "#5A5246"));
        float dowY = monthY + gap + dowTextSize;
        for (int c = 0; c < 7; c++) {
            float colCenterX = pad + c * (cellSize + gap) + (cellSize / 2f);
            canvas.drawText(dowLabels[c], colCenterX, dowY, dowPaint);
        }

        int gridTopStart = pad + headerHeight;
        int availH = heightPx - gridTopStart - pad;
        int rows = Math.max(2, Math.min(26, (availH + gap) / (cellSize + gap)));

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar sundayCal = (Calendar) todayCal.clone();
        sundayCal.add(Calendar.DAY_OF_YEAR, -(sundayCal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY));

        Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        for (int r = 0; r < rows; r++) {
            Calendar weekStart = (Calendar) sundayCal.clone();
            weekStart.add(Calendar.DAY_OF_YEAR, -(r * 7));

            for (int c = 0; c < 7; c++) {
                Calendar dCal = (Calendar) weekStart.clone();
                dCal.add(Calendar.DAY_OF_YEAR, c);
                String iso = sdf.format(dCal.getTime());

                String colorHex = isDarkMode ? "#2C261F" : "#E6DFD3";
                if (iso.compareTo(todayIso) > 0) {
                    colorHex = isDarkMode ? "#201C17" : "#F3EDE0";
                } else if (daysObj.has(iso)) {
                    try {
                        JSONObject dObj = daysObj.getJSONObject(iso);
                        String st = dObj.optString("status", "");
                        int surfMin = dObj.optInt("surfingMinutes", 0);
                        int surfSes = dObj.optInt("surfingSessions", 0);
                        boolean hasSurf = (surfMin >= 10 || surfSes >= 1);
                        boolean hasRpd = dObj.optBoolean("rpdCompleted", false) || (dObj.optJSONArray("rpdEntries") != null && dObj.optJSONArray("rpdEntries").length() > 0);
                        boolean hasDiary = dObj.optBoolean("journalCompleted", false) || (dObj.optJSONArray("journalEntries") != null && dObj.optJSONArray("journalEntries").length() > 0);
                        if ("lost".equals(st)) {
                            colorHex = "#B83B2A";
                        } else if ("won".equals(st)) {
                            colorHex = (hasSurf && hasRpd && hasDiary) ? "#2D6A4F" : "#84B097";
                        }
                    } catch (Exception ignored) {}
                }

                cellPaint.setColor(Color.parseColor(colorHex));
                float left = pad + c * (cellSize + gap);
                float top = gridTopStart + r * (cellSize + gap);
                if (top + cellSize <= heightPx - pad + gap) {
                    RectF cellRect = new RectF(left, top, left + cellSize, top + cellSize);
                    canvas.drawRoundRect(cellRect, 6f, 6f, cellPaint);

                    Paint numPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    numPaint.setTextAlign(Paint.Align.CENTER);
                    numPaint.setFakeBoldText(true);
                    numPaint.setTextSize(Math.max(10f, cellSize * 0.42f));
                    if ("#2D6A4F".equals(colorHex) || "#B83B2A".equals(colorHex)) {
                        numPaint.setColor(Color.parseColor("#FFFFFF"));
                    } else if ("#84B097".equals(colorHex)) {
                        numPaint.setColor(Color.parseColor("#16291E"));
                    } else if ("#F3EDE0".equals(colorHex) || "#201C17".equals(colorHex)) {
                        numPaint.setColor(Color.parseColor(isDarkMode ? "#786D5E" : "#9E9382"));
                    } else {
                        numPaint.setColor(Color.parseColor(isDarkMode ? "#C2B6A3" : "#231F1A"));
                    }
                    int dayNum = dCal.get(Calendar.DAY_OF_MONTH);
                    float textY = top + (cellSize / 2f) - ((numPaint.descent() + numPaint.ascent()) / 2f);
                    canvas.drawText(String.valueOf(dayNum), left + (cellSize / 2f), textY, numPaint);
                }
            }
        }

        return bitmap;
    }
}
