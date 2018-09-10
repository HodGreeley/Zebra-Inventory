package com.couchbase.mobile.zebra;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.couchbase.lite.BasicAuthenticator;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.DataSource;
import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseConfiguration;
import com.couchbase.lite.Dictionary;
import com.couchbase.lite.Endpoint;
import com.couchbase.lite.Expression;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryBuilder;
import com.couchbase.lite.Replicator;
import com.couchbase.lite.ReplicatorConfiguration;
import com.couchbase.lite.Result;
import com.couchbase.lite.SelectResult;
import com.couchbase.lite.URLEndpoint;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import static android.graphics.PorterDuff.Mode.DST;
import static android.graphics.PorterDuff.Mode.DST_OVER;

public class ScanActivity extends AppCompatActivity {
    private static final String TAG = ScanActivity.class.getName();

    private static final String DATAWEDGE_ACTION = "com.symbol.datawedge.api.ACTION";
    private static final String DATAWEDGE_SOFT_SCAN_TRIGGER = "com.symbol.datawedge.api.SOFT_SCAN_TRIGGER";
    private static final String DATAWEDGE_TOGGLE_SCANNING = "TOGGLE_SCANNING";
    private static final String ISBN_LABEL_TYPE = "LABEL-TYPE-EAN13";

    private static final String DATAWEDGE_INTENT_ACTION = "com.zebra.inventory.ACTION";
    private static final String DATAWEDGE_INTENT_LABEL_TYPE = "com.symbol.datawedge.label_type";
    private static final String DATAWEDGE_INTENT_DATA = "com.symbol.datawedge.data_string";

    private static final int THUMBNAIL_WIDTH = 245;
    private static final int THUMBNAIL_HEIGHT = 400;

    private Database database;

    private ImageView thumbnail;
    private TextView title;
    private TextView author;
    private TextView isbn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        thumbnail = findViewById(R.id.thumbnail);
        title = findViewById(R.id.title);
        author = findViewById(R.id.author);
        isbn = findViewById(R.id.isbn);

        thumbnail.setMinimumWidth(THUMBNAIL_WIDTH);
        thumbnail.setMinimumHeight(THUMBNAIL_HEIGHT);
        thumbnail.setBackgroundColor(Color.DKGRAY);

        Button softScanButton = findViewById(R.id.buttonDWSoftScan);

        try {
            DatabaseConfiguration config = new DatabaseConfiguration(getApplicationContext());
            database = new Database("inventory", config);

            Endpoint targetEndpoint = new URLEndpoint(new URI("ws://localhost:4984/inventory"));
            ReplicatorConfiguration repConfig = new ReplicatorConfiguration(database, targetEndpoint)
                    .setReplicatorType(ReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL)
                    .setAuthenticator(new BasicAuthenticator("user", "password"))
                    .setContinuous(true);
            Replicator replicator = new Replicator(repConfig);
            replicator.start();
        } catch(CouchbaseLiteException | URISyntaxException ex) {
            ex.printStackTrace();
        }

        softScanButton.setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setAction(DATAWEDGE_ACTION);
            intent.putExtra(DATAWEDGE_SOFT_SCAN_TRIGGER, DATAWEDGE_TOGGLE_SCANNING);
            sendBroadcast(intent);
        });
    }

    @SuppressLint("CheckResult")
    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();

        if (!(DATAWEDGE_INTENT_ACTION.equals(intent.getAction())
            && ISBN_LABEL_TYPE.equals(intent.getStringExtra(DATAWEDGE_INTENT_LABEL_TYPE)))) {
            thumbnail.setColorFilter(0, PorterDuff.Mode.CLEAR);
            return;
        }

        String isbn = intent.getStringExtra(DATAWEDGE_INTENT_DATA);

        Observable.create((ObservableOnSubscribe<Map<String, Object>>)source -> {
            List<Result> results = null;
            Map<String, Object> info;

            Query query = QueryBuilder
                    .select(SelectResult.all())
                    .from(DataSource.database(database))
                    .where(Expression.property("isbn").equalTo(Expression.string(isbn)));

            try {
                results = query.execute().allResults();
            } catch(CouchbaseLiteException ex) {
                source.onError((ex));
            }

            if (0 == results.size()) {
                info = warning("Missing");
            } else if (1 < results.size()) {
                info = warning("Duplicate");
            } else {
                Dictionary result = results.get(0).getDictionary(database.getName());
                info = result.toMap();

                InputStream is = result.getBlob("cover").getContentStream();
                Bitmap thumbnail = BitmapFactory.decodeStream(is);
                info.put("thumbnail", thumbnail);
            }

            source.onNext(info);
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::display, Throwable::printStackTrace);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    private Map<String, Object> warning(String warning) {
        Map<String, Object> map = new HashMap<>();

        map.put("title", warning);
        map.put("author", warning);
        map.put("isbn", warning);

        map.put("thumbnail", bitmapFromString(warning));

        return map;
    }

    private void display(Map<String, Object>info) {
        thumbnail.setColorFilter(0, DST);
        thumbnail.setImageBitmap((Bitmap)info.get("thumbnail"));
        title.setText((String)info.get("title"));
        author.setText((String)info.get("author"));
        isbn.setText((String)info.get("isbn"));
    }

    // Ref: https://stackoverflow.com/questions/11120392/android-center-text-on-canvas
    private static final int WARNING_FOREGROUND = 0xffff8000;
    private static final int WARNING_BACKGROUND = Color.DKGRAY;
    private static final float WARNING_SIZE = 38f;

    private Bitmap bitmapFromString(String text) {
        Bitmap bitmap = Bitmap.createBitmap(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        Rect rect = new Rect();

        paint.setAntiAlias(true);
        paint.setTextSize(WARNING_SIZE);
        paint.setColor(WARNING_FOREGROUND);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.getTextBounds(text, 0, text.length(), rect);
        float x = (THUMBNAIL_WIDTH - rect.width()) / 2f - rect.left;
        float y = (THUMBNAIL_HEIGHT + rect.height()) / 2f - rect.bottom;
        canvas.drawText(text, x, y, paint);

        canvas.drawColor(WARNING_BACKGROUND, DST_OVER);

        return bitmap;
    }
}