$ErrorActionPreference = "Stop"
$zipUrl = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip"
$zipFile = ".mvn\maven.zip"
$dest = ".mvn\maven"

if (-not (Test-Path "$dest\apache-maven-3.9.6\bin\mvn.cmd")) {
    Write-Host "Downloading Apache Maven 3.9.6..."
    Invoke-WebRequest -Uri $zipUrl -OutFile $zipFile
    Write-Host "Extracting to $dest..."
    Expand-Archive -Path $zipFile -DestinationPath $dest -Force
    Remove-Item $zipFile -Force
    Write-Host "Maven installed in $dest\apache-maven-3.9.6"
} else {
    Write-Host "Maven already present."
}
