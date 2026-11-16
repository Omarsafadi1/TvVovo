package com.vovo.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fotos do vovô: as que vieram dentro do app (pasta conteudo/fotos)
 * ou, se o GitHub estiver configurado, as que estiverem em conteudo/fotos no repositório.
 */
public class Photos {
    private static final long REFRESH_EVERY_MS = 60L * 60 * 1000; // no máximo 1x por hora

    /** Lista de fotos disponíveis agora. Itens começam com "asset:" ou "file:". */
    public static List<String> list(Context ctx) {
        List<String> out = new ArrayList<>();
        File[] files = dir(ctx).listFiles();
        if (files != null) {
            for (File f : files) if (isImage(f.getName())) out.add("file:" + f.getAbsolutePath());
        }
        if (out.isEmpty()) {
            try {
                String[] names = ctx.getAssets().list("fotos");
                if (names != null) for (String n : names) if (isImage(n)) out.add("asset:fotos/" + n);
            } catch (Exception ignored) { }
        }
        Collections.sort(out);
        return out;
    }

    /** Lê uma foto já reduzida para caber na tela sem pesar na memória. */
    public static Bitmap decode(Context ctx, String item, int maxSide) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            InputStream in = open(ctx, item);
            BitmapFactory.decodeStream(in, null, bounds);
            in.close();
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            in = open(ctx, item);
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            in.close();
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    /** Sincroniza com a pasta conteudo/fotos do GitHub. Rodar fora da thread principal. Retorna true se mudou. */
    public static boolean refreshFromInternet(Context ctx) {
        String api = Github.contentsApi("conteudo/fotos");
        if (api == null) return false;
        SharedPreferences p = ctx.getSharedPreferences("tv", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - p.getLong("photos_checked", 0) < REFRESH_EVERY_MS) return false;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(api).openConnection();
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("User-Agent", "TvVovo");
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            if (c.getResponseCode() != 200) return false;
            JSONArray arr = new JSONArray(Channel.readAll(c.getInputStream()));
            p.edit().putLong("photos_checked", now).apply();

            File dir = dir(ctx);
            Set<String> wanted = new HashSet<>();
            boolean changed = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String name = o.optString("name");
                if (!"file".equals(o.optString("type")) || !isImage(name)) continue;
                // nome local = identificador do conteúdo + nome original (detecta foto trocada)
                String local = o.optString("sha").substring(0, 10) + "_" + name;
                wanted.add(local);
                File f = new File(dir, local);
                if (f.exists()) continue;
                if (download(o.optString("download_url"), f)) changed = true;
            }
            File[] existing = dir.listFiles();
            if (existing != null) {
                for (File f : existing) {
                    if (!wanted.contains(f.getName())) { f.delete(); changed = true; }
                }
            }
            return changed;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean download(String url, File dest) {
        File tmp = new File(dest.getPath() + ".part");
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            InputStream in = c.getInputStream();
            FileOutputStream out = new FileOutputStream(tmp);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            in.close();
            return tmp.renameTo(dest);
        } catch (Exception e) {
            tmp.delete();
            return false;
        }
    }

    private static InputStream open(Context ctx, String item) throws Exception {
        if (item.startsWith("asset:")) return ctx.getAssets().open(item.substring(6));
        return new java.io.FileInputStream(item.substring(5));
    }

    private static File dir(Context ctx) {
        File d = new File(ctx.getFilesDir(), "fotos");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static boolean isImage(String n) {
        String l = n.toLowerCase();
        return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp");
    }
}
