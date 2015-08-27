package com.zeen.plagiarismchecker.application.impl.service;

import java.util.logging.Logger;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.google.common.base.Strings;
import sun.util.locale.provider.LocaleServiceProviderPool;

public class RESTServer {

    private static final Logger LOGGER = Logger.getLogger(RESTServer.class
            .getName());

    static boolean started = false;
    static final int DEFAULT_PORT_NUMBER = 8080;

    static final int DEFAULT_MAX_REQUEST_HEADER_SIZE = 1024 * 1024 * 10;
    static final int DEFAULT_MAX_RESPONSE_HEADER_SIZE = 1024 * 1024 * 10;

    public static final String PORT_NUMBER_ENV_VARIABLE_NAME = "PlagiarismCheckerServicePortNumber";
    public static final String MAX_REQUEST_HEADER_SIZE_ENV_VARIABLE_NAME = "PlagiarismCheckerServiceMaxRequestHeaderSize";
    public static final String MAX_RESPONSE_HEADER_SIZE_ENV_VARIABLE_NAME = "PlagiarismCheckerServiceMaxResponseHeaderSize";

    static int getPortNumberSetting() {
        String property = System.getProperty(PORT_NUMBER_ENV_VARIABLE_NAME);
        if (Strings.isNullOrEmpty(property)) {
            property = System.getenv(PORT_NUMBER_ENV_VARIABLE_NAME);
        }
        if (Strings.isNullOrEmpty(property)) {
            return DEFAULT_PORT_NUMBER;
        }
        return Integer.valueOf(property);
    }

    static int getMaxRequestHeaderSizeSetting() {
        String property = System
                .getProperty(MAX_REQUEST_HEADER_SIZE_ENV_VARIABLE_NAME);
        if (Strings.isNullOrEmpty(property)) {
            property = System.getenv(MAX_REQUEST_HEADER_SIZE_ENV_VARIABLE_NAME);
        }
        if (Strings.isNullOrEmpty(property)) {
            return DEFAULT_MAX_REQUEST_HEADER_SIZE;
        }
        return Integer.valueOf(property);
    }

    static int getMaxResponseHeaderSizeSetting() {
        String property = System
                .getProperty(MAX_RESPONSE_HEADER_SIZE_ENV_VARIABLE_NAME);
        if (Strings.isNullOrEmpty(property)) {
            property = System
                    .getenv(MAX_RESPONSE_HEADER_SIZE_ENV_VARIABLE_NAME);
        }
        if (Strings.isNullOrEmpty(property)) {
            return DEFAULT_MAX_RESPONSE_HEADER_SIZE;
        }
        return Integer.valueOf(property);
    }

    public static void main(String[] args) throws Exception {
        PlagiarismCheckerService.setupContext(args);

        ServletContextHandler context = new ServletContextHandler(
                ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        Server server = new Server();

        HttpConfiguration conf = new HttpConfiguration();

        conf.setRequestHeaderSize(getMaxRequestHeaderSizeSetting());
        conf.setResponseHeaderSize(getMaxResponseHeaderSizeSetting());

        ServerConnector connector = new ServerConnector(server,
                new HttpConnectionFactory(conf));
        connector.setPort(getPortNumberSetting());

        server.setHandler(context);
        server.setConnectors(new ServerConnector[] { connector });

        ServletHolder jerseyServlet = context.addServlet(
                org.glassfish.jersey.servlet.ServletContainer.class, "/*");
        jerseyServlet.setInitOrder(0);

        // Tells the Jersey Servlet which REST service/class to load.

        jerseyServlet.setInitParameter(
                "jersey.config.server.provider.classnames",
                PlagiarismCheckerService.class.getCanonicalName());

        try {
            LOGGER.info("Starting server in port 8080");
            server.start();
            LOGGER.info("Started");
            started = true;
            server.join();
            connector.join();
        } finally {
            server.destroy();
        }
    }

}
