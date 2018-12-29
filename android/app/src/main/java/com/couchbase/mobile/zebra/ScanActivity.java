package com.couchbase.mobile.zebra;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.ImageView;
import android.widget.TextView;

import com.couchbase.lite.Dictionary;
import com.couchbase.lite.ResultSet;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static android.graphics.PorterDuff.Mode.DST;
import static android.graphics.PorterDuff.Mode.DST_OVER;

public class ScanActivity extends AppCompatActivity {
    private static final String TAG = ScanActivity.class.getSimpleName();

    private static final String DATAWEDGE_ACTION = "com.symbol.datawedge.api.ACTION";
    private static final String DATAWEDGE_SOFT_SCAN_TRIGGER = "com.symbol.datawedge.api.SOFT_SCAN_TRIGGER";
    private static final String DATAWEDGE_TOGGLE_SCANNING = "TOGGLE_SCANNING";
    private static final String ISBN_LABEL_TYPE = "LABEL-TYPE-EAN13";

    private static final String DATAWEDGE_INTENT_ACTION = "com.zebra.inventory.ACTION";
    private static final String DATAWEDGE_INTENT_LABEL_TYPE = "com.symbol.datawedge.label_type";
    private static final String DATAWEDGE_INTENT_DATA = "com.symbol.datawedge.data_string";

    private static final int THUMBNAIL_WIDTH = 245;
    private static final int THUMBNAIL_HEIGHT = 400;

    private DataManager dataManager;
    private Disposable disp = null;

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

        findViewById(R.id.buttonDWSoftScan).setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setAction(DATAWEDGE_ACTION);
            intent.putExtra(DATAWEDGE_SOFT_SCAN_TRIGGER, DATAWEDGE_TOGGLE_SCANNING);
            sendBroadcast(intent);
        });

        findViewById(R.id.buttonP2PSync).setOnClickListener(view -> dataManager.startPeerReplication());

        findViewById(R.id.buttonCatalog).setOnClickListener(view -> openCatalog());

        dataManager = new DataManager(getApplicationContext());

        dataManager.startServerReplication();
        dataManager.initializePeerToPeer(getApplicationContext());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
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

        this.isbn.setText(isbn);

        disp = dataManager.fromQuery(dataManager.createISBNQuery(isbn))
                .map(ResultSet::allResults)
                .map(results -> {
                    Map<String, Object> info;

                    if (0 == results.size()) { return warning("Missing"); }
                    if (1 < results.size()) { return warning("Duplicate"); }

                    Dictionary result = results.get(0).getDictionary(dataManager.getDatabase().getName());
                    info = result.toMap();

                    InputStream is = result.getBlob("cover").getContentStream();
                    Bitmap thumbnail = BitmapFactory.decodeStream(is);
                    info.put("thumbnail", thumbnail);

                    return info;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::display, Throwable::printStackTrace);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (null != disp) disp.dispose();
    }

    private static final String[] permissions = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    @Override
    public void onStart() {
        super.onStart();

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void openCatalog() {
        Intent intent = new Intent(this, CatalogActivity.class);
        startActivity(intent);
    }

    public static Map<String, Object> warning(String warning) {
        Map<String, Object> map = new HashMap<>();

        map.put("title", warning);
        map.put("author", warning);
        map.put("thumbnail", bitmapFromString(warning));

        return map;
    }

    private void display(Map<String, Object>info) {
        thumbnail.setColorFilter(0, DST);
        thumbnail.setImageBitmap((Bitmap)info.get("thumbnail"));
        title.setText((String)info.get("title"));
        author.setText((String)info.get("author"));
    }

    // Ref: https://stackoverflow.com/questions/11120392/android-center-text-on-canvas
    private static final int WARNING_FOREGROUND = 0xffff8000;
    private static final int WARNING_BACKGROUND = Color.DKGRAY;
    private static final float WARNING_SIZE = 38f;

    private static Bitmap bitmapFromString(String text) {
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