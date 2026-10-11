@echo off
setlocal

echo ===================================================
echo [START] Running OpenMathPad Quality and Security Analysis
echo ===================================================

if not exist "analysis_reports\latest" mkdir "analysis_reports\latest"

echo.
echo [1/2] Running Android Lint (:app:lintDebug)...
call gradlew.bat :app:lintDebug
if errorlevel 1 (
    echo [FAIL] Android Lint analysis encountered issues.
    set LINT_STATUS=1
) else (
    echo [PASS] Android Lint analysis passed cleanly.
    set LINT_STATUS=0
)

echo.
echo [2/2] Running Unit Tests (:app:testDebugUnitTest)...
call gradlew.bat :app:testDebugUnitTest
if errorlevel 1 (
    echo [FAIL] Unit tests failed.
    set TEST_STATUS=1
) else (
    echo [PASS] Unit tests passed cleanly.
    set TEST_STATUS=0
)

echo.
echo [REPORT] Exporting analysis reports to analysis_reports\latest...
if exist "app\build\reports" (
    xcopy /E /I /Y "app\build\reports" "analysis_reports\latest" >nul
    echo [REPORT] Reports copied successfully to analysis_reports\latest.
) else (
    echo [WARN] No reports directory found in app\build\reports.
)

echo.
echo ===================================================
if "%LINT_STATUS%"=="0" if "%TEST_STATUS%"=="0" (
    echo [PASS] ALL ANALYSES AND TESTS PASSED SUCCESSFULLY!
    echo ===================================================
    exit /b 0
)

echo [FAIL] ANALYSIS COMPLETED WITH FAILURES.
echo ===================================================
if not "%TEST_STATUS%"=="0" exit /b 1
exit /b 1
