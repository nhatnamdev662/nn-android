package app.nn.agent;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Listener> listeners = new ArrayList<>();

    private AdbMdns mdns;
    private boolean mdnsActive = false;

    private AdbManager(Context context) {
        this.context = context.getApplicationContext();
        port = Prefs.adbPort(this.context);
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
            sInstance.disconnectInternal();
        }
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
        if (!mdnsActive) {
            mdns = new AdbMdns(context, new AdbMdns.Listener() {
                @Override
                public void onPairingServiceDiscovered(String host, int p) {
                    detectedPairingPort = p;
                    notifyPairingDiscovered(host, p);
                    AdbPairingService.showNotification(context, p);
                }

                @Override
                public void onConnectServiceDiscovered(String host, int p) {
                    detectedConnectPort = p;
                    if (state != State.CONNECTED && state != State.CONNECTING) {
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

    public static boolean isConnected() {
        return state == State.CONNECTED && AdbConnection.isConnected();
    }

    private synchronized void stopInternal() {
        if (mdnsActive && mdns != null) {
            mdns.stop();
            mdnsActive = false;
        }
        AdbPairingService.cancelNotification(context);
        disconnectInternal();
    }

    private void connectInternal(int targetPort) {
        if (targetPort <= 0 || targetPort > 65535) {
            updateState(State.DISCONNECTED, "Invalid port: " + targetPort, 0);
            return;
        }

        updateState(State.CONNECTING, "", targetPort);
        executor.execute(() -> {
            try {
                AdbConnection.connect("127.0.0.1", targetPort);
                port = targetPort;
                Prefs.setAdbPort(context, targetPort);
                Prefs.setAdbEnabled(context, true);
                updateState(State.CONNECTED, "", targetPort);

                // AxManager-style UID 2000 shell daemon presence
                try {
                    AdbConnection.execute("nohup sh -c 'while true; do sleep 3600; done' >/dev/null 2>&1 &", 3000);
                } catch (Throwable ignored) {}
            } catch (Throwable t) {
                Log.w(TAG, "Connection failed to 127.0.0.1:" + targetPort, t);
                String msg = t.getMessage();
                String friendly;
                if (msg != null && (msg.contains("ECONNREFUSED") || msg.contains("refused"))) {
                    friendly = "Cổng " + targetPort + " không phản hồi. Hãy kiểm tra Gỡ lỗi không dây đã bật chưa hoặc cập nhật số cổng.";
                } else if (msg != null && msg.contains("timeout")) {
                    friendly = "Hết thời gian chờ kết nối tới cổng " + targetPort + ".";
                } else {
                    friendly = "Chưa kết nối tới cổng " + targetPort + ".";
                }
                updateState(State.DISCONNECTED, friendly, targetPort);
                if (detectedConnectPort > 0 && detectedConnectPort != targetPort) {
                    mainHandler.postDelayed(() -> connectInternal(detectedConnectPort), 500);
                }
            }
        });
    }

    private void disconnectInternal() {
        executor.execute(() -> {
            try {
                AdbConnection.disconnect();
            } catch (Throwable ignored) {}
            updateState(State.DISCONNECTED, "", 0);
        });
    }

    private void pairInternal(int pairingPort, String pairingCode, PairCallback callback) {
        updateState(State.PAIRING, "Pairing with 127.0.0.1:" + pairingPort, pairingPort);
        executor.execute(() -> {
            AdbPairingClient client = null;
            try {
                client = new AdbPairingClient("127.0.0.1", pairingPort, pairingCode);
                client.pair();
                updateState(State.PAIRED, "Pairing successful", pairingPort);

                mainHandler.post(() -> {
                    if (callback != null) callback.onResult(true, null);
                });

                // Auto-connect after successful pairing if connection port is known
                int connectPort = detectedConnectPort > 0 ? detectedConnectPort : Prefs.adbPort(context);
                if (connectPort > 0) {
                    connectInternal(connectPort);
                }

            } catch (Throwable t) {
                Log.e(TAG, "Pairing failed", t);
                String err = t.getMessage() != null ? t.getMessage() : "Pairing failed";
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
