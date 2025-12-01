@echo off
REM Build script for ODIN Market Feed Client (Windows)
echo ========================================
echo Building ODIN Market Feed Client...
echo ========================================
echo.

REM Create build directory
echo [1/4] Creating build directories...
if not exist build\classes mkdir build\classes
if not exist build\jar mkdir build\jar
echo Done.
echo.

REM Compile Java source
echo [2/4] Compiling Java sources...
if exist "lib\Java-WebSocket-1.5.3.jar" (
    javac -d build\classes -cp "lib\Java-WebSocket-1.5.3.jar" src\main\java\com\trading\ODINMarketFeedClient.java
    if errorlevel 1 (
        echo ERROR: Compilation failed!
        pause
        exit /b 1
    )
) else (
    echo WARNING: Java-WebSocket-1.5.3.jar not found in lib\ directory
    echo Compiling without dependencies...
    javac -d build\classes src\main\java\com\trading\ODINMarketFeedClient.java 2>nul
    if errorlevel 1 (
        echo ERROR: Please download Java-WebSocket-1.5.3.jar to lib\ directory
        echo Download from: https://repo1.maven.org/maven2/org/java-websocket/Java-WebSocket/1.5.3/Java-WebSocket-1.5.3.jar
        pause
        exit /b 1
    )
)
echo Done.
echo.

REM Create JAR file
echo [3/4] Creating JAR file...
cd build\classes
jar cvf ..\jar\odin-market-feed-client-1.0.0.jar com\ >nul 2>&1
cd ..\..
if exist "build\jar\odin-market-feed-client-1.0.0.jar" (
    echo Created: odin-market-feed-client-1.0.0.jar
) else (
    echo ERROR: Failed to create JAR file
    pause
    exit /b 1
)
echo.

REM Create JAR with dependencies (if dependency exists)
if exist "lib\Java-WebSocket-1.5.3.jar" (
    echo [4/4] Creating JAR with dependencies...
    if not exist build\jar-with-deps mkdir build\jar-with-deps
    cd build\jar-with-deps
    
    echo Extract dependency JAR
    REM Extract dependency JAR
    jar xf ..\..\lib\Java-WebSocket-1.5.3.jar >nul 2>&1
    
    echo Copy compiled classes
    REM Copy compiled classes
    xcopy /E /I /Q ..\classes\com com\ >nul 2>&1
    
    echo Create fat JAR
    REM Create fat JAR
    jar cvf ..\jar\odin-market-feed-client-1.0.0-jar-with-dependencies.jar . >nul 2>&1
    cd ..\..
    
    if exist "build\jar\odin-market-feed-client-1.0.0-jar-with-dependencies.jar" (
        echo Created: odin-market-feed-client-1.0.0-jar-with-dependencies.jar
    ) else (
        echo WARNING: Failed to create JAR with dependencies
    )
) else (
    echo [4/4] Skipping JAR with dependencies (Java-WebSocket not found)
)
echo.

REM Display results
echo ========================================
echo Build complete!
echo ========================================
echo.
echo JAR files created in build\jar\:
echo.
dir build\jar\*.jar
echo.
echo ========================================
echo Next steps:
echo 1. Test the JAR file
echo 2. Commit to Git: git add . ^&^& git commit -m "Build v1.0.0"
echo 3. Push to GitHub: git push origin main
echo 4. Create release and upload JARs
echo ========================================
echo.
pause