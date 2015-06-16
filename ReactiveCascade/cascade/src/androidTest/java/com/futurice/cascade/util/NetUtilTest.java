package com.futurice.cascade.util;

import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;

import com.futurice.cascade.AsyncAndroidTestCase;
import com.futurice.cascade.i.functional.IAltFuture;
import com.futurice.cascade.reactive.ReactiveValue;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.internal.spdy.Header;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collection;

import static com.futurice.cascade.Async.WORKER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the NetUtil class
 *
 * Created by phou on 6/2/2015.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class NetUtilTest extends AsyncAndroidTestCase {

    public NetUtilTest() {
        super();
    }

    public void setUp() throws Exception {
        super.setUp();

        setDefaultTimeoutMillis(15000); // Give real net traffic enough time to complete
    }

    @Test
    public void testGet() throws Exception {
        assertThat(getNetUtil().get("http://httpbin.org/").body().bytes().length).isGreaterThan(100);
    }

    @Test
    public void testGetWithHeaders() throws Exception {
        Collection<Header> headers = new ArrayList<>();
        headers.add(new Header("Test", "ValueZ"));
        assertThat(getNetUtil().get("http://httpbin.org/headers", headers).body().string()).contains("ValueZ");
    }

    @Test
    public void testGetFromIGettable() throws Exception {
        ReactiveValue<String> value = new ReactiveValue<>("RV Test", "http://httpbin.org/headers");
        assertThat(getNetUtil().get(value).body().bytes().length).isGreaterThan(20);
    }

    @Test
    public void testGetFromIGettableWithHeaders() throws Exception {
        ReactiveValue<String> value = new ReactiveValue<>("RV Test", "http://httpbin.org/headers");
        Collection<Header> headers = new ArrayList<>();
        headers.add(new Header("Test", "ValueG"));
        assertThat(getNetUtil().get(value, headers).body().string()).contains("ValueG");
    }

    @Test
    public void testGetAsync() throws Exception {
        IAltFuture<?, Response> iaf = getNetUtil().getAsync("http://httpbin.org/get")
                .fork();
        assertThat(awaitDone(iaf).isSuccessful()).isTrue();
    }

    @Test
    public void testGetAsyncFrom() throws Exception {
        IAltFuture<?, Response> iaf = WORKER
                .from("http://httpbin.org/get")
                .then(getNetUtil().getAsync())
                .fork();
        assertThat(awaitDone(iaf).isSuccessful()).isTrue();
    }

    @Test
    public void testGetAsync2() throws Exception {
        Collection<Header> headers = new ArrayList<>();
        headers.add(new Header("Test", "Value"));
        assertThat(getNetUtil().get("http://httpbin.org/", headers).body().bytes().length).isGreaterThan(100);

    }

    @Test
    public void testPutAsync() throws Exception {

    }

    @Test
    public void testPutAsync1() throws Exception {

    }

    @Test
    public void testPutAsync2() throws Exception {

    }

    @Test
    public void testPut() throws Exception {

    }

    @Test
    public void testPutAsync3() throws Exception {

    }

    @Test
    public void testPutAsync4() throws Exception {

    }

    @Test
    public void testPutAsync5() throws Exception {

    }

    @Test
    public void testPut1() throws Exception {

    }

    @Test
    public void testPost() throws Exception {

    }

    @Test
    public void testPostAsync() throws Exception {

    }

    @Test
    public void testPostAsync1() throws Exception {

    }

    @Test
    public void testPostAsync2() throws Exception {

    }

    @Test
    public void testPost1() throws Exception {

    }

    @Test
    public void testPostAsync3() throws Exception {

    }

    @Test
    public void testPostAsync4() throws Exception {

    }

    @Test
    public void testPostAsync5() throws Exception {

    }

    @Test
    public void testPost2() throws Exception {

    }

    @Test
    public void testDeleteAsync() throws Exception {

    }

    @Test
    public void testDeleteAsync1() throws Exception {

    }

    @Test
    public void testDelete() throws Exception {

    }

    @Test
    public void testDeleteAsync2() throws Exception {

    }

    @Test
    public void testDeleteAsync3() throws Exception {

    }

    @Test
    public void testDelete1() throws Exception {

    }

    @Test
    public void testGetMaxNumberOfNetConnections() throws Exception {

    }

    @Test
    public void testIsWifi() throws Exception {

    }

    @Test
    public void testGetNetworkType() throws Exception {

    }
}