# Changelog

All notable changes to the ODIN Market Feed Client will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planned
- Add support for more market data types
- Implement automatic reconnection with exponential backoff
- Add comprehensive unit tests
- Add integration tests
- Performance optimizations for high-frequency data

## [1.0.0] - 2024-11-27

### Added
- Initial release of ODIN Market Feed Client
- WebSocket connection management
- Subscribe to LTP (Last Traded Price) Touchline data
- Unsubscribe from market data feeds
- Pause and resume subscription functionality
- ZLIB compression and decompression support
- Automatic message fragmentation and defragmentation
- Connection callbacks (onConnect, onDisconnect, onMessage, onError)
- CompletableFuture support for asynchronous operations
- Comprehensive error handling
- Example usage code
- Maven project structure
- Full documentation

### Features
- Real-time market data streaming
- Support for multiple market segments
- Token-based subscription system
- Binary data handling with Little Endian byte order
- Automatic message parsing and formatting
- Thread-safe operations
- Memory-efficient stream handling

### Dependencies
- Java-WebSocket 1.5.3
- Java 11 or higher

### Documentation
- README.md with usage examples
- BUILD.md with build instructions
- RELEASE.md with publishing guide
- Inline code documentation
- MIT License

---

## Version History

- **1.0.0** - Initial public release (2024-11-27)

## Links

- [GitHub Repository](https://github.com/SIPL-Dev/java-odinmarketfeedclient)
- [Latest Release](https://github.com/SIPL-Dev/java-odinmarketfeedclient/releases/latest)
- [Issue Tracker](https://github.com/SIPL-Dev/java-odinmarketfeedclient/issues)

---

**Note:** Replace `YOUR_USERNAME` with your actual GitHub username.
