<#
    Starts one NovaChain node.

    Examples (run each in its own terminal):
        ./scripts/start-node.ps1 -Port 8080
        ./scripts/start-node.ps1 -Port 8081 -Peers "http://localhost:8080"
        ./scripts/start-node.ps1 -Port 8082 -Peers "http://localhost:8080,http://localhost:8081"

    Each node gets its own embedded ledger at ./data/novachain-<port>.mv.db and its
    own wallet UI at http://localhost:<port>.
#>
param(
    [int]$Port = 8080,
    [string]$Peers = ""
)

$args = "--server.port=$Port"
if ($Peers -ne "") {
    $args += " --novachain.node.peers=$Peers"
}

Write-Host "Starting NovaChain node on port $Port" -ForegroundColor Cyan
if ($Peers -ne "") { Write-Host "  seed peers: $Peers" -ForegroundColor DarkGray }

mvn spring-boot:run "-Dspring-boot.run.arguments=$args"
