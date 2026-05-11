package com.personal.ftpserver;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.*;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    // UI
    private Button btnStartStop;
    private TextView tvStatus, tvIP, tvPort, tvSpeed, tvConnections, tvLog;
    private EditText etUsername, etPassword, etPort;
    private ImageView ivQR;
    private Switch switchAnon;
    private ScrollView scrollLog;

    private boolean serverRunning = false;
    private StringBuilder logBuilder = new StringBuilder();
    private static final int PERM_REQ = 100;

    // ─── BroadcastReceiver: listens to FTPService updates ───
    private BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra("status");
            String extra  = intent.getStringExtra("extra");
            long bytes    = intent.getLongExtra("bytes", 0);
            double speed  = intent.getDoubleExtra("speed", 0);

            runOnUiThread(() -> handleStatus(status, extra, bytes, speed));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        loadSettings();
        setupListeners();
        requestPermissions();
    }

    private void bindViews() {
        btnStartStop  = findViewById(R.id.btn_start_stop);
        tvStatus      = findViewById(R.id.tv_status);
        tvIP          = findViewById(R.id.tv_ip);
        tvPort        = findViewById(R.id.tv_port);
        tvSpeed       = findViewById(R.id.tv_speed);
        tvConnections = findViewById(R.id.tv_connections);
        tvLog         = findViewById(R.id.tv_log);
        scrollLog     = findViewById(R.id.scroll_log);
        etUsername    = findViewById(R.id.et_username);
        etPassword    = findViewById(R.id.et_password);
        etPort        = findViewById(R.id.et_port);
        ivQR          = findViewById(R.id.iv_qr);
        switchAnon    = findViewById(R.id.switch_anon);
    }

    private void setupListeners() {
        btnStartStop.setOnClickListener(v -> {
            if (serverRunning) stopServer();
            else startServer();
        });

        switchAnon.setOnCheckedChangeListener((btn, checked) -> {
            etUsername.setEnabled(!checked);
            etPassword.setEnabled(!checked);
        });

        // Copy IP to clipboard on tap
        tvIP.setOnClickListener(v -> {
            String ip = tvIP.getText().toString();
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("FTP Address", ip));
            Toast.makeText(this, "کاپی ہوگیا!", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadSettings() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        etUsername.setText(prefs.getString("username", "admin"));
        etPassword.setText(prefs.getString("password", "1234"));
        etPort.setText(String.valueOf(prefs.getInt("port", FTPServer.DEFAULT_PORT)));
        switchAnon.setChecked(prefs.getBoolean("anonymous", false));
        etUsername.setEnabled(!switchAnon.isChecked());
        etPassword.setEnabled(!switchAnon.isChecked());
    }

    private void saveSettings() {
        SharedPreferences.Editor ed = getPreferences(MODE_PRIVATE).edit();
        ed.putString("username", etUsername.getText().toString());
        ed.putString("password", etPassword.getText().toString());
        ed.putInt("port",        getPort());
        ed.putBoolean("anonymous", switchAnon.isChecked());
        ed.apply();
    }

    private void startServer() {
        if (!isWifiConnected()) {
            Toast.makeText(this, "WiFi آن کریں پہلے!", Toast.LENGTH_LONG).show();
            return;
        }

        saveSettings();

        String username = switchAnon.isChecked() ? "" : etUsername.getText().toString().trim();
        String password = switchAnon.isChecked() ? "" : etPassword.getText().toString().trim();
        int    port     = getPort();
        String rootPath = getRootPath();

        Intent intent = new Intent(this, FTPService.class);
        intent.setAction(FTPService.ACTION_START);
        intent.putExtra("rootPath",  rootPath);
        intent.putExtra("username",  username);
        intent.putExtra("password",  password);
        intent.putExtra("port",      port);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(intent);
        else
            startService(intent);

        String ip = getWifiIP();
        String ftpUrl = "ftp://" + ip + ":" + port;

        tvIP.setText(ftpUrl);
        tvPort.setText("Port: " + port);
        generateQRCode(ftpUrl);
        addLog("▶ سرور شروع ہوا: " + ftpUrl);
    }

    private void stopServer() {
        Intent intent = new Intent(this, FTPService.class);
        intent.setAction(FTPService.ACTION_STOP);
        startService(intent);
    }

    private void handleStatus(String status, String extra, long bytes, double speed) {
        switch (status) {
            case "started":
                serverRunning = true;
                btnStartStop.setText("🛑 سرور بند کریں");
                btnStartStop.setBackgroundColor(Color.parseColor("#E53935"));
                tvStatus.setText("● چل رہا ہے");
                tvStatus.setTextColor(Color.parseColor("#43A047"));
                ivQR.setVisibility(View.VISIBLE);
                break;

            case "stopped":
                serverRunning = false;
                btnStartStop.setText("▶ سرور شروع کریں");
                btnStartStop.setBackgroundColor(Color.parseColor("#1976D2"));
                tvStatus.setText("● بند ہے");
                tvStatus.setTextColor(Color.parseColor("#E53935"));
                tvSpeed.setText("0.0 MB/s");
                tvConnections.setText("0 متصل");
                ivQR.setVisibility(View.GONE);
                addLog("■ سرور بند ہوا");
                break;

            case "connected":
                addLog("✔ نیا کنیکشن: " + extra);
                break;

            case "disconnected":
                addLog("✘ کنیکشن ختم: " + extra);
                break;

            case "upload":
                addLog("⬆ اپلوڈ: " + extra + " (" + formatSize(bytes) + ")");
                break;

            case "download":
                addLog("⬇ ڈاؤنلوڈ: " + extra + " (" + formatSize(bytes) + ")");
                break;

            case "speed":
                tvSpeed.setText(String.format(Locale.US, "%.1f MB/s", speed));
                break;

            case "error":
                addLog("⚠ خرابی: " + extra);
                Toast.makeText(this, "خرابی: " + extra, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void addLog(String message) {
        String time = new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(new Date());
        logBuilder.insert(0, "[" + time + "] " + message + "\n");

        // Keep last 50 lines
        String[] lines = logBuilder.toString().split("\n");
        if (lines.length > 50) {
            logBuilder = new StringBuilder();
            for (int i = 0; i < 50; i++) logBuilder.append(lines[i]).append("\n");
        }

        tvLog.setText(logBuilder.toString());
    }

    // ─── QR Code Generator ───
    private void generateQRCode(String text) {
        try {
            int size = 300;
            Bitmap bitmap = generateQR(text, size, size);
            ivQR.setImageBitmap(bitmap);
        } catch (Exception e) {
            ivQR.setVisibility(View.GONE);
        }
    }

    // Simple QR code using ZXing if available, fallback to text
    private Bitmap generateQR(String text, int width, int height) {
        // Uses ZXing BarcodeEncoder
        try {
            Class<?> encoderClass  = Class.forName("com.google.zxing.BarcodeFormat");
            Object qrFormat = java.lang.reflect.Array.get(
                    encoderClass.getMethod("values").invoke(null), 11); // QR_CODE index

            Class<?> writerClass = Class.forName("com.google.zxing.MultiFormatWriter");
            Object writer = writerClass.newInstance();
            Object bitMatrix = writerClass.getMethod("encode", String.class,
                    encoderClass, int.class, int.class)
                    .invoke(writer, text, qrFormat, width, height);

            Class<?> encUtils = Class.forName("com.journeyapps.barcodescanner.BarcodeEncoder");
            Object encoder = encUtils.newInstance();
            return (Bitmap) encUtils.getMethod("createBitmap", bitMatrix.getClass())
                    .invoke(encoder, bitMatrix);

        } catch (Exception e) {
            // Fallback: draw text QR placeholder
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            canvas.drawColor(Color.WHITE);
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setTextSize(14);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("QR: " + text, width / 2f, height / 2f, paint);
            return bmp;
        }
    }

    // ─── Network helpers ───
    private String getWifiIP() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wm == null) return "127.0.0.1";
        int ip = wm.getConnectionInfo().getIpAddress();
        return String.format(Locale.US, "%d.%d.%d.%d",
                ip & 0xff, (ip >> 8) & 0xff, (ip >> 16) & 0xff, (ip >> 24) & 0xff);
    }

    private boolean isWifiConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected() &&
                ni.getType() == ConnectivityManager.TYPE_WIFI;
    }

    private int getPort() {
        try {
            return Integer.parseInt(etPort.getText().toString().trim());
        } catch (NumberFormatException e) {
            return FTPServer.DEFAULT_PORT;
        }
    }

    private String getRootPath() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.getExternalStorageDirectory().getAbsolutePath();
        }
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
        return String.format(Locale.US, "%.2f GB", bytes / 1073741824.0);
    }

    // ─── Permissions ───
    private void requestPermissions() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!perms.isEmpty())
            ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), PERM_REQ);
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        boolean allGranted = true;
        for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) allGranted = false;
        if (!allGranted)
            Toast.makeText(this, "اجازت ضروری ہے!", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(FTPService.BROADCAST_STATUS);
        registerReceiver(statusReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
    }
}
