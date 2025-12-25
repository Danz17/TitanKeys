@echo off
echo ========================================
echo Compilazione e installazione TitanKeys
echo ========================================
echo.

REM Check if ADB is available
where adb >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo WARNING: ADB not found in PATH
    echo Continuing with build only (install will be skipped)
    set SKIP_INSTALL=1
) else (
    REM Check if device is connected (USB or wireless)
    adb devices | findstr /R "device$" >nul
    if %ERRORLEVEL% NEQ 0 (
        adb devices | findstr /R "[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*:[0-9][0-9]*" >nul
        if %ERRORLEVEL% NEQ 0 (
            echo No device connected. Attempting wireless ADB connection...
            if exist tools\adb\wireless_adb_connect.bat (
                call tools\adb\wireless_adb_connect.bat
            ) else (
                echo WARNING: wireless_adb_connect.bat not found
                echo Please connect device manually or run: adb connect ^<IP^>:^<PORT^>
            )
        )
    )
)

REM Compila e installa l'app
call gradlew.bat installDebug

REM Verifica se l'installazione è riuscita
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo Installazione completata con successo!
    echo ========================================
    echo.
    echo Avvio dell'app sul dispositivo...
    
    REM Lancia l'app sul dispositivo Android
    adb shell am start -n com.titankeys.keyboard/.MainActivity
    
    if %ERRORLEVEL% EQU 0 (
        echo.
        echo App avviata con successo!
        exit /b 0
    ) else (
        echo.
        echo ERRORE: Impossibile avviare l'app.
        echo Verifica che il dispositivo sia connesso e che ADB sia configurato correttamente.
        exit /b %ERRORLEVEL%
    )
) else (
    echo.
    echo ERRORE: La compilazione/installazione non è riuscita.
    echo Verifica gli errori sopra.
    exit /b %ERRORLEVEL%
)

