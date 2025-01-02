package sun.jvm.hotspot.debugger.remote;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.rmi.*;
import java.util.*;
import java.util.stream.*;

import sun.jvm.hotspot.debugger.*;

public class RemoteHttpDebugger implements RemoteDebugger {

    private final URI baseURI;

    private static enum HttpMethod {
        GET,
        POST
    }

    private HttpRequest buildGetRequest(String path, Properties args) {
        if (args != null) {
            String queryParams = args.keySet()
                                     .stream()
                                     .map(k -> k + "=" + args.get(k))
                                     .collect(Collectors.joining("&"));
            path += "?" + queryParams;
        }

        var requestURI = baseURI.resolve(path);
        return HttpRequest.newBuilder()
                          .uri(requestURI)
                          .GET()
                          .build();
    }

    private HttpRequest buildPostRequest(String path, Properties args) throws IOException {
        String requestBody = "";
        if (args != null) {
            var writer = new StringWriter();
            args.store(writer, null);
            requestBody = writer.toString();
        }

        return HttpRequest.newBuilder()
                          .uri(baseURI.resolve(path))
                          .setHeader("Content-Type", "text/x-java-properties")
                          .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                          .build();
    }

    private String execRequest(HttpMethod method, String path) throws IOException, InterruptedException {
        return execRequest(method, path, null);
    }

    private String execRequest(HttpMethod method, String path, Properties args) throws IOException, InterruptedException {
        var client = HttpClient.newBuilder()
                               .version(HttpClient.Version.HTTP_1_1)
                               .build();
        var request = switch(method) {
                          case GET -> buildGetRequest(path, args);
                          case POST -> buildPostRequest(path, args);
                          default -> throw new IllegalArgumentException("Unexpected method: " + method.toString());
                      };

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Operation did not succeed: " + response.statusCode());
        }

