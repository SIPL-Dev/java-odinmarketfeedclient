package com.trading;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.time.Instant;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.trading.callback.OnCloseCallback;
import com.trading.callback.OnErrorCallback;
import com.trading.callback.OnMessageCallback;
import com.trading.callback.OnOpenCallback;


interface MarketData {
    int getMktSegId();

    int getToken();

    long getLut();

    int getLtp();

    int getClosePrice();

    int getDecimalLocator();
}

// ZLIB Compressor class
class ZLIBCompressor {

    public byte[] compress(byte[] data) throws IOException {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[1024];

        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            outputStream.write(buffer, 0, count);
        }

        deflater.end();
        return outputStream.toByteArray();
    }

    public byte[] uncompress(byte[] data) throws IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(data);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[1024];

        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
        } catch (Exception e) {
            throw new IOException("Error decompressing data", e);
        } finally {
            inflater.end();
        }

        return outputStream.toByteArray();
    }
}

// interface OnOpenCallback {
//     void onOpen();
// }

// interface OnMessageCallback {
//     void onMessage(String message);
// }

// interface OnErrorCallback {
//     void onError(String error);
// }

// interface OnCloseCallback {
//     void onClose(int code, String reason);
// }


// Fragmentation Handler class
class FragmentationHandler {
    private byte[] memoryStream;
    private int lastWrittenIndex;
    private boolean isDisposed;
    private ZLIBCompressor zlibCompressor;

    private static final int MINIMUM_PACKET_SIZE = 5;
    private static final int PACKET_HEADER_SIZE = 5;
    private static final int HEADER_LENGTH = 6;

    private char[] headerChar = new char[5];
    public FragmentationHandler() {
        this.memoryStream = new byte[0];
        this.lastWrittenIndex = -1;
        this.isDisposed = false;
        this.zlibCompressor = new ZLIBCompressor();
    }

    public byte[] fragmentData(byte[] data) throws IOException {
        byte[] compressed = zlibCompressor.compress(data);
        String lengthString = String.format("%06d", compressed.length);
        byte[] lenBytes = lengthString.getBytes(StandardCharsets.US_ASCII);
        lenBytes[0] = 5; // compression flag

        byte[] result = new byte[lenBytes.length + compressed.length];
        System.arraycopy(lenBytes, 0, result, 0, lenBytes.length);
        System.arraycopy(compressed, 0, result, lenBytes.length, compressed.length);

        return result;
    }

    public List<byte[]> defragment(byte[] data) {
        if (isDisposed) {
            return new ArrayList<>();
        }

        // Append data to memory stream
        byte[] newStream = new byte[lastWrittenIndex + 1 + data.length];
        if (lastWrittenIndex >= 0) {
            System.arraycopy(memoryStream, 0, newStream, 0, lastWrittenIndex + 1);
        }
        System.arraycopy(data, 0, newStream, lastWrittenIndex + 1, data.length);
        memoryStream = newStream;
        lastWrittenIndex = memoryStream.length - 1;

        return defragmentData();
    }

    private List<byte[]> defragmentData() {
        boolean parseDone = false;
        int bytesParsed = 0;
        List<byte[]> packetList = new ArrayList<>();
        int position = 0;

        while (position < lastWrittenIndex - MINIMUM_PACKET_SIZE && !parseDone) {
            int headerEnd = position + PACKET_HEADER_SIZE + 1;
            if (headerEnd > memoryStream.length) {
                break;
            }

            byte[] header = Arrays.copyOfRange(memoryStream, position, headerEnd);
            int packetSize = isLength(header);

            if (packetSize <= 0) {
                position += 1;
                bytesParsed += 1;
            } else {
                int dataStart = headerEnd;
                int dataEnd = dataStart + packetSize;

                if (dataEnd <= lastWrittenIndex + 1) {
                    byte[] compressData = Arrays.copyOfRange(memoryStream, dataStart, dataEnd);
                    defragmentInnerData(compressData, packetList);
                    bytesParsed += PACKET_HEADER_SIZE + 1 + packetSize;
                    position = dataEnd;
                } else {
                    parseDone = true;
                }
            }
        }

        clearProcessedData(bytesParsed);
        return packetList;
    }

