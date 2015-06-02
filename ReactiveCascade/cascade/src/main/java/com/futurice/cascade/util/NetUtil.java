package com.futurice.cascade.util;

import android.app.Activity;
import android.content.Context;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.TelephonyManager;

import com.futurice.cascade.functional.ImmutableValue;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.internal.spdy.Header;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;

import static android.telephony.TelephonyManager.NETWORK_TYPE_1xRTT;
import static android.telephony.TelephonyManager.NETWORK_TYPE_CDMA;
import static android.telephony.TelephonyManager.NETWORK_TYPE_EDGE;
import static android.telephony.TelephonyManager.NETWORK_TYPE_EHRPD;
import static android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_0;
import static android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_A;
import static android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_B;
import static android.telephony.TelephonyManager.NETWORK_TYPE_GPRS;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSPA;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA;
import static android.telephony.TelephonyManager.NETWORK_TYPE_IDEN;
import static android.telephony.TelephonyManager.NETWORK_TYPE_LTE;
import static android.telephony.TelephonyManager.NETWORK_TYPE_UMTS;
import static android.telephony.TelephonyManager.NETWORK_TYPE_UNKNOWN;
import static com.futurice.cascade.Async.*;

/**
 * OkHttp convenience wrapper methods
 */
public final class NetUtil {
    final OkHttpClient client;
    final ImmutableValue<String> origin;
    private static final int MAX_NUMBER_OF_WIFI_NET_CONNECTIONS = 6;
    private static final int MAX_NUMBER_OF_3G_NET_CONNECTIONS = 4;
    private static final int MAX_NUMBER_OF_2G_NET_CONNECTIONS = 2;

    public enum NetType {NET_2G, NET_2_5G, NET_3G, NET_3_5G, NET_4G}

    private final TelephonyManager telephonyManager;
    private final WifiManager wifiManager;

    public NetUtil(final Context context) {
        origin = originAsync();
        client = new OkHttpClient();
        telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        wifiManager = (WifiManager) context.getSystemService(Activity.WIFI_SERVICE);
    }

    @NonNull
    public Response get(@NonNull final URL url) throws IOException {
        return get(url.toString(), null);
    }

    @NonNull
    public Response get(@NonNull final String url) throws IOException {
        return get(url, null);
    }

    @NonNull
    public Response get(
            @NonNull final URL url,
            @Nullable final Collection<Header> headers) throws IOException {
        return get(url.toString(), headers);
    }

    @NonNull
    public Response get(
            @NonNull final String url,
            @Nullable final Collection<Header> headers) throws IOException {
        dd(origin, "get " + url);
        final Call call = setupCall(url, builder -> addHeaders(builder, headers));

        return execute(call);
    }

    @NonNull
    public Response put(
            @NonNull final URL url,
            RequestBody body) throws IOException {
        return put(url.toString(), body);
    }

    @NonNull
    public Response put(
            @NonNull final String url,
            RequestBody body) throws IOException {
        return put(url, null, body);
    }

    @NonNull
    public Response put(
            @NonNull final URL url,
            @Nullable final Collection<Header> headers,
            @NonNull final RequestBody body) throws IOException {
        return put(url.toString(), headers, body);
    }

    @NonNull
    public Response put(
            @NonNull final String url,
            @Nullable final Collection<Header> headers,
            @NonNull final RequestBody body) throws IOException {
        dd(origin, "put " + url);
        final Call call = setupCall(url, builder -> {
            addHeaders(builder, headers);
            builder.put(body);
        });

        return execute(call);
    }

    @NonNull
    public Response post(
            @NonNull final String url,
            @NonNull final RequestBody body) throws IOException {
        return post(url, null, body);
    }

    @NonNull
    public Response post(
            @NonNull final URL url,
            @NonNull final RequestBody body) throws IOException {
        return post(url.toString(), null, body);
    }

    @NonNull
    public Response post(
            @NonNull final URL url,
            @Nullable final Collection<Header> headers,
            @NonNull final RequestBody body) throws IOException {
        return post(url.toString(), headers, body);
    }

