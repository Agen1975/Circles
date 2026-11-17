package com.game.cosmicbubbles;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Play Billing. Product IDs must be created in Play Console with exactly these names.
 *
 * Note: purchases are verified on the device only. That is common for small games; a server
 * check can be added later if fraud becomes a problem.
 */
public final class Store {
    /** One-time, non-consumable: no interstitial ads, all themes, a starter pack of tokens. */
    public static final String PREMIUM = "premium";
    /** Consumable: 5 undos + 5 hints. */
    public static final String TOKENS_SMALL = "tokens_small";
    /** Consumable: 20 undos + 20 hints. */
    public static final String TOKENS_LARGE = "tokens_large";

    public interface Listener {
        /** Called on the main thread whenever products, prices or ownership change. */
        void onStoreChanged(String message);
    }

    private static Store instance;
    private final Context app;
    private final BillingClient client;
    private final Map<String, ProductDetails> products = new HashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private boolean ready;

    public static synchronized Store get(Context c) {
        if (instance == null) instance = new Store(c.getApplicationContext());
        return instance;
    }

    private Store(Context app) {
        this.app = app;
        client = BillingClient.newBuilder(app)
                .setListener(this::onPurchasesUpdated)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build();
        connect();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public boolean ready() {
        return ready && !products.isEmpty();
    }

    /** Localized price such as "$2.99", or null if not loaded. */
    public String price(String id) {
        ProductDetails d = products.get(id);
        if (d == null || d.getOneTimePurchaseOfferDetails() == null) return null;
        return d.getOneTimePurchaseOfferDetails().getFormattedPrice();
    }

    private void connect() {
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult r) {
                if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
                ready = true;
                queryProducts();
                restore();
            }

            @Override
            public void onBillingServiceDisconnected() {
                ready = false;
            }
        });
    }

    private void queryProducts() {
        List<QueryProductDetailsParams.Product> list = new ArrayList<>();
        for (String id : new String[]{PREMIUM, TOKENS_SMALL, TOKENS_LARGE}) {
            list.add(QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id).setProductType(BillingClient.ProductType.INAPP).build());
        }
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(list).build(),
                (r, result) -> {
                    if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
                    for (ProductDetails d : result.getProductDetailsList()) products.put(d.getProductId(), d);
                    notifyChanged(null);
                });
    }

    /** Re-applies Premium on reinstall or a new device, and finishes any interrupted purchases. */
    public void restore() {
        if (!ready) return;
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build(), (r, purchases) -> {
            if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                for (Purchase p : purchases) handle(p);
            }
        });
    }

    public boolean buy(Activity a, String id) {
        ProductDetails d = products.get(id);
        if (!ready || d == null) return false;
        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(
                        BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
                .build();
        client.launchBillingFlow(a, params);
        return true;
    }

    private void onPurchasesUpdated(BillingResult r, List<Purchase> purchases) {
        if (r.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase p : purchases) handle(p);
        } else if (r.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            restore();
        }
    }

    private void handle(Purchase p) {
        if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) {
            if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                notifyChanged("Payment pending. Your item arrives when it completes.");
            }
            return;
        }
        Progress progress = Progress.get(app);
        for (String id : p.getProducts()) {
            if (PREMIUM.equals(id)) {
                boolean first = !progress.premium();
                progress.setPremium(true);
                if (!p.isAcknowledged()) {
                    client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(p.getPurchaseToken()).build(), res -> { });
                }
                if (first) notifyChanged("Premium unlocked. Thank you!");
            } else {
                final int amount = TOKENS_LARGE.equals(id) ? 20 : 5;
                // Grant only after Play confirms the consume, so a token pack is never granted twice.
                client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(p.getPurchaseToken()).build(),
                        (res, token) -> {
                            if (res.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                progress.addUndo(amount);
                                progress.addHint(amount);
                                notifyChanged("+" + amount + " undos and +" + amount + " hints added");
                            }
                        });
            }
        }
    }

    private void notifyChanged(String message) {
        main.post(() -> {
            if (listener != null) listener.onStoreChanged(message);
        });
    }
}
