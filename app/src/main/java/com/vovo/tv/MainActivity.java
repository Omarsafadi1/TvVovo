package com.vovo.tv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Handler;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Tela inicial: grade de canais com ícones grandes. */
public class MainActivity extends Activity {
    private RecyclerView grid;
    private Adapter adapter;
    private int lastIndex = 0;

    // ---- fotos do vovô ----
    private ImageView photo, photoBlur;
    private List<String> photos;
    private int photoIndex = 0;
    private final Handler handler = new Handler();
    private static final long PHOTO_MS = 8000;
    private final Runnable nextPhoto = new Runnable() {
        @Override public void run() { showNextPhoto(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, 2));
        grid.setItemAnimator(null);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        photo = findViewById(R.id.photo);
        photoBlur = findViewById(R.id.photoBlur);
        findViewById(R.id.photoFrame).setClipToOutline(true);
    }

    private void startSlideshow() {
        photos = Photos.list(this);
        handler.removeCallbacks(nextPhoto);
        if (photos.isEmpty()) return;
        photoIndex = photoIndex % photos.size();
        showNextPhoto();
    }

    private void showNextPhoto() {
        if (photos == null || photos.isEmpty()) return;
        final String item = photos.get(photoIndex % photos.size());
        photoIndex = (photoIndex + 1) % photos.size();
        new Thread(new Runnable() {
            @Override public void run() {
                final Bitmap b = Photos.decode(MainActivity.this, item, 1280);
                final Bitmap blur = b == null ? null : Bitmap.createScaledBitmap(b, 24,
                        Math.max(1, 24 * b.getHeight() / Math.max(1, b.getWidth())), true);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        if (b != null) {
                            // troca suave: some, troca a foto, aparece
                            photo.animate().alpha(0f).setDuration(400).withEndAction(new Runnable() {
                                @Override public void run() {
                                    photo.setImageBitmap(b);
                                    photoBlur.setImageBitmap(blur);
                                    photo.animate().alpha(1f).setDuration(600).start();
                                }
                            }).start();
                        }
                        handler.removeCallbacks(nextPhoto);
                        handler.postDelayed(nextPhoto, PHOTO_MS);
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(nextPhoto);
    }

    @Override
    protected void onResume() {
        super.onResume();
        lastIndex = getSharedPreferences("tv", MODE_PRIVATE).getInt("last", 0);
        adapter.setChannels(Channel.get(this));
        focusTile(lastIndex);
        startSlideshow();
        // atualiza canais e fotos pelo GitHub em segundo plano
        new Thread(new Runnable() {
            @Override public void run() {
                if (Photos.refreshFromInternet(MainActivity.this)) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { startSlideshow(); }
                    });
                }
                if (Channel.refreshFromInternet(MainActivity.this)) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            adapter.setChannels(Channel.get(MainActivity.this));
                            focusTile(lastIndex);
                        }
                    });
                }
            }
        }).start();
    }

    private void focusTile(final int index) {
        if (adapter.getItemCount() == 0) return;
        final int pos = Math.max(0, Math.min(index, adapter.getItemCount() - 1));
        grid.scrollToPosition(pos);
        grid.post(new Runnable() {
            @Override public void run() {
                RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(pos);
                if (h != null) h.itemView.requestFocus();
            }
        });
    }

    /** Como é a tela inicial da caixa, o botão Voltar não deve sair dela. */
    @Override
    public void onBackPressed() { }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openSettings();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void openChannel(int index) {
        getSharedPreferences("tv", MODE_PRIVATE).edit().putInt("last", index).apply();
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("index", index);
        startActivity(i);
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
    }

    class Adapter extends RecyclerView.Adapter<Holder> {
        private List<Channel> channels;

        void setChannels(List<Channel> list) {
            channels = list;
            notifyDataSetChanged();
        }

        @Override public int getItemCount() {
            // +1 = botão de configurações no fim da grade
            return channels == null ? 0 : channels.size() + 1;
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.tile, parent, false);
            return new Holder(v);
        }

        @Override public void onBindViewHolder(final Holder h, int position) {
            final int pos = h.getAdapterPosition();
            if (pos >= channels.size()) {
                h.number.setText("");
                h.ar.setText("الإعدادات");
                h.name.setText("Configurações");
                h.logo.setTag(null);
                h.logo.setImageResource(R.drawable.ic_settings);
                h.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { openSettings(); }
                });
                return;
            }
            Channel c = channels.get(pos);
            h.number.setText(String.valueOf(pos + 1));
            h.ar.setText(c.ar.isEmpty() ? c.name : c.ar);
            h.name.setText(c.name);
            LogoLoader.load(MainActivity.this, c.logo, h.logo);
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openChannel(h.getAdapterPosition()); }
            });
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView logo;
        final TextView ar, name, number;

        Holder(View v) {
            super(v);
            logo = v.findViewById(R.id.logo);
            ar = v.findViewById(R.id.ar);
            name = v.findViewById(R.id.name);
            number = v.findViewById(R.id.number);
            v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View view, boolean has) {
                    float s = has ? 1.08f : 1f;
                    view.animate().scaleX(s).scaleY(s).setDuration(120).start();
                }
            });
        }
    }
}
