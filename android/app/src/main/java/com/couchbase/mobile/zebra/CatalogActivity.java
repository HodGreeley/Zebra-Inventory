package com.couchbase.mobile.zebra;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;

import com.couchbase.lite.Dictionary;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static com.couchbase.mobile.zebra.ScanActivity.warning;

public class CatalogActivity extends AppCompatActivity {
    private DataManager dataManager;
    private ArrayList<Map<String, Object>> catalog;
    private Disposable disposable;
    private RecyclerView entriesView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_catalog);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        catalog = new ArrayList<>();

        entriesView = findViewById(R.id.entriesView);
        entriesView.setLayoutManager(new GridLayoutManager(this, 2));
        entriesView.setAdapter(new EntryAdapter(catalog));

        dataManager = new DataManager(getApplicationContext());
    }

    @Override
    protected void onResume() {
        super.onResume();

        disposable = dataManager.fromQuery(dataManager.createISBNQuery(null))
                .flatMapIterable(results -> results)
                .map(result -> {
                    Map<String, Object> info;

                    Dictionary dict = result.getDictionary(dataManager.getDatabase().getName());
                    info = dict.toMap();

                    InputStream is = dict.getBlob("cover").getContentStream();
                    Bitmap thumbnail = BitmapFactory.decodeStream(is);
                    info.put("thumbnail", thumbnail);

                    return info;
                })
                .defaultIfEmpty(warning("Missing"))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread()) // Switch to main thread before modifying data. See https://issuetracker.google.com/issues/37007605
                .map(entry -> {
                    catalog.add(entry);

                    return catalog.size() - 1;
                })
                .subscribe(entriesView.getAdapter()::notifyItemInserted, Throwable::printStackTrace);
    }

    @Override
    protected void onPause() {
        super.onPause();

        disposable.dispose();
        catalog.clear();
        entriesView.getAdapter().notifyDataSetChanged();
    }
}
