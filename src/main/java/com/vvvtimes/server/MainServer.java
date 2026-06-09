package com.vvvtimes.server;

import com.vvvtimes.JrebelUtil.JrebelSign;
import com.vvvtimes.util.rsasign;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;


public class MainServer extends AbstractHandler {

    private static final Logger logger = LoggerFactory.getLogger(MainServer.class);
    
    /**
     * 配置文件
     */
    private static final Properties CONFIG = new Properties();
    
    /**
     * 配置参数
     */
    private static String SERVER_VERSION;
    private static String SERVER_PROTOCOL_VERSION;
    private static String SERVER_GUID;
    private static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";
    private static final String CONTENT_TYPE_HTML = "text/html; charset=utf-8";
    private static String COMPANY_ADMIN;
    private static final String GROUP_TYPE_MANAGED = "managed";
    private static final String SEAT_POOL_TYPE_STANDALONE = "standalone";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final int LICENSE_TYPE = 1;
    private static final int ID = 1;
    private static long ONLINE_LICENSE_DAYS;
    private static long OFFLINE_LICENSE_DAYS;
    private static final long ONE_DAY_MILLIS = 24L * 60 * 60 * 1000;
    private static String PROLONGATION_PERIOD;
    
    /**
     * 生成JRebel签名(JrebelSign单例)
     */
    private static final JrebelSign JREBEL_SIGN = new JrebelSign();

    /**
     * 加载配置文件
     */
    private static void loadConfig() {
        try (InputStream input = new FileInputStream("CONFIG.properties")) {
            CONFIG.load(input);
            logger.info("配置文件加载成功");
        } catch (IOException e) {
            logger.warn("未找到配置文件，使用默认配置");
            // 设置默认值
            CONFIG.setProperty("server.version", "3.2.4");
            CONFIG.setProperty("server.protocolVersion", "1.1");
            CONFIG.setProperty("server.guid", "a1b4aea8-b031-4302-b602-670a990272cb");
            CONFIG.setProperty("company.admin", "Administrator");
            CONFIG.setProperty("license.onlineDays", "3650");
            CONFIG.setProperty("license.offlineDays", "180");
            CONFIG.setProperty("prolongation.period", "607875500");
        }
        
        // 初始化配置参数
        SERVER_VERSION = CONFIG.getProperty("server.version", "3.2.4");
        SERVER_PROTOCOL_VERSION = CONFIG.getProperty("server.protocolVersion", "1.1");
        SERVER_GUID = CONFIG.getProperty("server.guid", "a1b4aea8-b031-4302-b602-670a990272cb");
        COMPANY_ADMIN = CONFIG.getProperty("company.admin", "Administrator");
        ONLINE_LICENSE_DAYS = Long.parseLong(CONFIG.getProperty("license.onlineDays", "3650"));
        OFFLINE_LICENSE_DAYS = Long.parseLong(CONFIG.getProperty("license.offlineDays", "180"));
        PROLONGATION_PERIOD = CONFIG.getProperty("prolongation.period", "607875500");
    }

    private static Map<String, String> parseArguments(String[] args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException(
                String.format("参数数量无效: %d, 期望为偶数", args.length));
        }

        Map<String, String> params = new HashMap<>();

        for (int i = 0, len = args.length; i < len;) {
            String argName = args[i++];

            if (argName.charAt(0) == '-') {
                if (argName.length() < 2) {
                    throw new IllegalArgumentException("参数格式错误: " + argName);
                }
                argName = argName.substring(1);
            }

            params.put(argName, args[i++]);
        }

