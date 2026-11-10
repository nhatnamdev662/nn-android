package app.nn.agent;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Discovers Wireless Debugging pairing (_adb-tls-pairing._tcp) and connect
 * (_adb-tls-connect._tcp) services via Android's Network Service Discovery (mDNS / NSD).
 * Uses a sequential queue for resolve requests to prevent FAILURE_ALREADY_ACTIVE (3).
 */
public final class AdbMdns {
    private static final String TAG = "AdbMdns";

    public static final String SERVICE_TYPE_PAIRING = "_adb-tls-pairing._tcp.";
    public static final String SERVICE_TYPE_CONNECT = "_adb-tls-connect._tcp.";

    public interface Listener {
        void onPairingServiceDiscovered(String host, int port);
        void onConnectServiceDiscovered(String host, int port);
    }

    private static class ResolveRequest {
        final NsdServiceInfo info;
        final boolean isPairing;
        int retries = 0;

        ResolveRequest(NsdServiceInfo info, boolean isPairing) {
            this.info = info;
            this.isPairing = isPairing;
        }
    }

    private final Context context;
    private final Listener listener;
    private final NsdManager nsdManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Queue<ResolveRequest> resolveQueue = new ArrayDeque<>();

    private NsdManager.DiscoveryListener pairingListener;
    private NsdManager.DiscoveryListener connectListener;
    private boolean running = false;
    private boolean isResolving = false;

    public AdbMdns(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.nsdManager = (NsdManager) this.context.getSystemService(Context.NSD_SERVICE);
    }

    public synchronized void start() {
        if (running || nsdManager == null) return;
        running = true;

        pairingListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.w(TAG, "Pairing discovery start failed: " + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.w(TAG, "Pairing discovery stop failed: " + errorCode);
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.d(TAG, "Pairing discovery started");
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "Pairing discovery stopped");
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                enqueueResolve(serviceInfo, true);
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Pairing service lost: " + serviceInfo.getServiceName());
            }
        };

        connectListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.w(TAG, "Connect discovery start failed: " + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.w(TAG, "Connect discovery stop failed: " + errorCode);
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.d(TAG, "Connect discovery started");
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "Connect discovery stopped");
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                enqueueResolve(serviceInfo, false);
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Connect service lost: " + serviceInfo.getServiceName());
            }
        };

        try {
            nsdManager.discoverServices(SERVICE_TYPE_PAIRING, NsdManager.PROTOCOL_DNS_SD, pairingListener);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to start pairing service discovery", t);
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE_CONNECT, NsdManager.PROTOCOL_DNS_SD, connectListener);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to start connect service discovery", t);
        }
    }

    public synchronized void stop() {
        if (!running || nsdManager == null) return;
        running = false;
        resolveQueue.clear();
        isResolving = false;

        if (pairingListener != null) {
            try {
                nsdManager.stopServiceDiscovery(pairingListener);
            } catch (Throwable ignored) {}
            pairingListener = null;
        }

        if (connectListener != null) {
            try {
                nsdManager.stopServiceDiscovery(connectListener);
            } catch (Throwable ignored) {}
            connectListener = null;
        }
    }

    public synchronized void restart() {
        stop();
        handler.postDelayed(this::start, 350);
    }

    private synchronized void enqueueResolve(NsdServiceInfo serviceInfo, boolean isPairing) {
        resolveQueue.add(new ResolveRequest(serviceInfo, isPairing));
        processNextResolve();
    }

    private synchronized void processNextResolve() {
        if (isResolving || resolveQueue.isEmpty() || !running || nsdManager == null) return;
        final ResolveRequest req = resolveQueue.poll();
        if (req == null) return;
        isResolving = true;

        try {
            nsdManager.resolveService(req.info, new NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(NsdServiceInfo info, int errorCode) {
                    Log.w(TAG, "Resolve failed for " + info.getServiceName() + ": " + errorCode);
                    synchronized (AdbMdns.this) {
                        isResolving = false;
                        if (errorCode == 3 && req.retries < 3 && running) { // FAILURE_ALREADY_ACTIVE
                            req.retries++;
                            handler.postDelayed(() -> {
                                synchronized (AdbMdns.this) {
                                    resolveQueue.add(req);
                                    processNextResolve();
                                }
                            }, 300);
                            return;
                        }
                        processNextResolve();
                    }
                }

                @Override
                public void onServiceResolved(NsdServiceInfo info) {
                    InetAddress host = info.getHost();
                    int port = info.getPort();
                    String hostAddr = (host != null) ? host.getHostAddress() : "127.0.0.1";
                    Log.d(TAG, "Service resolved: " + info.getServiceName() + " -> " + hostAddr + ":" + port);

                    if (listener != null) {
                        if (req.isPairing) {
                            listener.onPairingServiceDiscovered(hostAddr, port);
                        } else {
                            listener.onConnectServiceDiscovered(hostAddr, port);
                        }
                    }
                    synchronized (AdbMdns.this) {
                        isResolving = false;
                        processNextResolve();
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "Error invoking resolveService", t);
            synchronized (AdbMdns.this) {
                isResolving = false;
                processNextResolve();
            }
        }
    }
}
