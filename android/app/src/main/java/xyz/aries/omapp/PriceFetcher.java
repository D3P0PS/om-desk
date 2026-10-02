package xyz.aries.omapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Last prices from the venues' public REST APIs, one request per venue per check.
 * Symbols a venue does not return are simply absent from the result (the caller
 * reports them); network and parse errors are raised.
 */
public final class PriceFetcher {
    private PriceFetcher() {}

    public static Map<String, Double> fetch(String venue, Set<String> symbols) throws Exception {
        Map<String, Double> out = new HashMap<>();
        switch (venue) {
            case "BINANCE": {
                JSONArray q = new JSONArray();
                for (String s : symbols) q.put(s);
                String url = "https://api.binance.com/api/v3/ticker/price?symbols="
                        + URLEncoder.encode(q.toString(), "UTF-8");
                JSONArray arr = new JSONArray(get(url));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    out.put(o.getString("symbol"), Double.parseDouble(o.getString("price")));
                }
                break;
            }
            case "BINANCE_FUTURES": {
                JSONArray arr = new JSONArray(get("https://fapi.binance.com/fapi/v1/ticker/price"));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String s = o.getString("symbol");
                    if (symbols.contains(s)) out.put(s, Double.parseDouble(o.getString("price")));
                }
                break;
            }
            case "BYBIT": {
                for (String s : symbols) {
                    JSONObject o = new JSONObject(get("https://api.bybit.com/v5/market/tickers?category=linear&symbol="
                            + URLEncoder.encode(s, "UTF-8")));
                    JSONArray list = o.getJSONObject("result").optJSONArray("list");
                    if (list != null && list.length() > 0) {
                        out.put(s, Double.parseDouble(list.getJSONObject(0).getString("lastPrice")));
                    }
                }
                break;
            }
            case "HYPERLIQUID": {
                JSONObject mids = new JSONObject(post("https://api.hyperliquid.xyz/info", "{\"type\":\"allMids\"}"));
                for (String s : symbols) {
                    if (mids.has(s)) out.put(s, Double.parseDouble(mids.getString(s)));
                }
                break;
            }
            default:
                throw new IllegalArgumentException("unknown venue " + venue);
        }
        return out;
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(15_000);
        return read(c, url);
    }

    private static String post(String url, String json) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(15_000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("content-type", "application/json");
        try (OutputStream os = c.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return read(c, url);
    }

    private static String read(HttpURLConnection c, String url) throws IOException {
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException(new URL(url).getHost() + " HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) buf.write(b, 0, n);
                return buf.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }
}