        return params;
    }

    public static void main(String[] args) throws Exception {
        // 加载配置
        loadConfig();
        
        Map<String, String> arguments = parseArguments(args);
        String port = arguments.get("p");

        if (port == null || !port.matches("\\d+")) {
            port = CONFIG.getProperty("server.port", "18081");
        }

        Server server = new Server(Integer.parseInt(port));
        server.setHandler(new MainServer());
        server.start();

        System.out.println("License Server started at http://localhost:" + port);
        System.out.println("JetBrains Activation address was: http://localhost:" + port + "/");
        System.out.println("JRebel 7.1 and earlier version Activation address was: http://localhost:" + port
                + "/{tokenname}, with any email.");
        System.out.println("JRebel 2018.1 and later version Activation address was: http://localhost:" + port
                + "/{guid}(eg:http://localhost:" + port + "/" + UUID.randomUUID() + "), with any email.");

        server.join();
    }

    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        System.out.println(target);

        if (target.equals("/")) {
            indexHandler(target, baseRequest, request, response);
        } else if (target.equals("/jrebel/leases")) {
            jrebelLeasesHandler(target, baseRequest, request, response);
        } else if (target.equals("/jrebel/leases/1")) {
            jrebelLeases1Handler(target, baseRequest, request, response);
        } else if (target.equals("/agent/leases")) {
            jrebelLeasesHandler(target, baseRequest, request, response);
        } else if (target.equals("/agent/leases/1")) {
            jrebelLeases1Handler(target, baseRequest, request, response);
        } else if (target.equals("/jrebel/validate-connection")) {
            jrebelValidateHandler(target, baseRequest, request, response);
        } else if (target.equals("/rpc/ping.action")) {
            pingHandler(target, baseRequest, request, response);
        } else if (target.equals("/rpc/obtainTicket.action")) {
            obtainTicketHandler(target, baseRequest, request, response);
        } else if (target.equals("/rpc/releaseTicket.action")) {
            releaseTicketHandler(target, baseRequest, request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
    }
    
    /**
     * 构建基础响应JSON对象
     */
    private JSONObject buildBaseResponse() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("serverVersion", SERVER_VERSION);
        jsonObject.put("serverProtocolVersion", SERVER_PROTOCOL_VERSION);
        jsonObject.put("serverGuid", SERVER_GUID);
        jsonObject.put("groupType", GROUP_TYPE_MANAGED);
        jsonObject.put("statusCode", STATUS_SUCCESS);
        return jsonObject;
    }
    
    /**
     * 构建完整的许可证响应JSON对象
     */
    private JSONObject buildLicenseResponse(boolean offline, String username, long validFrom, long validUntil) {
        JSONObject jsonObject = buildBaseResponse();
        jsonObject.put("id", ID);
        jsonObject.put("licenseType", LICENSE_TYPE);
        jsonObject.put("evaluationLicense", false);
        jsonObject.put("serverRandomness", "H2ulzLlh7E0=");
        jsonObject.put("seatPoolType", SEAT_POOL_TYPE_STANDALONE);
        jsonObject.put("offline", offline);
        jsonObject.put("validFrom", validFrom);
        jsonObject.put("validUntil", validUntil);
        jsonObject.put("company", username);
        jsonObject.put("orderId", "");
        jsonObject.put("zeroIds", Collections.emptyList());
        jsonObject.put("licenseValidFrom", validFrom);
        jsonObject.put("licenseValidUntil", validUntil);
        return jsonObject;
    }
    
    /**
     * 发送JSON响应
     */
    private void sendJsonResponse(Request baseRequest, HttpServletResponse response, JSONObject jsonObject) throws IOException {
        response.setContentType(CONTENT_TYPE_JSON);
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);
        response.getWriter().print(jsonObject);
    }

    private void jrebelValidateHandler(Request baseRequest, HttpServletResponse response) throws IOException {
        JSONObject jsonObject = buildBaseResponse();
        jsonObject.put("company", COMPANY_ADMIN);
        jsonObject.put("canGetLease", true);
        jsonObject.put("licenseType", LICENSE_TYPE);
        jsonObject.put("evaluationLicense", false);
        jsonObject.put("seatPoolType", SEAT_POOL_TYPE_STANDALONE);
        
        sendJsonResponse(baseRequest, response, jsonObject);
    }

    private void jrebelLeases1Handler(Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String username = request.getParameter("username");
        
        JSONObject jsonObject = buildBaseResponse();
        jsonObject.put("msg", null);
        jsonObject.put("statusMessage", null);
        
        if (username != null) {
            jsonObject.put("company", username);
        }
        
        sendJsonResponse(baseRequest, response, jsonObject);
    }

    private void jrebelLeasesHandler(Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
        try {
            // 参数获取和验证
            String clientRandomness = request.getParameter("randomness");
            String username = request.getParameter("username");
            String guid = request.getParameter("guid");
            
            if (clientRandomness == null || username == null || guid == null) {
                logger.warn("参数验证失败 - randomness-username: {}, guid: {}", clientRandomness+"-"+username, guid);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                baseRequest.setHandled(true);
                return;
            }
            
            boolean offline = Boolean.parseBoolean(request.getParameter("offline"));
            
            // 时间计算
            long validFrom = System.currentTimeMillis();
            long validUntil = offline
                ? validFrom + OFFLINE_LICENSE_DAYS * ONE_DAY_MILLIS
                : validFrom + ONLINE_LICENSE_DAYS * ONE_DAY_MILLIS;

            // 生成签名
            JREBEL_SIGN.toLeaseCreateJson(clientRandomness, guid, offline,
                    String.valueOf(validFrom), String.valueOf(validUntil));

            // 构建响应JSON
            JSONObject jsonObject = buildLicenseResponse(offline, username, validFrom, validUntil);
            jsonObject.put("signature", JREBEL_SIGN.getSignature());
            
            sendJsonResponse(baseRequest, response, jsonObject);
            
            logger.info("成功处理许可证请求 - 用户-离线模式: {}, 有效期至: {}", username+"-"+offline, new Date(validUntil));
            
        } catch (Exception e) {
            logger.error("处理许可证请求时发生错误", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            baseRequest.setHandled(true);
        }
    }

    private void releaseTicketHandler(Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
        try {
            baseRequest.setHandled(true);
            
            String salt = request.getParameter("salt");
            if (salt == null) {
                logger.warn("releaseTicket请求缺少salt参数");
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            String xmlContent = String.format(
                    "<ReleaseTicketResponse><message></message><responseCode>OK</responseCode><salt>%s</salt></ReleaseTicketResponse>",
                    salt);
            String xmlSignature = rsasign.Sign(xmlContent);
            
            response.setContentType(CONTENT_TYPE_HTML);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().printf("<!-- %s -->\n%s", xmlSignature, xmlContent);
            
            logger.info("成功处理releaseTicket请求 - salt: {}", salt);
            
        } catch (Exception e) {
            logger.error("处理releaseTicket请求时发生错误", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            baseRequest.setHandled(true);
        }
    }

    private void obtainTicketHandler(Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
        try {
            baseRequest.setHandled(true);
            
            String salt = request.getParameter("salt");
            String username = request.getParameter("userName");
            if (salt == null || username == null) {
                logger.warn("obtainTicket请求参数缺失 - salt: {}, username: {}", salt, username);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            String xmlContent = String.format(
                    "<ObtainTicketResponse><message></message><prolongationPeriod>%s</prolongationPeriod><responseCode>OK</responseCode><salt>%s</salt><ticketId>1</ticketId><ticketProperties>licensee=%s\tlicenseType=0\t</ticketProperties></ObtainTicketResponse>",
                    PROLONGATION_PERIOD, salt, username);
            String xmlSignature = rsasign.Sign(xmlContent);
            
            response.setContentType(CONTENT_TYPE_HTML);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().printf("<!-- %s -->\n%s", xmlSignature, xmlContent);
            
            logger.info("成功处理obtainTicket请求 - 用户: {}", username);
            
        } catch (Exception e) {
            logger.error("处理obtainTicket请求时发生错误", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            baseRequest.setHandled(true);
        }
    }

    private void pingHandler(Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
        try {
            baseRequest.setHandled(true);
            
            String salt = request.getParameter("salt");
            if (salt == null) {
                logger.warn("ping请求缺少salt参数");
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            String xmlContent = String.format(
                    "<PingResponse><message></message><responseCode>OK</responseCode><salt>%s</salt></PingResponse>",
                    salt);
            String xmlSignature = rsasign.Sign(xmlContent);
            
            response.setContentType(CONTENT_TYPE_HTML);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().printf("<!-- %s -->\n%s", xmlSignature, xmlContent);
            
            logger.info("成功处理ping请求 - salt: {}", salt);
            
        } catch (Exception e) {
            logger.error("处理ping请求时发生错误", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            baseRequest.setHandled(true);
        }
    }

    private void indexHandler(String target, Request baseRequest, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        response.setContentType("text/html; charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);

        int port = request.getServerPort();
        String html = "<h1>Hello,This is a Jrebel & JetBrains License Server!</h1>";
        html += "<p>License Server started at http://localhost:" + port;
        html += "<p>JetBrains Activation address was: <span style='color:red'>http://localhost:" + port + "/";
        html += "<p>JRebel 7.1 and earlier version Activation address was: <span style='color:red'>http://localhost:"
                + port + "/{tokenname}</span>, with any email.";
        html += "<p>JRebel 2018.1 and later version Activation address was: http://localhost:" + port
                + "/{guid}(eg:<span style='color:red'>http://localhost:" + port + "/" + UUID.randomUUID()
                + "</span>), with any email.";

        response.getWriter().println(html);

    }
}
