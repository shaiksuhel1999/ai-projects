from google import genai
from google.genai import types
from mcp_client import MCPClient
import asyncio

MY_API_KEY = "AAAAAAKJHNJYHBJYHYHB"
client = genai.Client(api_key=MY_API_KEY)
mcp = MCPClient()

WEATHER_TOOL = types.FunctionDeclaration(
    name="get_weather",
    description="Get current weather",
    parameters={
        "type": "object",
        "properties": {"city": {"type": "string"}},
        "required": ["city"],
    },
)

TOOLS = [types.Tool(function_declarations=[WEATHER_TOOL])]


async def ask_agent(question: str) -> str:
    response = client.models.generate_content(
        model="gemini-2.5-flash",
        contents=question,
        config=types.GenerateContentConfig(tools=TOOLS),
    )

    part = response.candidates[0].content.parts[0]

    if part.function_call:
        result = await mcp.call_tool(
            part.function_call.name,
            part.function_call.args,
        )

        followup = client.models.generate_content(
            model="gemini-2.5-flash",
            contents=[
                question,
                types.Content(
                    role="tool",
                    parts=[
                        types.Part(
                            function_response=types.FunctionResponse(
                                name=part.function_call.name,
                                response={"result": result},
                            )
                        )
                    ],
                ),
            ],
        )

        return followup.text

    return response.text

async def main():
    while True:
        q = input("\nAsk a question (or 'exit'): ")
        if q.lower() == "exit":
            break

        answer = await ask_agent(q)
        print("\nAnswer:\n", answer)

if __name__ == "__main__":
    asyncio.run(main())
