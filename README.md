# ODIN Market Feed Client

A Java WebSocket client library for connecting to ODIN Market Feed with built-in support for compression, fragmentation, and real-time market data streaming.

## Features

- 🚀 WebSocket-based real-time market data streaming
- 🗜️ Built-in ZLIB compression/decompression
- 📦 Automatic message fragmentation and defragmentation
- 🔄 Subscribe/Unsubscribe to market data feeds
- ⏸️ Pause/Resume subscription support
- 🔌 Connection management with auto-reconnect capabilities
- 📊 Support for LTP (Last Traded Price) and Touchline data

## Requirements

- Java 11 or higher
- Maven 3.6 or higher

## Installation

### Maven

Add this dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.trading</groupId>
    <artifactId>odin-market-feed-client</artifactId>
    <version>1.0.0</version>
    <scope>system</scope>
</dependency>
<dependency>
    <groupId>org.java-websocket</groupId>
    <artifactId>Java-WebSocket</artifactId>
    <version>1.5.3</version>
</dependency>
```

### Gradle

Add this to your `build.gradle`:

```gradle
implementation 'com.trading:odin-market-feed-client:1.0.0'
```

### Build from Source

```bash
git clone https://github.com/SIPL-Dev/java-odinmarketfeedclient.git
cd odin-market-feed-client
mvn clean install
```

## Usage

### Basic Connection

```java
import com.trading.ODINMarketFeedClient;
import java.util.Arrays;
import java.util.List;

public class Example {
    public static void main(String[] args) {
        // Create client instance
        ODINMarketFeedClient client = new ODINMarketFeedClient();
        
        // Set callbacks
        client.setOnConnectCallback(() -> {
            System.out.println("Connected to market feed!");
        });
        
        client.setOnMessageCallback((message) -> {
            System.out.println("Received: " + message);
        });
        
        client.setOnErrorCallback((error) -> {
            System.err.println("Error: " + error);
        });
        
        client.setOnDisconnectCallback(() -> {
            System.out.println("Disconnected from market feed");
        });
        
        // Connect to the server
        String wsUrl = "wss://your-market-feed-url.com";
        client.connect(wsUrl);
        
        // Wait for connection
        Thread.sleep(2000);
        
        // Subscribe to tokens
        List<String> tokens = Arrays.asList("1_26000", "1_26009");
        client.subscribeLTPTouchline(tokens);
    }
}
```


## Thread Safety

The client uses `CompletableFuture` for asynchronous operations. Ensure proper synchronization when accessing shared resources from callbacks.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For issues, questions, or contributions, please open an issue on GitHub.

## Changelog

### Version 1.0.0
- Initial release
- WebSocket connection management
- LTP Touchline subscription support
- Compression and fragmentation handling
- Pause/Resume functionality

## Acknowledgments

- Built with [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket) library
- Uses ZLIB compression for efficient data transfer


