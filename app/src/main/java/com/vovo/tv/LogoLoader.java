package com.vovo.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Carrega logos da pasta assets ou de um endereço da internet, com cache em memória. */
public class LogoLoader {
    private static final LruCache<String, Bitmap> cache = new LruCache<>(40);
    private static final ExecutorService pool = Executors.newFixedThreadPool(2);
    private static final Handler main = new Handler(Looper.getMainLooper());

    public static void load(final Context ctx, final String logo, final ImageView view) {
        view.setTag(logo);
        view.setImageDrawable(null);
        if (logo == null || logo.isEmpty()) return;
        Bitmap b = cache.get(logo);
        if (b != null) { view.setImageBitmap(b); return; }
        final Context app = ctx.getApplicationContext();
        pool.execute(new Runnable() {
            @Override public void run() {
                Bitmap bmp = null;
                InputStream in = null;
                try {
                    if (logo.startsWith("http")) {
                        HttpURLConnection c = (HttpURLConnection) new URL(logo).openConnection();
                        c.setConnectTimeout(8000);
                        c.setReadTimeout(8000);
                        in = c.getInputStream();
                    } else {
                        try {
                            in = app.getAssets().open(logo);
                        } catch (Exception notBundled) {
                            // logo novo colocado no GitHub depois da instalação
                            String base = Github.rawBase();
                            if (base == null) throw notBundled;
                            HttpURLConnection c = (HttpURLConnection) new URL(base + "conteudo/" + logo).openConnection();
                            c.setConnectTimeout(8000);
                            c.setReadTimeout(8000);
                            in = c.getInputStream();
                        }
                    }
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    bmp = BitmapFactory.decodeStream(in, null, o);
                } catch (Exception ignored) {
                } finally {
                    try { if (in != null) in.close(); } catch (Exception ignored) { }
                }
                if (bmp == null) return;
                // reduz logos grandes para economizar memória da caixinha
                int max = 320;
                if (bmp.getWidth() > max || bmp.getHeight() > max) {
                    float s = Math.min((float) max / bmp.getWidth(), (float) max / bmp.getHeight());
                    bmp = Bitmap.createScaledBitmap(bmp, Math.max(1, (int) (bmp.getWidth() * s)),
                            Math.max(1, (int) (bmp.getHeight() * s)), true);
                }
                final Bitmap fin = bmp;
                cache.put(logo, fin);
                main.post(new Runnable() {
                    @Override public void run() {
                        if (logo.equals(view.getTag())) view.setImageBitmap(fin);
                    }
                });
            }
        });
    }
}
