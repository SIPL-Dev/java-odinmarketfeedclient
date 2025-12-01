package com.trading;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Example usage of ODINMarketFeedClient
 */
public class Example {
    public static void main(String[] args) {

        System.out.println("═══════════════════════════════════════");
        System.out.println("🚀 Market Feed WebSocket Java Application");
        System.out.println("═══════════════════════════════════════");
        System.out.println();

        ODINMarketFeedClient client = new ODINMarketFeedClient();

        // Set up event handlers
        client.setOnOpen(() -> {
            System.out.println("✅ WebSocket opened");
            //subscribeToTokens(client);
            subscribeToTokensAsync(client);
        });

        client.setOnMessage((message) -> {
            System.out.println("📨 Received: " + message);
        });

        client.setOnError((error) -> {
            System.err.println("❌ Error: " + error);
        });

        client.setOnClose((code, reason) -> {
            System.out.println("🔌 Closed: " + code + " - " + reason);
        });

        try {
            // CHANGE THESE VALUES TO YOUR SERVER
            System.out.println("🔗 Connecting to server...\n");
            client.connect("YOUR-SERVER-IP", 4509, false, "TESTCLIENT", "").get();

            // Send custom message
            // client.sendMessage("your|custom|message");

            // Example: Subscribe to tokens
            // Keep connection alive
            System.out.println("\n💤 Keeping connection alive for 60 seconds...\n");
            TimeUnit.SECONDS.sleep(360);

        } catch (Exception e) {
            System.err.println("💥 Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                System.out.println("\n🔌 Disconnecting...");
                client.disconnect();
                client.dispose();
                System.out.println("👋 Application closed");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Subscribe to tokens
     * Format: "MarketSegmentID_Token,MarketSegmentID_Token,..."
     * Example: "1_22,1_2885"
     */
    public static void subscribeToTokens(ODINMarketFeedClient client) {
        try {
            List<String> tokens = Arrays.asList("1_22", "1_2885");
            // Touchline subscriptions (matching your Go code)
            client.subscribeTouchline(tokens, "0", false);
            // client.subscribeTouchline(tokens, "0", true);
            // client.subscribeTouchline(tokens, "1", false);
            // client.subscribeTouchline(tokens, "1", true);
            // Best Five subscription
            // client.subscribeBestFive("2885", 1);
            // LTP Touchline
            // client.subscribeLTPTouchline(tokens);
            TimeUnit.SECONDS.sleep(15);
            // Pause/Resume
            client.subscribePauseResume(true);
            TimeUnit.SECONDS.sleep(5);
            client.subscribePauseResume(false);
            TimeUnit.SECONDS.sleep(10);
            client.unsubscribeLTPTouchline(tokens);

        } catch (Exception e) {
            System.err.println("❌ Subscription error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static CompletableFuture<Void> subscribeToTokensAsync(ODINMarketFeedClient client) {
        return CompletableFuture.runAsync(() -> {
            try {
                
                List<String> tokens = Arrays.asList("1_22", "1_2885");
                // Touchline subscriptions (matching your Go code)
                client.subscribeTouchline(tokens, "0", false);
                // client.subscribeTouchline(tokens, "0", true);
                // client.subscribeTouchline(tokens, "1", false);
                // client.subscribeTouchline(tokens, "1", true);
                // Best Five subscription
                // client.subscribeBestFive("2885", 1);
                // LTP Touchline
                // client.subscribeLTPTouchline(tokens);
                //TimeUnit.SECONDS.sleep(15);
                // Pause/Resume
                //client.subscribePauseResume(true);
                //TimeUnit.SECONDS.sleep(5);
                //client.subscribePauseResume(false);
                TimeUnit.SECONDS.sleep(10);
                client.unsubscribeTouchline(tokens);

            } catch (Exception e) {
                System.err.println("❌ Subscription error: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
    }

}