        return response.body();
    }

    public RemoteHttpDebugger(URI baseURI) {
        this.baseURI = baseURI;
    }

    public String getOS() throws RemoteException {
        try {
            return execRequest(HttpMethod.GET, "os");
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public String getCPU() throws RemoteException {
        try {
            return execRequest(HttpMethod.GET, "cpu");
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public MachineDescription getMachineDescription() throws RemoteException {
        try {
            var fqcn = execRequest(HttpMethod.GET, "machine-description");
            return (MachineDescription)Class.forName(fqcn)
                                            .getDeclaredConstructor()
                                            .newInstance();
        } catch (Exception e) {
            throw new DebuggerException(e);
        }
    }

    public long lookupInProcess(String objectName, String symbol) throws RemoteException {
        var prop = new Properties();
        if (objectName != null) {
            prop.setProperty("objectName", objectName);
        }
        prop.setProperty("symbol", symbol);

        try {
            var ret = execRequest(HttpMethod.POST, "lookup-in-process", prop);
            return Long.parseLong(ret);
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public ReadResult readBytesFromProcess(long address, long numBytes) throws RemoteException {
        var prop = new Properties();
        prop.setProperty("address", Long.toString(address));
        prop.setProperty("numBytes", Long.toString(numBytes));

        try {
            var ret = execRequest(HttpMethod.POST, "read-bytes-from-process", prop);
            final String FAIL_LABEL = "<fail>";
            return ret.startsWith(FAIL_LABEL)
                ? new ReadResult(Long.parseLong(ret.replace(FAIL_LABEL, "")))
                : new ReadResult(Base64.getDecoder().decode(ret));
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public boolean hasConsole() throws RemoteException {
        try {
            return Boolean.parseBoolean(execRequest(HttpMethod.GET, "console"));
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public String getConsolePrompt() throws RemoteException {
        try {
            return execRequest(HttpMethod.GET, "console-prompt");
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public String consoleExecuteCommand(String cmd) throws RemoteException {
        var prop = new Properties();
        prop.setProperty("cmd", cmd);

        try {
            return execRequest(HttpMethod.POST, "console-execute-command", prop);
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public long getJBooleanSize() throws RemoteException {
        try {
            return Long.parseLong(execRequest(HttpMethod.GET, "size/jboolean"));
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public long getJByteSize() throws RemoteException {
        try {
            return Long.parseLong(execRequest(HttpMethod.GET, "size/jbyte"));
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public long getJCharSize() throws RemoteException {
        try {
            return Long.parseLong(execRequest(HttpMethod.GET, "size/jchar"));
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public long getJDoubleSize() throws RemoteException {
        try {
            return Long.parseLong(execRequest(HttpMethod.GET, "size/jdouble"));
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public long getJFloatSize() throws RemoteException {
        try {
            return Long.parseLong(execRequest(HttpMethod.GET, "size/jfloat"));
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public long getJIntSize() throws RemoteException {
        try {
            return Long.parseLong(execRequest(HttpMethod.GET, "size/jint"));
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public long getJLongSize() throws RemoteException {
        try {
            return Long.parseLong(execRequest(HttpMethod.GET, "size/jlong"));
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public long getJShortSize() throws RemoteException {
        try {
            return Long.parseLong(execRequest(HttpMethod.GET, "size/jshort"));
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public long getHeapOopSize() throws RemoteException {
        try {
            return Long.parseLong(execRequest(HttpMethod.GET, "size/heapoop"));
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public long getNarrowOopBase() throws RemoteException {
        try {
            return Long.parseLong(execRequest(HttpMethod.GET, "narrow/oop/base"));
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public int getNarrowOopShift() throws RemoteException {
        try {
            return Integer.parseInt(execRequest(HttpMethod.GET, "narrow/oop/shift"));
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public long getKlassPtrSize() throws RemoteException {
        try {
            return Long.parseLong(execRequest(HttpMethod.GET, "size/klassptr"));
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public long getNarrowKlassBase() throws RemoteException {
        try {
            return Long.parseLong(execRequest(HttpMethod.GET, "narrow/klass/base"));
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public int getNarrowKlassShift() throws RemoteException {
        try {
            return Integer.parseInt(execRequest(HttpMethod.GET, "narrow/klass/shift"));
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public boolean areThreadsEqual(long addrOrId1, boolean isAddress1,
                                   long addrOrId2, boolean isAddress2) throws RemoteException {
        var prop = new Properties();
        prop.setProperty("addrOrId1", Long.toString(addrOrId1));
        prop.setProperty("isAddress1", Boolean.toString(isAddress1));
        prop.setProperty("addrOrId2", Long.toString(addrOrId2));
        prop.setProperty("isAddress2", Boolean.toString(isAddress2));

        try {
            var ret = execRequest(HttpMethod.GET, "are-threads-equal", prop);
            return Boolean.parseBoolean(ret);
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public int getThreadHashCode(long addrOrId, boolean isAddress) throws RemoteException {
        var prop = new Properties();
        prop.setProperty("addrOrId", Long.toString(addrOrId));
        prop.setProperty("isAddress", Boolean.toString(isAddress));

        try {
            var ret = execRequest(HttpMethod.GET, "thread-hashcode", prop);
            return Integer.parseInt(ret);
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public long[] getThreadIntegerRegisterSet(long addrOrId, boolean isAddress) throws RemoteException {
        var prop = new Properties();
        prop.setProperty("addrOrId", Long.toString(addrOrId));
        prop.setProperty("isAddress", Boolean.toString(isAddress));

        try {
            var ret = execRequest(HttpMethod.GET, "thread-integer-register-set", prop);
            return Arrays.stream(ret.split(","))
                         .mapToLong(Long::parseLong)
                         .toArray();
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

    public String execCommandOnServer(String command, Map<String, Object> options) throws RemoteException {
        var prop = new Properties();
        prop.setProperty("command", command);
        options.forEach((k, v) -> prop.setProperty(k, v.toString()));

        try {
            return execRequest(HttpMethod.POST, "exec-command", prop);
        } catch (IOException | InterruptedException e) {
            throw new DebuggerException(e);
        }
    }

}
