[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$url = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse"
$output = "C:\Users\Administrator\Downloads\jdk21.zip"
$extractPath = "C:\Program Files\JDK"

Write-Host "Downloading JDK 21..."
Invoke-WebRequest -Uri $url -OutFile $output -UseBasicParsing

Write-Host "Extracting JDK 21..."
Expand-Archive -Path $output -DestinationPath $extractPath -Force

Write-Host "Cleaning up..."
Remove-Item $output -Force

Write-Host "JDK 21 installed successfully!"
Get-ChildItem -Path $extractPath -Directory