    @NonNull
    public Response post(
            @NonNull final String url,
            @Nullable final Collection<Header> headers,
            @NonNull final RequestBody body) throws IOException {
        dd(origin, "post " + url);
        final Call call = setupCall(url, builder -> {
            addHeaders(builder, headers);
            builder.post(body);
        });

        return execute(call);
    }

    @NonNull
    public Response delete(@NonNull final URL url) throws IOException {
        return delete(url.toString(), null);
    }

    @NonNull
    public Response delete(@NonNull final String url) throws IOException {
        return delete(url, null);
    }

    @NonNull
    public Response delete(
            @NonNull final URL url,
            @Nullable final Collection<Header> headers) throws IOException {
        return delete(url.toString(), headers);
    }

    @NonNull
    public Response delete(
            @NonNull final String url,
            @Nullable final Collection<Header> headers) throws IOException {
        dd(origin, "remove " + url);
        final Call call = setupCall(url, builder -> {
            addHeaders(builder, headers);
            builder.delete();
        });

        return execute(call);
    }

    private void addHeaders(
            @NonNull final Request.Builder builder,
            @Nullable final Collection<Header> headers) {
        if (headers == null) {
            return;
        }

        for (Header header : headers) {
            builder.addHeader(header.name.toString(), header.value.toString());
        }
    }

    @NonNull
    private Call setupCall(
            @NonNull final String url,
            @Nullable final BuilderModifier builderModifier) throws IOException {
        final Request.Builder builder = new Request.Builder()
                .url(url);
        if (builderModifier != null) {
            builderModifier.modify(builder);
        }

        return client.newCall(builder.build());
    }

    @NonNull
    private Response execute(@NonNull final Call call) throws IOException {
        final Response response = call.execute();

        if (response.isRedirect()) {
            final String location = response.headers().get("Location");

            dd(origin, "Following HTTP redirect to " + location);
            return get(location);
        }
        if (!response.isSuccessful()) {
            final String s = "Unexpected response code " + response;
            final IOException e = new IOException(s);

            ee(origin, s, e);
            throw e;
        }

        return response;
    }

    /**
     * Functional interface to do something to an OkHttp request builder before dispatch
     */
    private interface BuilderModifier {
        void modify(Request.Builder builder);
    }

    //TODO Use max number of NET connections on startup split adapt as these change
    public int getMaxNumberOfNetConnections() {
        if (isWifi()) {
            return MAX_NUMBER_OF_WIFI_NET_CONNECTIONS;
        }

        switch (getNetworkType()) {
            case NET_2G:
            case NET_2_5G:
                return MAX_NUMBER_OF_2G_NET_CONNECTIONS;

            case NET_3G:
            case NET_3_5G:
            case NET_4G:
            default:
                return MAX_NUMBER_OF_3G_NET_CONNECTIONS;

        }
    }

    /**
     * Check if a current network WIFI connection is CONNECTED
     *
     * @return
     */
    public boolean isWifi() {
        SupplicantState s = wifiManager.getConnectionInfo().getSupplicantState();
        NetworkInfo.DetailedState state = WifiInfo.getDetailedStateOf(s);

        return state == NetworkInfo.DetailedState.CONNECTED;
    }

    @NonNull
    public NetType getNetworkType() {
        switch (telephonyManager.getNetworkType()) {
            case NETWORK_TYPE_UNKNOWN:
            case NETWORK_TYPE_CDMA:
            case NETWORK_TYPE_GPRS:
            case NETWORK_TYPE_IDEN:
                return NetType.NET_2_5G.NET_2G;

            case NETWORK_TYPE_EDGE:
                return NetType.NET_2_5G;

            case NETWORK_TYPE_UMTS:
            case NETWORK_TYPE_1xRTT:
            default:
                return NetType.NET_3G;

            case NETWORK_TYPE_EHRPD:
            case NETWORK_TYPE_EVDO_0:
            case NETWORK_TYPE_EVDO_A:
            case NETWORK_TYPE_EVDO_B:
            case NETWORK_TYPE_HSPA:
            case NETWORK_TYPE_HSPAP:
            case NETWORK_TYPE_HSUPA:
            case NETWORK_TYPE_HSDPA:
                return NetType.NET_3_5G;

            case NETWORK_TYPE_LTE:
                return NetType.NET_4G;
        }
    }
}