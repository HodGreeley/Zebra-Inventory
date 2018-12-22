package com.couchbase.mobile.zebra;

import android.graphics.Bitmap;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;
import java.util.Map;

import static android.graphics.PorterDuff.Mode.DST;

public class EntryAdapter extends RecyclerView.Adapter<EntryAdapter.EntryViewHolder> {
    private List<Map<String, Object>> entries = null;

    public EntryAdapter(List<Map<String, Object>> entries) {
        this.entries = entries;
    }

    @Override
    public EntryViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false);
        return new EntryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(EntryViewHolder holder, int position) {
        Map<String, Object> entry = entries.get(position);

        holder.cover.setColorFilter(0, DST);
        holder.cover.setImageBitmap((Bitmap)entry.get("thumbnail"));
        holder.title.setText((String)entry.get("title"));
        holder.author.setText((String)entry.get("author"));
        holder.isbn.setText((String)entry.get("isbn"));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class EntryViewHolder extends RecyclerView.ViewHolder {
        private ImageView cover;
        private TextView title;
        private TextView author;
        private TextView isbn;

        EntryViewHolder(View view) {
            super(view);

            cover = view.findViewById(R.id.cat_thumbnail);
            title = view.findViewById(R.id.cat_title);
            author = view.findViewById(R.id.cat_author);
            isbn = view.findViewById(R.id.cat_isbn);
        }
    }
}
