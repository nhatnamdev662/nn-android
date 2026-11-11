package app.nn.agent;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Central state coordinator and service manager for on-device Wireless Debugging (ADB).
 * Coordinates AdbMdns discovery, AdbConnection loopback client, and AdbPairingClient.
 */
public final class AdbManager {
    private static final String TAG = "AdbManager";

    public enum State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        PAIRING,
        PAIRED,
        ERROR
    }

    public interface Listener {
        void onAdbStateChanged(State state, String host, int port, String error);
        void onAdbPairingDiscovered(String host, int port);
    }

    public interface PairCallback {
        void onResult(boolean success, String error);
    }

    private static volatile AdbManager sInstance;
    private static final Object LOCK = new Object();

    public static volatile State state = State.DISCONNECTED;
    public static volatile int port = 0;
    public static volatile int detectedPairingPort = 0;
    public static volatile int detectedConnectPort = 0;
    public static volatile String detail = "";
    private static volatile boolean justPaired = false;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Listener> listeners = new ArrayList<>();

    private AdbMdns mdns;
    private boolean mdnsActive = false;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private volatile boolean isWifiConnected = false;
    private volatile boolean networkCallbackRegistered = false;

    private static final long HEARTBEAT_INTERVAL_MS = 10000;
    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (state == State.CONNECTED && isWifiConnected) {
                executor.execute(() -> {
                    try {
                        AdbConnection.ExecutionResult res = AdbConnection.execute("echo 1", 2000);
                        if (res.exitCode != 0) {
                            throw new java.io.IOException("Heartbeat failed (exitCode=" + res.exitCode + ")");
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "ADB Heartbeat probe failed: " + t.getMessage() + ". Disconnecting and re-discovering...");
                        AdbConnection.disconnect();
                        String errText = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                        updateState(State.DISCONNECTED, context.getString(R.string.setup_adb_err_lost, errText), port);
                        if (isWifiConnected && Prefs.adbEnabled(context)) {
                            restartDiscoveryInternal();
                        }
                    }
                });
            }
            if (state == State.CONNECTED && isWifiConnected) {
                mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
            }
        }
    };

    private void startHeartbeat() {
        stopHeartbeat();
        mainHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS);
    }

    private void stopHeartbeat() {
        mainHandler.removeCallbacks(heartbeatRunnable);
    }

    public static boolean isWifiAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (Network net : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return true;
                }
            }
            Network active = cm.getActiveNetwork();
            if (active == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } else {
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI;
        }
    }

    private synchronized void registerNetworkCallback() {
        if (networkCallbackRegistered) return;
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;
        try {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    Log.d(TAG, "Wi-Fi network became available");
                    isWifiConnected = true;
                    onWifiAvailable();
                }

                @Override
                public void onLost(Network network) {
                    if (isWifiAvailable(context)) {
                        Log.d(TAG, "Previous Wi-Fi network lost during AP/band switch; Wi-Fi remains active");
                        isWifiConnected = true;
                        return;
                    }
                    Log.d(TAG, "Wi-Fi network lost (switched to 4G/Cellular or offline)");
                    isWifiConnected = false;
                    onWifiLost();
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                    boolean wifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                    if (wifi && !isWifiConnected) {
                        isWifiConnected = true;
                        onWifiAvailable();
                    } else if (!wifi && isWifiConnected) {
                        isWifiConnected = false;
                        onWifiLost();
                    }
                }
            };
            connectivityManager.registerNetworkCallback(request, networkCallback);
            networkCallbackRegistered = true;
        } catch (Throwable t) {
            Log.w(TAG, "Failed to register network callback", t);
        }
    }

    private synchronized void onWifiLost() {
        Log.i(TAG, "Wi-Fi disconnected. Disconnecting ADB and pausing mDNS.");
        stopHeartbeat();
        if (mdnsActive && mdns != null) {
            mdns.stop();
            mdnsActive = false;
        }
        AdbPairingService.cancelNotification(context);
        executor.execute(() -> {
            try {
                AdbConnection.disconnect();
            } catch (Throwable ignored) {}
            updateState(State.DISCONNECTED, context.getString(R.string.setup_adb_status_wifi_required), 0);
        });
    }

    private synchronized void onWifiAvailable() {
        Log.i(TAG, "Wi-Fi restored. Auto-reconnecting ADB if enabled.");
        if (Prefs.adbEnabled(context)) {
            startInternal();
        }
    }

    private AdbManager(Context context) {
        this.context = context.getApplicationContext();
        port = Prefs.adbPort(this.context);
        isWifiConnected = isWifiAvailable(this.context);
        registerNetworkCallback();
        AdbConnection.setDisconnectListener(reason -> {
            Log.w(TAG, "AdbConnection dropped: " + reason);
            if (state == State.CONNECTED) {
                String msg = reason != null ? reason : context.getString(R.string.setup_adb_status_disconnected);
                updateState(State.DISCONNECTED, context.getString(R.string.setup_adb_err_lost, msg), port);
                if (isWifiConnected && Prefs.adbEnabled(this.context)) {
                    restartDiscoveryInternal();
                }
            }
        });
    }

    public static AdbManager get(Context context) {
        if (sInstance == null) {
            synchronized (LOCK) {
                if (sInstance == null) {
                    sInstance = new AdbManager(context);
                }
            }
        }
        return sInstance;
    }

    public static AdbManager get() {
        if (sInstance == null) {
            throw new IllegalStateException("AdbManager not initialized with Context");
        }
        return sInstance;
    }

    public static void ensure(Context c) {
        AdbManager mgr = get(c);
        mgr.startInternal();
    }

    public static boolean isDiscovering() {
        return sInstance != null && sInstance.mdnsActive;
    }

    public static void restartDiscovery() {
        if (sInstance != null) {
            sInstance.restartDiscoveryInternal();
        }
    }

    public static void stop() {
        if (sInstance != null) {
            sInstance.stopInternal();
        }
    }

    public static void connect(int portToConnect) {
        if (sInstance != null) {
            sInstance.connectInternal(portToConnect);
        }
    }

    public static void disconnect() {
        if (sInstance != null) {
            sInstance.stopInternal();
        }
    }

    public static void reconnect(Context c) {
        AdbManager mgr = get(c);
        Prefs.setAdbEnabled(mgr.context, true);
        mgr.reconnectInternal();
    }

    private synchronized void reconnectInternal() {
        stopHeartbeat();
        isWifiConnected = isWifiAvailable(context);
        if (!isWifiConnected) {
            updateState(State.DISCONNECTED, context.getString(R.string.setup_adb_status_wifi_required), 0);
            return;
        }
        executor.execute(() -> {
            try {
                AdbConnection.disconnect();
            } catch (Throwable ignored) {}
            mainHandler.post(() -> {
                synchronized (this) {
                    if (mdns != null) {
                        mdns.restart();
                        mdnsActive = true;
                    }
                    startInternal();
                }
            });
        });
    }

    public static void pair(int pairingPort, String pairingCode, PairCallback callback) {
        if (sInstance != null) {
            sInstance.pairInternal(pairingPort, pairingCode, callback);
        } else if (callback != null) {
            callback.onResult(false, "AdbManager not initialized");
        }
    }

    public static void addListener(Listener l) {
        if (sInstance != null && l != null) {
            synchronized (sInstance.listeners) {
                if (!sInstance.listeners.contains(l)) {
                    sInstance.listeners.add(l);
                }
            }
        }
    }

    public static void removeListener(Listener l) {
        if (sInstance != null && l != null) {
            synchronized (sInstance.listeners) {
                sInstance.listeners.remove(l);
            }
        }
    }

    private synchronized void startInternal() {
        registerNetworkCallback();
        isWifiConnected = isWifiAvailable(context);
        if (!isWifiConnected) {
            updateState(State.DISCONNECTED, context.getString(R.string.setup_adb_status_wifi_required), 0);
            return;
        }

        if (!mdnsActive) {
            mdns = new AdbMdns(context, new AdbMdns.Listener() {
                @Override
                public void onPairingServiceDiscovered(String host, int p) {
                    detectedPairingPort = p;
                    if (!isWifiConnected) return;
                    notifyPairingDiscovered(host, p);
                    AdbPairingService.showNotification(context, p);
                }

                @Override
                public void onConnectServiceDiscovered(String host, int p) {
                    detectedConnectPort = p;
                    if (!isWifiConnected) return;
                    if (state != State.CONNECTED && state != State.CONNECTING) {
                        connectInternal(p);
                    } else if (state == State.CONNECTED && p != port) {
                        Log.i(TAG, "Wireless Debugging port changed from " + port + " to " + p + ". Reconnecting...");
                        connectInternal(p);
                    }
                }
            });
            mdns.start();
            mdnsActive = true;
        }

        int savedPort = Prefs.adbPort(context);
        int targetPort = detectedConnectPort > 0 ? detectedConnectPort : savedPort;
        if (targetPort > 0 && state != State.CONNECTED && state != State.CONNECTING) {
            connectInternal(targetPort);
        }
    }

    private synchronized void restartDiscoveryInternal() {
        isWifiConnected = isWifiAvailable(context);
        if (!isWifiConnected) {
            onWifiLost();
            return;
        }
        if (mdns != null) {
            mdns.restart();
            mdnsActive = true;
        } else {
            startInternal();
        }
        int savedPort = Prefs.adbPort(context);
        int targetPort = detectedConnectPort > 0 ? detectedConnectPort : savedPort;
        if (targetPort > 0 && state != State.CONNECTED && state != State.CONNECTING) {
            connectInternal(targetPort);
        }
    }

    public static boolean isConnected() {
        return state == State.CONNECTED && AdbConnection.isConnected();
    }

    /**
     * Triggers an automatic reconnect through {@link AdbManager} and waits briefly for the loopback session
     * to become ready, avoiding concurrent {@link AdbConnection#connect} races from background tool calls.
     * Safe to invoke from worker threads only (returns current connection state if called on the main thread).
     */
    public static boolean ensureConnectedBlocking(Context c, long timeoutMs) {
        if (isConnected()) return true;
        if (c == null || !Prefs.adbEnabled(c) || !isWifiAvailable(c)) return false;
        if (Looper.myLooper() == Looper.getMainLooper()) return false;

        final CountDownLatch latch = new CountDownLatch(1);
        final Listener listener = new Listener() {
            @Override
            public void onAdbStateChanged(State s, String host, int p, String error) {
                if (s == State.CONNECTED) {
                    latch.countDown();
                }
            }

            @Override
            public void onAdbPairingDiscovered(String host, int p) {
            }
        };

        ensure(c);
        addListener(listener);
        try {
            if (isConnected()) return true;
            latch.await(Math.max(200L, timeoutMs), TimeUnit.MILLISECONDS);
            return isConnected();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return isConnected();
        } finally {
            removeListener(listener);
        }
    }

    private synchronized void stopInternal() {
        stopHeartbeat();
        if (mdnsActive && mdns != null) {
            mdns.stop();
            mdnsActive = false;
        }
        AdbPairingService.cancelNotification(context);
        disconnectInternal();
    }

    private void connectInternal(int targetPort) {
        if (!isWifiConnected) {
            updateState(State.DISCONNECTED, context.getString(R.string.setup_adb_status_wifi_required), 0);
            return;
        }
        if (targetPort <= 0 || targetPort > 65535) {
            updateState(State.DISCONNECTED, context.getString(R.string.setup_adb_err_invalid_port, targetPort), 0);
            return;
        }

        updateState(State.CONNECTING, "", targetPort);
        executor.execute(() -> {
            if (!isWifiConnected) {
                updateState(State.DISCONNECTED, context.getString(R.string.setup_adb_status_wifi_required), 0);
                return;
            }
            try {
                AdbConnection.connect("127.0.0.1", targetPort);
                port = targetPort;
                Prefs.setAdbPort(context, targetPort);
                Prefs.setAdbEnabled(context, true);
                updateState(State.CONNECTED, "", targetPort);
                startHeartbeat();

                // Display success notification only after an active user pairing
                if (justPaired) {
                    justPaired = false;
                    AdbPairingService.showSuccessNotification(context, targetPort);
                }
            } catch (Throwable t) {
                stopHeartbeat();
                Log.w(TAG, "Connection failed to 127.0.0.1:" + targetPort, t);
                String msg = t.getMessage();
                String friendly;
                if (!isWifiConnected) {
                    friendly = context.getString(R.string.setup_adb_status_wifi_required);
                } else if (msg != null && (msg.contains("ECONNREFUSED") || msg.contains("refused"))) {
                    friendly = context.getString(R.string.setup_adb_err_refused, targetPort);
                } else if (msg != null && msg.contains("timeout")) {
                    friendly = context.getString(R.string.setup_adb_err_timeout, targetPort);
                } else {
                    friendly = msg != null ? msg : context.getString(R.string.setup_adb_err_failed, targetPort);
                }
                updateState(State.DISCONNECTED, friendly, targetPort);

                if (targetPort == detectedConnectPort) {
                    detectedConnectPort = 0;
                }

                if (isWifiConnected) {
                    if (detectedConnectPort > 0 && detectedConnectPort != targetPort) {
                        mainHandler.postDelayed(() -> connectInternal(detectedConnectPort), 500);
                    } else if (targetPort != 5555 && state != State.CONNECTED && state != State.CONNECTING) {
                        // Fallback: try standard loopback ADB port 5555
                        mainHandler.postDelayed(() -> connectInternal(5555), 300);
                    }
                }
            }
        });
    }

    private void disconnectInternal() {
        stopHeartbeat();
        executor.execute(() -> {
            try {
                AdbConnection.disconnect();
            } catch (Throwable ignored) {}
            updateState(State.DISCONNECTED, "", 0);
        });
    }

    private void pairInternal(int pairingPort, String pairingCode, PairCallback callback) {
        updateState(State.PAIRING, context.getString(R.string.setup_adb_pairing_in_progress, pairingPort), pairingPort);
        executor.execute(() -> {
            AdbPairingClient client = null;
            try {
                client = new AdbPairingClient("127.0.0.1", pairingPort, pairingCode);
                client.pair();
                justPaired = true;
                updateState(State.PAIRED, context.getString(R.string.setup_adb_paired), pairingPort);

                mainHandler.post(() -> {
                    if (callback != null) callback.onResult(true, null);
                });

                // Clear searching notification now that pairing succeeded
                AdbPairingService.cancelNotification(context);

                // Restart mDNS to pick up the connect service announcement fresh
                if (mdns != null) {
                    mdns.restart();
                }

                // Poll for connect port
                pollForConnectPort(0);

            } catch (Throwable t) {
                Log.e(TAG, "Pairing failed", t);
                String err = t.getMessage() != null ? t.getMessage() : context.getString(R.string.setup_adb_pair_failed_generic);
                updateState(State.ERROR, err, pairingPort);

                mainHandler.post(() -> {
                    if (callback != null) callback.onResult(false, err);
                });
            } finally {
                if (client != null) {
                    try { client.close(); } catch (Throwable ignored) {}
                }
            }
        });
    }

    private void pollForConnectPort(int attempt) {
        int connectPort = detectedConnectPort > 0 ? detectedConnectPort : Prefs.adbPort(context);
        if (connectPort > 0) {
            connectInternal(connectPort);
            return;
        }
        if (attempt >= 12) {
            Log.d(TAG, "No connect port detected via mDNS after pairing");
            return;
        }
        mainHandler.postDelayed(() -> {
            if (state == State.PAIRED || state == State.CONNECTING) {
                pollForConnectPort(attempt + 1);
            }
        }, 500);
    }

    private void updateState(State newState, String newDetail, int newPort) {
        state = newState;
        detail = newDetail != null ? newDetail : "";
        if (newPort > 0) {
            port = newPort;
        }

        mainHandler.post(() -> {
            List<Listener> copy;
            synchronized (listeners) {
                copy = new ArrayList<>(listeners);
            }
            for (Listener l : copy) {
                try {
                    l.onAdbStateChanged(newState, "127.0.0.1", port, detail);
                } catch (Throwable t) {
                    Log.w(TAG, "Listener error", t);
                }
            }
        });
    }

    private void notifyPairingDiscovered(String host, int p) {
        mainHandler.post(() -> {
            List<Listener> copy;
            synchronized (listeners) {
                copy = new ArrayList<>(listeners);
            }
            for (Listener l : copy) {
                try {
                    l.onAdbPairingDiscovered(host, p);
                } catch (Throwable t) {
                    Log.w(TAG, "Listener error", t);
                }
            }
        });
    }
}
