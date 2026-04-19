from fastmcp.client import Client
import asyncio

class MCPClient:
    def __init__(self):
        # ✅ Explicit sse transport
        self.client = Client("http://localhost:6000/sse/")

    async def call_tool(self, name: str, args: dict) -> str:
        async with self.client:
            print(f"Calling {name}")
            print(f"MCP client connected: {self.client.is_connected()}")
            tools = await self.client.list_tools()
            print(f"MCP client tools: {tools}")
            tool_found = any(tool_name.name == name for tool_name in tools)
            print(f"MCP client tool found: {tool_found}")
            if tool_found:
                print(f"MCP client tool found: {tools}")
                result = await self.client.call_tool(name, args)
                return result
            else:
                print(f"MCP client tool not found: {tools}")
            print(f"Mcp client is connected: {self.client.is_connected()}")
            return "None"

