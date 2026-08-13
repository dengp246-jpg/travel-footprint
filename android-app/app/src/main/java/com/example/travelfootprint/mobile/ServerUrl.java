package com.example.travelfootprint.mobile;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class ServerUrl {

    private ServerUrl() {
    }

    static String normalize(String rawValue, boolean allowHttp) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("请输入服务器地址");
        if (!value.contains("://")) value = "https://" + value;

        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("https") && !(allowHttp && scheme.equals("http"))) {
                throw new IllegalArgumentException(allowHttp ? "地址必须使用 HTTP 或 HTTPS" : "正式版仅允许 HTTPS 地址");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("服务器地址缺少有效域名或 IP");
            }
            if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("服务器地址不能包含账号、查询参数或锚点");
            }
            String normalized = uri.toString();
            while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
            return normalized;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("服务器地址格式不正确");
        }
    }
}
