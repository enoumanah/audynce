 # set-env.ps1 - Load .env vars into PowerShell session
 $envPath = ".\.env"  # Adjust if .env is elsewhere

 if (Test-Path $envPath) {
     Get-Content $envPath | ForEach-Object {
         if ($_ -match '^\s*([^#=]+)=(.*)$') {  # Match key=value (ignore # comments)
             $key = $matches[1].Trim()
             $value = $matches[2].Trim().Replace('"', '')  # Strip quotes if present
             Set-Item -Path "env:$key" -Value $value
             Write-Host "Set $key = $value" -ForegroundColor Green
         }
     }
     Write-Host "All env vars loaded! Run './mvnw spring-boot:run' now." -ForegroundColor Yellow
 } else {
     Write-Error ".env not found at $envPath"
 }