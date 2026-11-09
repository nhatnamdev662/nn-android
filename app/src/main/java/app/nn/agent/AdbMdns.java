package app.nn.agent;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;
import java.net.InetAddress;

/**
 * Discovers Wireless Debugging pairing (_adb-tls-pairing._tcp) and connect
 * (_adb-tls-connect._tcp) services via Android's Network Service Discovery (mDNS / NSD).
 */
public final class AdbMdns {
    private static final String TAG = "AdbMdns";

    public static final String SERVICE_TYPE_PAIRING = "_adb-tls-pairing._tcp.";
    public static final String SERVICE_TYPE_CONNECT = "_adb-tls-connect._tcp.";

    public interface Listener {
        void onPairingServiceDiscovered(String host, int port);
        void onConnectServiceDiscovered(String host, int port);
    }

    private final Context context;
    private final Listener listener;
    private final NsdManager nsdManager;

    private NsdManager.DiscoveryListener pairingListener;
    private NsdManager.DiscoveryListener connectListener;
    private boolean running = false;

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
                resolveService(serviceInfo, true);
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
                resolveService(serviceInfo, false);
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

    private void resolveService(NsdServiceInfo serviceInfo, boolean isPairing) {
        if (nsdManager == null) return;
        try {
            nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(NsdServiceInfo info, int errorCode) {
                    Log.w(TAG, "Resolve failed for " + info.getServiceName() + ": " + errorCode);
                }

                @Override
                public void onServiceResolved(NsdServiceInfo info) {
                    InetAddress host = info.getHost();
                    int port = info.getPort();
                    String hostAddr = (host != null) ? host.getHostAddress() : "127.0.0.1";
                    Log.d(TAG, "Service resolved: " + info.getServiceName() + " -> " + hostAddr + ":" + port);

                    if (listener != null) {
                        if (isPairing) {
                            listener.onPairingServiceDiscovered(hostAddr, port);
                        } else {
                            listener.onConnectServiceDiscovered(hostAddr, port);
                        }
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "Error invoking resolveService", t);
        }
    }
}