    private int isLength(byte[] header) {
        if (header.length != PACKET_HEADER_SIZE + 1) {
            return -1;
        }

        if (header[0] != 5 && header[0] != 2) {
            return -1;
        }

        String lengthStr = new String(header, 1, 5, StandardCharsets.US_ASCII);
        if (!lengthStr.matches("\\d+")) {
            return -1;
        }

        return Integer.parseInt(lengthStr);
    }

    private void defragmentInnerData(byte[] compressData, List<byte[]> packetList) {
        try {
            byte[] messageData = zlibCompressor.uncompress(compressData);

            int mUnCompressMsgLength;
            while (true) {
                mUnCompressMsgLength = 0;
                mUnCompressMsgLength = getMessageLength(messageData);

                if (mUnCompressMsgLength <= 0) {
                    break;
                }

                // Extract the uncompressed bytes
                byte[] unCompressBytes = new byte[mUnCompressMsgLength];
                System.arraycopy(messageData, HEADER_LENGTH, unCompressBytes, 0, mUnCompressMsgLength);

                packetList.add(unCompressBytes);
                // Calculate remaining data length
                int remainingLength = messageData.length - mUnCompressMsgLength - HEADER_LENGTH;

                if (remainingLength <= 0) {
                    break;
                }

                // Extract remaining data
                byte[] unCompressNewBytes = new byte[remainingLength];
                System.arraycopy(messageData, mUnCompressMsgLength + HEADER_LENGTH,
                        unCompressNewBytes, 0, remainingLength);

                messageData = unCompressNewBytes;
            }
        } catch (Exception error) {
            System.err.println("Error decompressing data: " + error.getMessage());
        }
    }

    private int getMessageLength(byte[] message) {
        if (message[0] == 5) {
        } else {
        }

        try {
            int i = 1;
            int startIndex = 0;

            for (; i < HEADER_LENGTH; i++) {
                headerChar[startIndex] = (char) message[i];
                startIndex++;
            }

            String sLength = new String(headerChar, 0, startIndex);
            return Integer.parseInt(sLength);
        } catch (Exception e) {
            return 0;
        }
    }

    private void clearProcessedData(int length) {
        if (length <= 0) {
            return;
        }

        if (length >= lastWrittenIndex + 1) {
            lastWrittenIndex = -1;
            memoryStream = new byte[0];
            return;
        }

        int size = lastWrittenIndex + 1 - length;
        byte[] data = Arrays.copyOfRange(memoryStream, length, length + size);
        memoryStream = data;
        lastWrittenIndex = size - 1;
    }
}

// Main ODIN Market Feed Client
public class ODINMarketFeedClient  {
    // private Session session;
    private WebSocketClient webSocketClient;
    private boolean isDisposed = false;
    private FragmentationHandler fragHandler;

    private Calendar dteNSE;

    // Callbacks
    public OnOpenCallback onOpenCallback;
    public OnMessageCallback onMessageCallback;
    public OnErrorCallback onErrorCallback;
    public OnCloseCallback onCloseCallback;

    public ODINMarketFeedClient() {
        this.fragHandler = new FragmentationHandler();

        // Initialize NSE date (1980-01-01)
        dteNSE = Calendar.getInstance();
        dteNSE.set(1980, Calendar.JANUARY, 1, 0, 0, 0);
        dteNSE.set(Calendar.MILLISECOND, 0);
    }

    public void setOnOpen(OnOpenCallback callback) {
        this.onOpenCallback = callback;
    }

    public void setOnMessage(OnMessageCallback callback) {
        this.onMessageCallback = callback;
    }

    public void setOnError(OnErrorCallback callback) {
        this.onErrorCallback = callback;
    }

    public void setOnClose(OnCloseCallback callback) {
        this.onCloseCallback = callback;
    }
    

    public CompletableFuture<Void> connect(String host, int port, boolean useSSL,
            String userId, String apiKey) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Input validation
        if (host == null || host.trim().isEmpty()) {
            if (onErrorCallback != null) {
                onErrorCallback.onError("Host cannot be null or empty.");
            }
            future.completeExceptionally(new IllegalArgumentException("Host cannot be null or empty."));
            return future;
        }

        if (port <= 0 || port > 65535) {
            if (onErrorCallback != null) {
                onErrorCallback.onError("Port must be between 1 and 65535.");
            }
            future.completeExceptionally(new IllegalArgumentException("Port must be between 1 and 65535."));
            return future;
        }

