package com.vovo.tv;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.common.Format;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.List;

/** Assiste um canal em tela cheia. Setas/CH+/CH- trocam de canal, números vão direto, Voltar sai. */
public class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView view;
    private TextView info, status;
    private List<Channel> channels;
    private int index;
    private final Handler handler = new Handler();
    private String typed = "";

    private final Runnable hideInfo = new Runnable() {
        @Override public void run() { info.setVisibility(View.GONE); }
    };
    private final Runnable retry = new Runnable() {
        @Override public void run() { play(index); }
    };
    private final Runnable goTyped = new Runnable() {
        @Override public void run() {
            try {
                int n = Integer.parseInt(typed) - 1;
                if (n >= 0 && n < channels.size()) play(n);
            } catch (Exception ignored) { }
            typed = "";
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);
        view = findViewById(R.id.player);
        info = findViewById(R.id.info);
        status = findViewById(R.id.status);
        channels = Channel.get(this);
        index = getIntent().getIntExtra("index", 0);
        hideSystemBars();
    }

    @Override
    protected void onStart() {
        super.onStart();
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 7.1.2) TvVovo/1.0")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000);
        // Mais "reserva" de vídeo: aguenta oscilações do Wi-Fi sem travar
        DefaultLoadControl load = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        20000,  // tenta manter sempre ~20 s guardados
                        60000,  // até 60 s
                        1500,   // começa a tocar com 1,5 s (abre mais rápido)
                        4000)   // depois de uma travada, espera 4 s antes de voltar
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
        // Começa na qualidade mais leve (abre rápido) e sobe sozinho quando a conexão permitir
        DefaultBandwidthMeter meter = new DefaultBandwidthMeter.Builder(this)
                .setInitialBitrateEstimate(500_000)
                .build();
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(http))
                .setLoadControl(load)
                .setBandwidthMeter(meter)
                .build();
        view.setPlayer(player);
        view.setUseController(false);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) showStatus("جاري التحميل…\nCarregando…");
                else if (state == Player.STATE_READY) status.setVisibility(View.GONE);
            }

            @Override public void onPlayerError(PlaybackException error) {
                showStatus("هذه القناة غير متاحة الآن\nCanal fora do ar — use ▲ ▼ para trocar");
                handler.removeCallbacks(retry);
                handler.postDelayed(retry, 15000); // tenta de novo sozinho
            }
        });
        play(index);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacksAndMessages(null);
        if (player != null) { player.release(); player = null; }
    }

    private void play(int i) {
        if (channels.isEmpty() || player == null) return;
        index = (i % channels.size() + channels.size()) % channels.size();
        getSharedPreferences("tv", MODE_PRIVATE).edit().putInt("last", index).apply();
        handler.removeCallbacks(retry);
        Channel c = channels.get(index);
        // Fica ~30 s atrás do "ao vivo": sobra espaço para guardar reserva e evitar travadas
        MediaItem item = new MediaItem.Builder()
                .setUri(Uri.parse(c.url))
                .setLiveConfiguration(new MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(30000)
                        .setMinOffsetMs(15000)
                        .build())
                .build();
        player.setMediaItem(item);
        player.prepare();
        player.setPlayWhenReady(true);
        showInfo();
    }

    private void showInfo() {
        Channel c = channels.get(index);
        String q = "";
        if (player != null) {
            Format f = player.getVideoFormat();
            if (f != null && f.height > 0) q = "   ·  " + f.height + "p";
        }
        info.setText((index + 1) + "   " + (c.ar.isEmpty() ? "" : c.ar + "   ") + c.name + q);
        info.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideInfo);
        handler.postDelayed(hideInfo, 4000);
    }

    private void showStatus(String text) {
        status.setText(text);
        status.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                play(index + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                play(index - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_INFO:
                showInfo();
                return true;
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            typed += (char) ('0' + keyCode - KeyEvent.KEYCODE_0);
            info.setText(typed);
            info.setVisibility(View.VISIBLE);
            handler.removeCallbacks(goTyped);
            handler.postDelayed(goTyped, 1500);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
