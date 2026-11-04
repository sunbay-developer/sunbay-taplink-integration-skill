package example.taplink;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.sunmi.tapro.taplink.sdk.TaplinkSDK;
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListenerAdapter;
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback;
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallbackAdapter;
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig;
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig;
import com.sunmi.tapro.taplink.sdk.enums.AppToAppMode;
import com.sunmi.tapro.taplink.sdk.enums.CableProtocol;
import com.sunmi.tapro.taplink.sdk.enums.CrossDeviceStrategy;
import com.sunmi.tapro.taplink.sdk.error.ConnectionError;
import com.sunmi.tapro.taplink.sdk.error.PaymentError;
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo;
import com.sunmi.tapro.taplink.sdk.model.common.PaymentCategory;
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent;
import com.sunmi.tapro.taplink.sdk.model.common.PaymentMethodInfo;
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest;
import com.sunmi.tapro.taplink.sdk.model.request.transaction.SaleRequest;
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Java-only public API example for SDK 1.1.0. One app-owned payment session.
 * Reuse the existing durable order store behind Journal; serialize submissions
 * and verify terminal identity in the app before sale/query. Keep io alive while
 * late result writes are pending. This adapter never replays a monetary operation.
 */
public final class TaplinkQuickStartJava implements AutoCloseable {
    public interface Journal {
        /** Atomic durable reservation: UUID, order, amount/currency and selected terminal.
         * Return false for a duplicate/unresolved order; throw on persistence failure. */
        boolean reserve(SaleRequest request);
        /** Idempotent final outcome handling; processing/unknown remain pending. */
        void recordResult(String originalRequestId, PaymentResult result);
        /** Retain the original attempt for reconciliation. */
        void recordError(String originalRequestId, PaymentError error);
    }

    public enum State {
        CONNECTED, DISCONNECTED, CONNECTION_ERROR, RECONNECTING,
        PROGRESS, APPROVED, DECLINED, UNRESOLVED, BLOCKED, STORAGE_ERROR
    }

    public interface Observer {
        /** Main thread. Detail is a typed SDK result/error/event, reason, or storage exception.
         * Do not log the full payload. CONNECTED still requires terminal identity verification. */
        void onUpdate(State state, String originalRequestId, Object detail);
    }

    private final Journal journal;
    private final Executor io;
    private final Observer observer;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean storageBlocked = new AtomicBoolean(false);

    public TaplinkQuickStartJava(Journal journal, Executor io, Observer observer) {
        this.journal = Objects.requireNonNull(journal);
        this.io = Objects.requireNonNull(io);
        this.observer = Objects.requireNonNull(observer);
    }

    /** Call once from the existing Application after local provisioning. False keeps UI gated. */
    public static boolean initialize(Context context, String appId, String secretKey, String merchantId) {
        if (blank(appId) || blank(secretKey)) return false;
        if (!TaplinkSDK.isSDKInitialized()) {
            TaplinkConfig config = TaplinkConfig.create(appId, secretKey)
                    .setTimeout(180).setLogEnabled(false);
            if (!blank(merchantId)) config = config.setMerchantId(merchantId);
            TaplinkSDK.initSDK(context.getApplicationContext(), config);
        }
        return true;
    }

    public static ConnectionConfig onDevice() {
        return ConnectionConfig.Companion.createOnDeviceMode(AppToAppMode.CUSTOM);
    }

    public static ConnectionConfig lan(String host, int port) {
        return ConnectionConfig.Companion.createCrossDeviceMode(CrossDeviceStrategy.LAN, host, port, null);
    }

    public static ConnectionConfig cable(CableProtocol protocol) {
        return ConnectionConfig.Companion.createCrossDeviceMode(
                CrossDeviceStrategy.CABLE, null, null, Objects.requireNonNull(protocol));
    }

    public static ConnectionConfig subScreen() {
        return ConnectionConfig.Companion.createSubScreenMode();
    }