        if (userId == null || userId.trim().isEmpty()) {
            if (onErrorCallback != null) {
                onErrorCallback.onError("User ID cannot be null or empty.");
            }
            future.completeExceptionally(new IllegalArgumentException("User ID cannot be null or empty."));
            return future;
        }

      

        try {
            String protocol = useSSL ? "wss" : "ws";
            String url = String.format("%s://%s:%d", protocol, host, port);

            // WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            // container.setDefaultMaxBinaryMessageBufferSize(receiveBufferSize);

            // URI uri = new URI(url);
            // this.session = container.connectToServer(this, uri);

            // Send authentication after connection
            webSocketClient = new WebSocketClient(new URI(url)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    System.out.println("Connected");

                    try {
                        sendLogin();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    if (onOpenCallback != null) {
                        onOpenCallback.onOpen();
                    }
                    future.complete(null);
                }

                @Override
                public void onMessage(String message) {
                    // Text messages (not used in this implementation)
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    byte[] data = new byte[bytes.remaining()];
                    bytes.get(data);
                    responseReceived(data);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("Connection closed: " + code + " - " + reason);
                    if (onCloseCallback != null) {
                        onCloseCallback.onClose(code, reason);
                    }
                }

                @Override
                public void onError(Exception ex) {
                    String errorMsg = "Connection error: " + ex.getMessage();
                    System.err.println(errorMsg);
                    if (onErrorCallback != null) {
                        onErrorCallback.onError(errorMsg);
                    }
                    future.completeExceptionally(ex);
                }

                public CompletableFuture<Void> sendLogin() throws InterruptedException {

                    return CompletableFuture.runAsync(() -> {
                       
                        // Send login message
                        String currentTime = formatTime(Date.from(Instant.now()));

                        String password = "68=";
                        if (apiKey != null && !apiKey.trim().isEmpty()) {
                            password = String.format("68=%s|401=2", apiKey);
                        }

                        // Build login message
                        String loginMsg = String.format("63=FT3.0|64=101|65=74|66=%s|67=%s|%s",currentTime, userId, password);
                        try {
                            sendMessage(loginMsg);
                        } catch (IOException e) {
                            if (onErrorCallback != null) {
                                onErrorCallback.onError("Error sending login message: " + e.getMessage());
                            }
                        }
                    });
                
                }
            };

            webSocketClient.connect();

            future.complete(null);
        } catch (Exception e) {
            if (onErrorCallback != null) {
                onErrorCallback.onError("Connection error: " + e.getMessage());
            }
            future.completeExceptionally(e);
        }

