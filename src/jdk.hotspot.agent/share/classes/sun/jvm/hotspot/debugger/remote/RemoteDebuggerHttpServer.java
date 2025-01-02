package sun.jvm.hotspot.debugger.remote;

import java.net.*;
import java.nio.charset.*;
import java.io.*;
import java.util.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.tools.*;

public class RemoteDebuggerHttpServer {

    private static RemoteDebuggerHttpServer inst;

    private final JVMDebugger debugger;

    private final HttpServer server;

    private RemoteDebuggerHttpServer(JVMDebugger debugger, InetSocketAddress addr) throws IOException {
        this.debugger = debugger;
        server = HttpServer.create(addr, 0);

        server.createContext("/os", this::getOS);
        server.createContext("/cpu", this::getCPU);
        server.createContext("/machine-description", this::getMachineDescription);
        server.createContext("/lookup-in-process", this::lookupInProcess);
        server.createContext("/read-bytes-from-process", this::readBytesFromProcess);
        server.createContext("/console", this::hasConsole);
        server.createContext("/console-prompt", this::getConsolePrompt);
        server.createContext("/console-execute-command", this::consoleExecuteCommand);
        server.createContext("/size/jboolean", this::getJBooleanSize);
        server.createContext("/size/jbyte", this::getJByteSize);
        server.createContext("/size/jchar", this::getJCharSize);
        server.createContext("/size/jdouble", this::getJDoubleSize);
        server.createContext("/size/jfloat", this::getJFloatSize);
        server.createContext("/size/jint", this::getJIntSize);
        server.createContext("/size/jlong", this::getJLongSize);
        server.createContext("/size/jshort", this::getJShortSize);
        server.createContext("/size/heapoop", this::getHeapOopSize);
        server.createContext("/narrow/oop/base", this::getNarrowOopBase);
        server.createContext("/narrow/oop/shift", this::getNarrowOopShift);
        server.createContext("/size/klassptr", this::getKlassPtrSize);
        server.createContext("/narrow/klass/base", this::getNarrowKlassBase);
        server.createContext("/narrow/klass/shift", this::getNarrowKlassShift);
        server.createContext("/are-threads-equal", this::areThreadsEqual);
        server.createContext("/thread-hashcode", this::getThreadHashCode);
        server.createContext("/thread-integer-register-set", this::getThreadIntegerRegisterSet);
        server.createContext("/exec-command", this::execCommandOnServer);

        server.start();
    }

    private void sendStringResponse(HttpExchange exchange, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain;charset=UTF-8");
        exchange.sendResponseHeaders(200, response.length());
        exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
    }

    private Properties getArgumentsFromRequestBody(HttpExchange exchange) throws IOException {
        var prop = new Properties();
        prop.load(exchange.getRequestBody());
        return prop;
    }

    private Properties getArgumentsFromQueryString(HttpExchange exchange) {
        var prop = new Properties();
        for (var q : exchange.getRequestURI().getQuery().split("&")) {
            var elements = q.split("=");
            prop.setProperty(elements[0], elements[1]);
        }
        return prop;
    }