    public void connect(ConnectionConfig config) {
        ensureOpen();
        if (!TaplinkSDK.isSDKInitialized()) throw new IllegalStateException("Initialize SDK first");
        TaplinkSDK.connectSDK(config, new ConnectionListenerAdapter() {
            @Override public void onConnected(String deviceId, String taproVersion) {
                emit(State.CONNECTED, null, null); // Read getTerminalInfo() before enabling payment.
            }
            @Override public void onDisconnected(String reason) { emit(State.DISCONNECTED, null, reason); }
            @Override public void onError(ConnectionError error) { emit(State.CONNECTION_ERROR, null, error); }
            @Override public void onReconnecting(int attempt, int maxAttempts) {
                emit(State.RECONNECTING, null, attempt);
            }
        });
    }

    public static SaleRequest saleRequest(String orderId, long amountCents, String currency) {
        return new SaleRequest.Builder()
                .setReferenceOrderId(orderId)
                .setTransactionRequestId(UUID.randomUUID().toString())
                .setAmount(AmountInfo.of(amountCents, currency))
                .setPaymentMethod(new PaymentMethodInfo(PaymentCategory.CARD, null, null))
                .build();
    }

    /** Call on the app's IO dispatcher after identity verification. */
    public void sale(SaleRequest request) {
        ensureConnected();
        String id = request.getTransactionRequestId();
        if (storageBlocked.get()) { emit(State.BLOCKED, id, null); return; }
        try {
            if (!journal.reserve(request)) { emit(State.BLOCKED, id, null); return; }
        } catch (RuntimeException error) {
            storageFailure(id, error);
            return; // Persistence failed: nothing is sent.
        }
        // An exception after reservation leaves the attempt available for reconciliation.
        TaplinkSDK.getClient().sale(request, callback(id));
    }

    /** One query; app verifies saved terminal identity and bounds polling. */
    public void query(String originalRequestId) {
        ensureConnected();
        if (blank(originalRequestId)) throw new IllegalArgumentException("Original request ID required");
        TaplinkSDK.getClient().query(
                new QueryRequest().setTransactionRequestId(originalRequestId), callback(originalRequestId));
    }

    private PaymentCallback callback(final String originalId) {
        return new PaymentCallbackAdapter() {
            @Override public void onSuccess(PaymentResult result) {
                io.execute(() -> {
                    try {
                        journal.recordResult(originalId, result);
                        State state = result.isSuccess() ? State.APPROVED
                                : result.isFailed() ? State.DECLINED
                                : result.isProcessing() ? State.UNRESOLVED : State.UNRESOLVED;
                        emit(state, originalId, result);
                    } catch (RuntimeException error) { storageFailure(originalId, error); }
                });
            }
            @Override public void onFailure(PaymentError error) {
                io.execute(() -> {
                    try {
                        journal.recordError(originalId, error);
                        // Includes 306/308 and query errors. Inspect cause; do not unlock checkout.
                        emit(State.UNRESOLVED, originalId, error);
                    } catch (RuntimeException storageError) { storageFailure(originalId, storageError); }
                });
            }
            @Override public void onProgress(PaymentEvent event) { emit(State.PROGRESS, originalId, event); }
        };
    }

    private void storageFailure(String id, RuntimeException error) {
        storageBlocked.set(true);
        emit(State.STORAGE_ERROR, id, error);
        // Repair storage, inspect durable attempts, then recreate the owner. Never auto-clear this gate.
    }

    private void emit(State state, String id, Object detail) {
        main.post(() -> { if (!closed.get()) observer.onUpdate(state, id, detail); });
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("Payment session is closed");
    }

    private void ensureConnected() {
        ensureOpen();
        if (!TaplinkSDK.isSDKConnected()) throw new IllegalStateException("Connect to the original terminal first");
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            try { TaplinkSDK.disconnectSDK(); }
            finally { TaplinkSDK.removeSDKConnectionListener(); }
        }
    }
}