        return future;
    }

    // public void disconnect() throws InterruptedException, IOException {
    // if (session != null && session.isOpen()) {
    // session.close();
    // }
    // }

    public void disconnect() throws InterruptedException {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.closeBlocking();
        }
    }

   
    /**
     * Subscribe to touchline data with mode and snapshot options
     * 
     * @param tokenList  List of tokens in format "MarketSegmentID_Token"
     * @param mode       Mode "0" or "1"
     * @param isSnapshot Whether to get snapshot data
     */
    public CompletableFuture<Void> subscribeTouchlineold(List<String> tokenList, String mode) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (tokenList == null || tokenList.isEmpty()) {
            if (onErrorCallback != null) {
                onErrorCallback.onError("Token list cannot be null or empty.");
            }
            future.completeExceptionally(new IllegalArgumentException("Token list cannot be null or empty."));
            return future;
        }

        StringBuilder strTokenToSubscribe = new StringBuilder();

        for (String item : tokenList) {
            if (isNullOrWhiteSpace(item)) {
                continue;
            }

            String[] parts = item.split("_");

            if (parts.length != 2) {
                if (onErrorCallback != null) {
                    onErrorCallback.onError(String.format(
                            "Invalid token format: '%s'. Expected format: 'MarketSegmentID_Token'.", item));
                }
                continue;
            }

            try {
                int marketSegmentId = Integer.parseInt(parts[0]);
                int token = Integer.parseInt(parts[1]);

                strTokenToSubscribe.append(String.format("1=%d$7=%d|", marketSegmentId, token));
            } catch (NumberFormatException e) {
                if (onErrorCallback != null) {
                    onErrorCallback.onError(String.format(
                            "Invalid token format: '%s'. Expected format: 'MarketSegmentID_Token'.", item));
                }
                continue;
            }
        }

        if (strTokenToSubscribe.length() > 0) {
            try {
                String currentTime = formatTime(new Date());
                String subscribeFlag = "230=1";
                String touchlineRequest = String.format("63=FT3.0|64=348|65=84|66=%s|%s%s", currentTime,
                        strTokenToSubscribe.toString(), subscribeFlag);
                sendMessage(touchlineRequest);
                System.out
                        .println(String.format("Subscribed to touchline for tokens: %s", String.join(", ", tokenList)));
                future.complete(null);
            } catch (Exception e) {
                if (onErrorCallback != null) {
                    onErrorCallback.onError("Touchline subscription failed: " + e.getMessage());
                }
                future.completeExceptionally(e);
            }
        } else {
            if (onErrorCallback != null) {
                onErrorCallback.onError("No valid tokens found to subscribe.");
            }
            future.completeExceptionally(new IllegalArgumentException("No valid tokens found to subscribe."));
        }

        return future;
    }

    public void subscribeTouchline(List<String> tokenList, String responseType, boolean ltpChangeOnly)
            throws Exception {
        // Validate token list
        if (tokenList == null || tokenList.isEmpty()) {
            if (onErrorCallback != null) {
                onErrorCallback.onError("Token list cannot be null or empty.");
            }
            throw new Exception("Token list cannot be empty");
        }

        // Validate response type
        if (!responseType.equals("0") && !responseType.equals("1")) {
            if (onErrorCallback != null) {
                onErrorCallback.onError("Invalid response type passed. Valid values are 0 or 1");
            }
            throw new Exception("Invalid response type");
        }

        StringBuilder strTokenToSubscribe = new StringBuilder();

        // Process each token
        for (String item : tokenList) {
            if (isNullOrWhiteSpace(item)) {
                continue;
            }

            String[] parts = item.split("_");
            if (parts.length != 2) {
                if (onErrorCallback != null) {
                    onErrorCallback.onError(String.format(
                            "Invalid token format: '%s'. Expected format: 'MarketSegmentID_Token'.", item));
                }
                continue;
            }

            try {
                int marketSegmentID = Integer.parseInt(parts[0]);
                int token = Integer.parseInt(parts[1]);
                strTokenToSubscribe.append(String.format("1=%d$7=%d|", marketSegmentID, token));
            } catch (NumberFormatException e) {
                if (onErrorCallback != null) {
                    onErrorCallback.onError(String.format(
                            "Invalid token format: '%s'. Expected format: 'MarketSegmentID_Token'.", item));
                }
                continue;
            }
        }

        // Build response type string
        String strResponseType = "";
        if (responseType.equals("1")) {
            strResponseType = "49=1";
        }

        // Build LTP change only string
        String sLTChangeOnly = ltpChangeOnly ? "200=1" : "200=0";

        // Build and send request if we have valid tokens
        if (strTokenToSubscribe.length() > 0) {
            String currentTime = formatTime(Date.from(Instant.now()));
            String tlRequest;

            if (!strResponseType.isEmpty()) {
                tlRequest = String.format("63=FT3.0|64=206|65=84|66=%s|%s|%s|%s230=1",
                        currentTime, strResponseType, sLTChangeOnly, strTokenToSubscribe.toString());
            } else {
                tlRequest = String.format("63=FT3.0|64=206|65=84|66=%s|%s|%s230=1",
                        currentTime, sLTChangeOnly, strTokenToSubscribe.toString());
            }

            sendMessage(tlRequest);
            System.out.printf("Subscribed to touchline tokens: %s%n", String.join(", ", tokenList));
            return;
        }

        // No valid tokens found
        if (onErrorCallback != null) {
            onErrorCallback.onError("No valid tokens found to subscribe.");
        }
        throw new Exception("No valid tokens found");
    }

    /**
     * Unsubscribe from touchline data
     * 
     * @param tokenList List of tokens in format "MarketSegmentID_Token"
     * @param mode      Mode "0" or "1"
     */
    public CompletableFuture<Void> unsubscribeTouchline(List<String> tokenList) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (tokenList == null || tokenList.isEmpty()) {
            if (onErrorCallback != null) {
                onErrorCallback.onError("Token list cannot be null or empty.");
            }
            future.completeExceptionally(new IllegalArgumentException("Token list cannot be null or empty."));
            return future;
        }

        StringBuilder strTokenToSubscribe = new StringBuilder();

        for (String item : tokenList) {
            if (isNullOrWhiteSpace(item)) {
                continue;
            }

            String[] parts = item.split("_");

            if (parts.length != 2) {
                if (onErrorCallback != null) {
                    onErrorCallback.onError(String.format(
                            "Invalid token format: '%s'. Expected format: 'MarketSegmentID_Token'.", item));
                }
                continue;
            }

            try {
                int marketSegmentId = Integer.parseInt(parts[0]);
                int token = Integer.parseInt(parts[1]);

                strTokenToSubscribe.append(String.format("1=%d$7=%d|", marketSegmentId, token));
            } catch (NumberFormatException e) {
                if (onErrorCallback != null) {
                    onErrorCallback.onError(String.format(
                            "Invalid token format: '%s'. Expected format: 'MarketSegmentID_Token'.", item));
                }
                continue;
            }
        }

        if (strTokenToSubscribe.length() > 0) {
            try {
                String currentTime = formatTime(new Date());
                String touchlineRequest = String.format(
                        "63=FT3.0|64=206|65=84|66=%s|%s230=2",
                        currentTime, strTokenToSubscribe.toString());
                sendMessage(touchlineRequest);
                System.out.println(String.format("Unsubscribed from touchline for tokens: %s",
                        String.join(", ", tokenList)));
                future.complete(null);
            } catch (Exception e) {
                if (onErrorCallback != null) {
                    onErrorCallback.onError("Touchline unsubscription failed: " + e.getMessage());
                }
                future.completeExceptionally(e);
            }
        } else {
            if (onErrorCallback != null) {
                onErrorCallback.onError("No valid tokens found to unsubscribe.");
            }
            future.completeExceptionally(new IllegalArgumentException("No valid tokens found to unsubscribe."));
        }

        return future;
    }

    /**
     * Subscribe to best 5 bid/ask prices for a token
     * 
     * @param token           Token identifier
     * @param marketSegmentId Market segment ID
     */
    public CompletableFuture<Void> subscribeBestFive(String token, int marketSegmentId) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (isNullOrWhiteSpace(token)) {
            if (onErrorCallback != null) {
                onErrorCallback.onError("Token cannot be null or empty.");
            }
            future.completeExceptionally(new IllegalArgumentException("Token cannot be null or empty."));
            return future;
        }

        try {
            String currentTime = formatTime(new Date());
            String bestFiveRequest = String.format(
                    "63=FT3.0|64=127|65=84|66=%s|1=%d|7=%s|230=1",
                    currentTime, marketSegmentId, token);
            sendMessage(bestFiveRequest);
            System.out.println(String.format("Subscribed to best five for token: %s (segment: %d)",
                    token, marketSegmentId));
            future.complete(null);
        } catch (Exception e) {
            if (onErrorCallback != null) {
                onErrorCallback.onError("Best five subscription failed: " + e.getMessage());
            }
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Unsubscribe from best 5 bid/ask prices for a token
     * 
     * @param token           Token identifier
     * @param marketSegmentId Market segment ID
     */
    public CompletableFuture<Void> unsubscribeBestFive(String token, int marketSegmentId) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (isNullOrWhiteSpace(token)) {
            if (onErrorCallback != null) {
                onErrorCallback.onError("Token cannot be null or empty.");
            }
            future.completeExceptionally(new IllegalArgumentException("Token cannot be null or empty."));
            return future;
        }

        try {
            String currentTime = formatTime(new Date());
            String bestFiveRequest = String.format(
                    "63=FT3.0|64=127|65=84|66=%s|1=%d|7=%s|230=2",
                    currentTime, marketSegmentId, token);
            sendMessage(bestFiveRequest);
            System.out.println(String.format("Unsubscribed from best five for token: %s (segment: %d)",
                    token, marketSegmentId));
            future.complete(null);
        } catch (Exception e) {
            if (onErrorCallback != null) {
                onErrorCallback.onError("Best five unsubscription failed: " + e.getMessage());
            }
            future.completeExceptionally(e);
        }

        return future;
    }

    public CompletableFuture<Void> subscribeLTPTouchline(List<String> tokenList) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (tokenList == null || tokenList.isEmpty()) {
            if (onErrorCallback != null) {
                onErrorCallback.onError("Token list cannot be null or empty.");
            }
            future.completeExceptionally(new IllegalArgumentException("Token list cannot be null or empty."));
            return future;
        }

        StringBuilder strTokenToSubscribe = new StringBuilder();

        for (String item : tokenList) {
            if (isNullOrWhiteSpace(item)) {
                continue;
            }

            String[] parts = item.split("_");

            if (parts.length != 2) {
                if (onErrorCallback != null) {
                    onErrorCallback.onError(String.format(
                            "Invalid token format: '%s'. Expected format: 'MarketSegmentID_Token'.", item));
                }
                continue;
            }

            try {
                int marketSegmentId = Integer.parseInt(parts[0]);
                int token = Integer.parseInt(parts[1]);

                strTokenToSubscribe.append(String.format("1=%d$7=%d|", marketSegmentId, token));
            } catch (NumberFormatException e) {
                if (onErrorCallback != null) {
                    onErrorCallback.onError(String.format(
                            "Invalid token format: '%s'. Expected format: 'MarketSegmentID_Token'.", item));
                }
                continue;
            }
        }

        if (strTokenToSubscribe.length() > 0) {
            try {
                String currentTime = formatTime(new Date());
                String tlRequest = String.format(
                        "63=FT3.0|64=347|65=84|66=%s|%s230=1",
                        currentTime, strTokenToSubscribe.toString());
                sendMessage(tlRequest);
                System.out.println("Subscribed to LTP touchline tokens: " + String.join(", ", tokenList));
                future.complete(null);
            } catch (Exception e) {
                if (onErrorCallback != null) {
                    onErrorCallback.onError("Subscription failed: " + e.getMessage());
                }
                future.completeExceptionally(e);
            }
        } else {
            if (onErrorCallback != null) {
                onErrorCallback.onError("No valid tokens found to subscribe.");
            }
            future.completeExceptionally(new IllegalArgumentException("No valid tokens found to subscribe."));
        }

        return future;
    }

    public CompletableFuture<Void> unsubscribeLTPTouchline(List<String> tokenList) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (tokenList == null || tokenList.isEmpty()) {
            if (onErrorCallback != null) {
                onErrorCallback.onError("Token list cannot be null or empty.");
            }
            future.completeExceptionally(new IllegalArgumentException("Token list cannot be null or empty."));
            return future;
        }

        StringBuilder strTokenToSubscribe = new StringBuilder();

        for (String item : tokenList) {
            if (isNullOrWhiteSpace(item)) {
                continue;
            }

            String[] parts = item.split("_");

            if (parts.length != 2) {
                if (onErrorCallback != null) {
                    onErrorCallback.onError(String.format(
                            "Invalid token format: '%s'. Expected format: 'MarketSegmentID_Token'.", item));
                }
                continue;
            }

            try {
                int marketSegmentId = Integer.parseInt(parts[0]);
                int token = Integer.parseInt(parts[1]);

                strTokenToSubscribe.append(String.format("1=%d$7=%d|", marketSegmentId, token));
            } catch (NumberFormatException e) {
                if (onErrorCallback != null) {
                    onErrorCallback.onError(String.format(
                            "Invalid token format: '%s'. Expected format: 'MarketSegmentID_Token'.", item));
                }
                continue;
            }
        }

        if (strTokenToSubscribe.length() > 0) {
            try {
                String currentTime = formatTime(new Date());
                String tlRequest = String.format(
                        "63=FT3.0|64=347|65=84|66=%s|%s230=2",
                        currentTime, strTokenToSubscribe.toString());
                sendMessage(tlRequest);
                System.out.println("Unsubscribed from LTP touchline tokens: " + String.join(", ", tokenList));
                future.complete(null);
            } catch (Exception e) {
                if (onErrorCallback != null) {
                    onErrorCallback.onError("Unsubscription failed: " + e.getMessage());
                }
                future.completeExceptionally(e);
            }
        } else {
            if (onErrorCallback != null) {
                onErrorCallback.onError("No valid tokens found to unsubscribe.");
            }
            future.completeExceptionally(new IllegalArgumentException("No valid tokens found to unsubscribe."));
        }

        return future;
    }

    /**
     * This method can be used to pause or resume the broadcast subscription for
     * user
     * when portal/app is in minimize mode or broadcast is not needed temporarily.
     * 
     * @param isPause true – Pause, false – Resume
     */
    public CompletableFuture<Void> subscribePauseResume(boolean isPause) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            String sIsPause = isPause ? "230=1" : "230=2";
            String currentTime = formatTime(new Date());
            String tlRequest = String.format(
                    "63=FT3.0|64=106|65=84|66=%s|%s",
                    currentTime, sIsPause);

            sendMessage(tlRequest);
            System.out.println((isPause ? "Pause " : "Resume ") + "request sent");
            future.complete(null);
        } catch (Exception e) {
            if (onErrorCallback != null) {
                onErrorCallback.onError("Pause/Resume failed: " + e.getMessage());
            }
            future.completeExceptionally(e);
        }

        return future;
    }

    // Helper methods
    private boolean isNullOrWhiteSpace(String str) {
        return str == null || str.trim().isEmpty();
    }

    private String formatTime(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(date);
    }

    private String formatDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HHmmss");
        return sdf.format(date);
    }

    public void sendMessage(String message) throws IOException {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            throw new IllegalStateException("WebSocket is not connected");
        }

        System.out.println("Sending Message: " + message);
        byte[] packet = fragHandler.fragmentData(message.getBytes(StandardCharsets.US_ASCII));
        webSocketClient.send(packet);
    }

    private void responseReceived(byte[] data) {
        try {
            List<byte[]> arrData = fragHandler.defragment(data);

            for (byte[] packet : arrData) {
                String strMsg = new String(packet, StandardCharsets.US_ASCII);

                if (strMsg.indexOf("|50=") >= 0) {
                    int dataIndex = strMsg.indexOf("|50=") + 4;
                    StringBuilder strNewMsg = new StringBuilder(strMsg.substring(0, strMsg.indexOf("|50=") + 1));

                    ByteBuffer buffer = ByteBuffer.wrap(packet).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    buffer.position(dataIndex);

                    int mktSegId = buffer.getInt();
                    strNewMsg.append("1=").append(mktSegId).append("|");

                    int token = buffer.getInt();
                    strNewMsg.append("7=").append(token).append("|");

                    int lutSeconds = buffer.getInt();
                    Date lutDate = new Date(dteNSE.getTimeInMillis() + lutSeconds * 1000L);
                    String lut = formatDate(lutDate);
                    strNewMsg.append("74=").append(lut).append("|");

                    int lttSeconds = buffer.getInt();
                    Date lttDate = new Date(dteNSE.getTimeInMillis() + lttSeconds * 1000L);
                    String ltt = formatDate(lttDate);
                    strNewMsg.append("73=").append(ltt).append("|");

                    int ltp = buffer.getInt();
                    strNewMsg.append("8=").append(ltp).append("|");

                    int bQty = buffer.getInt();
                    strNewMsg.append("2=").append(bQty).append("|");

                    int bPrice = buffer.getInt();
                    strNewMsg.append("3=").append(bPrice).append("|");

                    int sQty = buffer.getInt();
                    strNewMsg.append("5=").append(sQty).append("|"); // Note: Original uses bQty

                    int sPrice = buffer.getInt();
                    strNewMsg.append("6=").append(sPrice).append("|"); // Note: Original uses bPrice

                    int oPrice = buffer.getInt();
                    strNewMsg.append("75=").append(oPrice).append("|"); // Note: Original uses bQty

                    int hPrice = buffer.getInt();
                    strNewMsg.append("77=").append(hPrice).append("|"); // Note: Original uses bPrice

                    int lPrice = buffer.getInt();
                    strNewMsg.append("78=").append(lPrice).append("|"); // Note: Original uses bQty

                    int cPrice = buffer.getInt();
                    strNewMsg.append("76=").append(cPrice).append("|"); // Note: Original uses bPrice

                    int decLocator = buffer.getInt();
                    strNewMsg.append("399=").append(decLocator).append("|");

                    int prvClosePrice = buffer.getInt();
                    strNewMsg.append("250=").append(prvClosePrice).append("|");

                    int indicativeClosePrice = buffer.getInt();
                    strNewMsg.append("88=").append(indicativeClosePrice).append("|");

                    strMsg = strNewMsg.toString();
                }

                if (onMessageCallback != null) {
                    onMessageCallback.onMessage(strMsg);
                }
            }
        } catch (Exception error) {
            System.err.println("Error processing response: " + error.getMessage());
        }
    }

    public void dispose() {
        if (!isDisposed) {
            if (webSocketClient != null) {
                webSocketClient.close();
            }
            isDisposed = true;
        }
    }

}