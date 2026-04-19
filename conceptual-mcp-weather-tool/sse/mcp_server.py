import requests
from fastmcp import FastMCP

mcp = FastMCP(name="Weather MCP Server")


@mcp.tool
def get_weather(city: str) -> str:
    response = requests.get(
        f"https://wttr.in/{city}",
        params={"format": "j1"},
        timeout=10,
    )
    data = response.json()
    current = data["current_condition"][0]

    return (
        f"City: {city}\n"
        f"Temperature: {current['temp_C']}°C\n"
        f"Condition: {current['weatherDesc'][0]['value']}"
    )


if __name__ == "__main__":
    # 🔥 STDIO transport
    mcp.run(transport="sse", port=6000)
