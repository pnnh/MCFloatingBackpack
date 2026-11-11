// 开发用的假 Mojang 会话服务器：让"对局域网开放"的集成服务器（强制正版验证）能接受没有正版账号的开发客户端。
// 用法：java scripts/ai-gui/MockSession.java [端口]    客户端与房主都加
//   -Dminecraft.api.env=custom -Dminecraft.api.auth.host=http://主机:端口 -Dminecraft.api.account.host=… -Dminecraft.api.session.host=… -Dminecraft.api.services.host=…
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public class MockSession {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            System.out.println(exchange.getRequestMethod() + " " + path + (query == null ? "" : "?" + query));
            byte[] body = new byte[0];
            int status = 204;
            if (path.endsWith("/hasJoined")) {
                String name = "Dev";
                if (query != null) for (String kv : query.split("&")) if (kv.startsWith("username=")) name = kv.substring(9);
                byte[] h;
                try { h = MessageDigest.getInstance("MD5").digest(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)); } catch (java.security.NoSuchAlgorithmException e) { throw new RuntimeException(e); }
                StringBuilder sb = new StringBuilder();
                for (byte b : h) sb.append(String.format("%02x", b));
                String json = "{\"id\":\"" + sb + "\",\"name\":\"" + name + "\",\"properties\":[]}";
                body = json.getBytes(StandardCharsets.UTF_8);
                status = 200;
                exchange.getResponseHeaders().add("Content-Type", "application/json");
            } else if (!path.endsWith("/join")) {
                status = 404;
            }
            exchange.sendResponseHeaders(status, status == 204 ? -1 : body.length);
            if (body.length > 0) exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        System.out.println("mock session server on " + port);
    }
}
