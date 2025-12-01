# Build Instructions

This guide will help you build the JAR file for the ODIN Market Feed Client.

## Prerequisites

- Java Development Kit (JDK) 11 or higher
- Maven 3.6 or higher

## Quick Build

### Using Maven (Recommended)

```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/odin-market-feed-client.git
cd odin-market-feed-client

# Build the JAR
mvn clean package

# The JAR files will be in the target/ directory
ls target/*.jar
```

This will create:
- `odin-market-feed-client-1.0.0.jar` - Main library JAR
- `odin-market-feed-client-1.0.0-jar-with-dependencies.jar` - JAR with all dependencies included

### Using Gradle

If you prefer Gradle, create a `build.gradle` file:

```gradle
plugins {
    id 'java'
}

group = 'com.trading'
version = '1.0.0'
sourceCompatibility = '11'

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.java-websocket:Java-WebSocket:1.5.3'
}

jar {
    manifest {
        attributes(
            'Implementation-Title': 'ODIN Market Feed Client',
            'Implementation-Version': version
        )
    }
}

task fatJar(type: Jar) {
    archiveClassifier = 'with-dependencies'
    from {
        configurations.runtimeClasspath.collect { it.isDirectory() ? it : zipTree(it) }
    }
    with jar
}
```

Then build:
```bash
./gradlew build
./gradlew fatJar
```

## Manual Build (Without Maven/Gradle)

If you don't have Maven or Gradle installed:

### Step 1: Download Dependencies

Download the Java-WebSocket library:
- Go to: https://repo1.maven.org/maven2/org/java-websocket/Java-WebSocket/1.5.3/
- Download: `Java-WebSocket-1.5.3.jar`
- Place it in a `lib/` directory

### Step 2: Compile

```bash
# Create output directory
mkdir -p build/classes

# Compile the source files
javac -d build/classes \
      -cp "lib/Java-WebSocket-1.5.3.jar" \
      src/main/java/com/trading/*.java

# Verify compilation
ls build/classes/com/trading/
```

### Step 3: Create JAR

```bash
# Create JAR without dependencies
jar cvf odin-market-feed-client-1.0.0.jar \
    -C build/classes .

# Create JAR with dependencies (recommended)
mkdir -p build/jar-contents
cd build/jar-contents

# Extract the library
jar xf ../../lib/Java-WebSocket-1.5.3.jar

# Copy compiled classes
cp -r ../classes/* .

# Create the JAR
jar cvf ../../odin-market-feed-client-1.0.0-with-dependencies.jar .

cd ../..
```

### Step 4: Create Manifest (Optional)

Create `MANIFEST.MF`:
```
Manifest-Version: 1.0
Implementation-Title: ODIN Market Feed Client
Implementation-Version: 1.0.0
Built-By: Your Name
```

Then create JAR with manifest:
```bash
jar cvfm odin-market-feed-client-1.0.0.jar MANIFEST.MF -C build/classes .
```

## Verify the JAR

```bash
# List contents
jar tf odin-market-feed-client-1.0.0.jar

# Run a test (if you have a main class)
java -cp odin-market-feed-client-1.0.0-with-dependencies.jar \
     com.trading.Example
```

## Build Output

After building, you should have:

```
target/
├── odin-market-feed-client-1.0.0.jar                     # Library only
├── odin-market-feed-client-1.0.0-with-dependencies.jar  # With dependencies
├── odin-market-feed-client-1.0.0-sources.jar            # Source code
└── odin-market-feed-client-1.0.0-javadoc.jar            # Documentation
```

## Using the JAR in Your Project

### Maven

```xml
<dependency>
    <groupId>com.trading</groupId>
    <artifactId>odin-market-feed-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```gradle
implementation 'com.trading:odin-market-feed-client:1.0.0'
```

### Directly

```bash
java -cp odin-market-feed-client-1.0.0-with-dependencies.jar:your-app.jar \
     com.yourcompany.YourMainClass
```

## Troubleshooting

### Maven Build Fails

```bash
# Clean Maven cache
mvn clean

# Force update dependencies
mvn clean package -U

# Skip tests
mvn clean package -DskipTests
```

### Java Version Issues

```bash
# Check Java version
java -version
javac -version

# Should be 11 or higher
```

### Dependency Issues

If WebSocket library is not found:
```bash
# Download manually from Maven Central
# Place in ~/.m2/repository/org/java-websocket/Java-WebSocket/1.5.3/
```

## Clean Build

```bash
# Maven
mvn clean

# Manual
rm -rf build/ target/ *.jar
```

## Next Steps

After building, see [RELEASE.md](RELEASE.md) for instructions on publishing to GitHub.
