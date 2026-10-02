package com.leaden1.buttondiagnostic;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private TextView status, sessions, history;
    private MediaSessionManager manager;
    private final Map<String, MediaController.Callback> callbacks = new HashMap<>();
    private final List<String> historyLines = new ArrayList<>();

    private void log(String s) {
        historyLines.add(0, s);
        if (historyLines.size() > 80) historyLines.remove(historyLines.size() - 1);
        StringBuilder b = new StringBuilder();
        for (String x : historyLines) b.append(x).append("\n");
        history.setText(b.toString());
    }

    private ComponentName listenerComponent() {
        return new ComponentName(this, MediaNotificationListener.class);
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        manager = (MediaSessionManager)getSystemService(Context.MEDIA_SESSION_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,24,24,24);

        TextView title = new TextView(this);
        title.setText("LEADEN1 - DIAGNÓSTICO DE BOTONES V3");
        title.setTextSize(22);
        root.addView(title);

        status = new TextView(this);
        status.setText("● MONITOR DE MEDIA SESSIONS");
        status.setTextSize(17);
        root.addView(status);

        Button access = new Button(this);
        access.setText("ABRIR ACCESO A NOTIFICACIONES");
        access.setOnClickListener(v -> {
            try {
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        root.addView(access);

        Button refresh = new Button(this);
        refresh.setText("ACTUALIZAR SESIONES");
        refresh.setOnClickListener(v -> refreshSessions());
        root.addView(refresh);

        Button clear = new Button(this);
        clear.setText("LIMPIAR HISTORIAL");
        clear.setOnClickListener(v -> { historyLines.clear(); history.setText(""); });
        root.addView(clear);

        TextView st = new TextView(this);
        st.setText("\nSESIONES ACTIVAS");
        st.setTextSize(18);
        root.addView(st);

        sessions = new TextView(this);
        sessions.setTextSize(15);
        root.addView(sessions);

        TextView ht = new TextView(this);
        ht.setText("\nHISTORIAL");
        ht.setTextSize(18);
        root.addView(ht);

        history = new TextView(this);
        history.setTextSize(13);
        history.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(this);
        sv.addView(history);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        log("APP_INICIADA_V3");
        log("Objetivo: identificar la sesión que recibe los controles de GLASES.");
        refreshSessions();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshSessions();
    }

    private void refreshSessions() {
        try {
            List<MediaController> list = manager.getActiveSessions(listenerComponent());
            status.setText("● ACCESO A MEDIA SESSIONS ACTIVO");
            log("SESIONES_ENCONTRADAS=" + list.size());
            render(list);
            try {
                log("MEDIA_KEY_SESSION_PACKAGE=" + manager.getMediaKeyEventSessionPackageName());
            } catch (Exception e) {
                log("MEDIA_KEY_SESSION_NO_DISPONIBLE");
            }
        } catch (SecurityException e) {
            status.setText("● FALTA ACTIVAR ACCESO A NOTIFICACIONES");
            sessions.setText("Activa 'LEADEN1 Media Monitor' en Acceso a notificaciones y vuelve aquí.");
            log("SECURITY_EXCEPTION: se requiere Notification Access.");
        } catch (Exception e) {
            status.setText("● ERROR DE MONITOR");
            log("ERROR=" + e.getClass().getSimpleName());
        }
    }

    private void render(List<MediaController> list) {
        StringBuilder b = new StringBuilder();
        if (list.isEmpty()) b.append("No hay Media Sessions activas.\n");
        for (int i=0; i<list.size(); i++) {
            MediaController c = list.get(i);
            String key = c.getPackageName() + "|" + i;
            b.append("[").append(i).append("] ").append(c.getPackageName()).append("\n");
            b.append("  TAG: ").append(c.getTag()).append("\n");
            PlaybackState p = c.getPlaybackState();
            if (p != null) {
                b.append("  ESTADO: ").append(state(p.getState())).append("\n");
                b.append("  ACCIONES: ").append(p.getActions()).append("\n");
            }
            MediaMetadata m = c.getMetadata();
            if (m != null) {
                b.append("  TITULO: ").append(value(m.getText(MediaMetadata.METADATA_KEY_TITLE))).append("\n");
                b.append("  ARTISTA: ").append(value(m.getText(MediaMetadata.METADATA_KEY_ARTIST))).append("\n");
            }
            attach(c,key);
            b.append("\n");
        }
        sessions.setText(b.toString());
    }

    private String value(CharSequence x) { return x == null ? "-" : x.toString(); }

    private String state(int s) {
        switch(s) {
            case PlaybackState.STATE_PLAYING: return "PLAYING";
            case PlaybackState.STATE_PAUSED: return "PAUSED";
            case PlaybackState.STATE_STOPPED: return "STOPPED";
            case PlaybackState.STATE_BUFFERING: return "BUFFERING";
            case PlaybackState.STATE_NONE: return "NONE";
            default: return String.valueOf(s);
        }
    }

    private void attach(MediaController c, String key) {
        if (callbacks.containsKey(key)) return;
        MediaController.Callback cb = new MediaController.Callback() {
            @Override public void onPlaybackStateChanged(PlaybackState p) {
                runOnUiThread(() -> {
                    log("CAMBIO_ESTADO " + c.getPackageName() + " -> " + state(p.getState()));
                    refreshSessions();
                });
            }
            @Override public void onMetadataChanged(MediaMetadata m) {
                runOnUiThread(() -> {
                    log("CAMBIO_METADATA " + c.getPackageName() + " -> " + value(
                        m == null ? null : m.getText(MediaMetadata.METADATA_KEY_TITLE)));
                    refreshSessions();
                });
            }
            @Override public void onSessionDestroyed() {
                runOnUiThread(() -> {
                    log("SESSION_DESTROYED " + c.getPackageName());
                    refreshSessions();
                });
            }
        };
        callbacks.put(key, cb);
        c.registerCallback(cb);
        log("CONTROLLER_CONECTADO: " + c.getPackageName());
    }
}