    private void getOS(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            sendStringResponse(exchange, debugger.getOS());
        }
    }

    private void getCPU(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            sendStringResponse(exchange, debugger.getCPU());
        }
    }

    private void getMachineDescription(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            sendStringResponse(exchange, debugger.getMachineDescription().getClass().getName());
        }
    }

    private void lookupInProcess(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            var args = getArgumentsFromRequestBody(exchange);
            String objectName = args.getProperty("objectName");
            String symbol = args.getProperty("symbol");

            var addr = debugger.lookup(objectName, symbol);
            long addrValue = (addr == null) ? 0L : debugger.getAddressValue(addr);
            sendStringResponse(exchange, Long.toString(addrValue));
        }
    }

    private void readBytesFromProcess(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            var args = getArgumentsFromRequestBody(exchange);

            long address = Long.parseLong(args.getProperty("address"));
            long numBytes = Long.parseLong(args.getProperty("numBytes"));
            var result = debugger.readBytesFromProcess(address, numBytes);
            var data = result.getData();

            var response = (data == null) ? ("<fail>" +  Long.toString(result.getFailureAddress()))
                                          : Base64.getEncoder().encodeToString(data);
            sendStringResponse(exchange, response);
        }
    }

    private void hasConsole(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            sendStringResponse(exchange, Boolean.toString(debugger.hasConsole()));
        }
    }

    private void getConsolePrompt(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            sendStringResponse(exchange, debugger.getConsolePrompt());
        }
    }

    private void consoleExecuteCommand(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            var args = getArgumentsFromRequestBody(exchange);

            var cmd = args.getProperty("cmd");
            sendStringResponse(exchange, debugger.consoleExecuteCommand(cmd));
        }
    }

    private void getJBooleanSize(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            sendStringResponse(exchange, Long.toString(debugger.getJBooleanSize()));
        }
    }

    private void getJByteSize(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            sendStringResponse(exchange, Long.toString(debugger.getJByteSize()));
        }
    }

    private void getJCharSize(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            sendStringResponse(exchange, Long.toString(debugger.getJCharSize()));
        }
    }

    private void getJDoubleSize(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            sendStringResponse(exchange, Long.toString(debugger.getJDoubleSize()));
        }
    }

    private void getJFloatSize(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            sendStringResponse(exchange, Long.toString(debugger.getJFloatSize()));
        }
    }

    private void getJIntSize(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            sendStringResponse(exchange, Long.toString(debugger.getJIntSize()));
        }
    }

    private void getJLongSize(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            sendStringResponse(exchange, Long.toString(debugger.getJLongSize()));
        }
    }

    private void getJShortSize(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            sendStringResponse(exchange, Long.toString(debugger.getJShortSize()));
        }
    }

    private void getHeapOopSize(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            sendStringResponse(exchange, Long.toString(debugger.getHeapOopSize()));
        }
    }

    private void getNarrowOopBase(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            sendStringResponse(exchange, Long.toString(debugger.getNarrowOopBase()));
        }
    }

    private void getNarrowOopShift(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            sendStringResponse(exchange, Integer.toString(debugger.getNarrowOopShift()));
        }
    }

    private void getKlassPtrSize(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            sendStringResponse(exchange, Long.toString(debugger.getHeapOopSize()));
        }
    }

    private void getNarrowKlassBase(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            sendStringResponse(exchange, Long.toString(debugger.getNarrowKlassBase()));
        }
    }

    private void getNarrowKlassShift(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            sendStringResponse(exchange, Integer.toString(debugger.getNarrowKlassShift()));
        }
    }

    private void areThreadsEqual(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            var args = getArgumentsFromQueryString(exchange);
            long addrOrId1 = Long.parseLong(args.getProperty("addrOrId1"));
            boolean isAddress1 = Boolean.parseBoolean(args.getProperty("isAddress1"));
            long addrOrId2 = Long.parseLong(args.getProperty("addrOrId2"));
            boolean isAddress2 = Boolean.parseBoolean(args.getProperty("isAddress2"));

            ThreadProxy t1 = getThreadProxy(addrOrId1, isAddress1);
            ThreadProxy t2 = getThreadProxy(addrOrId2, isAddress2);

            sendStringResponse(exchange, Boolean.toString(t1.equals(t2)));
        }
    }

    private void getThreadHashCode(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            var args = getArgumentsFromQueryString(exchange);
            long addrOrId = Long.parseLong(args.getProperty("addrOrId"));
            boolean isAddress = Boolean.parseBoolean(args.getProperty("isAddress"));

            ThreadProxy t = getThreadProxy(addrOrId, isAddress);

            sendStringResponse(exchange, Integer.toString(t.hashCode()));
        }
    }

    private void getThreadIntegerRegisterSet(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            var args = getArgumentsFromQueryString(exchange);
            long addrOrId = Long.parseLong(args.getProperty("addrOrId"));
            boolean isAddress = Boolean.parseBoolean(args.getProperty("isAddress"));

            ThreadProxy t = getThreadProxy(addrOrId, isAddress);
            ThreadContext tc = t.getContext();
            var joiner = new StringJoiner(",");
            for (int r = 0; r < tc.getNumRegisters(); r++) {
                joiner.add(Long.toString(tc.getRegister(r)));
            }

            sendStringResponse(exchange, joiner.toString());
        }
    }

    private ThreadProxy getThreadProxy(long addrOrId, boolean isAddress) throws DebuggerException {
        if (isAddress) {
            Address addr = debugger.parseAddress("0x" + Long.toHexString(addrOrId));
            return debugger.getThreadForIdentifierAddress(addr);
        } else {
            return debugger.getThreadForThreadId(addrOrId);
        }
    }

    private void execCommandOnServer(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            throw new IOException("Unsupported request: " + exchange.getRequestMethod());
        }

        try (exchange) {
            var args = getArgumentsFromRequestBody(exchange);
            var command = args.getProperty("command");
            String response;

            if (command.equals("findsym")) {
                response = debugger.findSymbol(args.getProperty("symbol"));
            } else {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                try (var out = new PrintStream(bout)) {
                    if (command.equals("pmap")) {
                        (new PMap(debugger)).run(out, debugger);
                    } else if (command.equals("pstack")) {
                        PStack pstack = new PStack(debugger);
                        pstack.setVerbose(false);
                        pstack.setConcurrentLocks(Boolean.parseBoolean(args.getProperty("concurrentLocks")));
                        pstack.run(out, debugger);
                    } else {
                        throw new DebuggerException(command + " is not supported in this debugger");
                    }
                }
                response = bout.toString();
            }

            sendStringResponse(exchange, response);
        }

    }

    public static synchronized void create(JVMDebugger dbg, InetSocketAddress addr) throws IOException {
        if (inst != null) {
            throw new IllegalStateException("Instance already exists");
        }
        inst = new RemoteDebuggerHttpServer(dbg, addr);
    }

    public static synchronized void shutdown() {
        if (inst == null) {
            throw new IllegalStateException("Instance has not yet initialized");
        }
        inst.server.stop(0);
    }

}
