package com.vovo.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Um canal de TV e o carregamento da lista (GitHub -> cache -> lista embutida). */
public class Channel {
    public final String name;
    public final String ar;
    public final String url;
    public final String logo;

    Channel(String name, String ar, String url, String logo) {
        this.name = name; this.ar = ar; this.url = url; this.logo = logo;
    }

    private static List<Channel> cached;

    public static synchronized List<Channel> get(Context ctx) {
        if (cached == null) cached = loadLocal(ctx);
        return cached;
    }

    /** Lista disponível imediatamente: cópia salva da última atualização ou a lista embutida no app. */
    static List<Channel> loadLocal(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("tv", Context.MODE_PRIVATE);
        String saved = p.getString("channels_json", null);
        if (saved != null) {
            List<Channel> l = parse(saved);
            if (!l.isEmpty()) return l;
        }
        try {
            return parse(readAll(ctx.getAssets().open("channels.json")));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** Baixa a lista do GitHub (se configurado). Retorna true se mudou algo. Rodar fora da thread principal. */
    public static boolean refreshFromInternet(Context ctx) {
        String base = Github.rawBase();
        if (base == null) return false;
        String address = base + "conteudo/channels.json";
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            String json = readAll(c.getInputStream());
            List<Channel> l = parse(json);
            if (l.isEmpty()) return false;
            SharedPreferences p = ctx.getSharedPreferences("tv", Context.MODE_PRIVATE);
            if (json.equals(p.getString("channels_json", null))) return false;
            p.edit().putString("channels_json", json).apply();
            synchronized (Channel.class) { cached = l; }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static List<Channel> parse(String json) {
        List<Channel> out = new ArrayList<>();
        try {
            JSONArray a = new JSONObject(json).getJSONArray("channels");
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String url = o.optString("url", "");
                if (url.isEmpty()) continue;
                out.add(new Channel(o.optString("name", ""), o.optString("ar", ""), url, o.optString("logo", "")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    static String readAll(InputStream in) throws Exception {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally {
            in.close();
        }
    }
}
