using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using ModelContextProtocol.Server;
using System.ComponentModel;

var builder = Host.CreateApplicationBuilder(args);

builder.Services
    .AddMcpServer()
    .WithStdioServerTransport()
    .WithTools<TextTools>();

builder.Logging.AddConsole(options =>
{
    options.LogToStandardErrorThreshold = LogLevel.Trace;
});

await builder.Build().RunAsync();

[McpServerToolType]
public static class TextTools
{
    [McpServerTool(Name = "count_words"), Description("Count the number of words in a text.")]
    public static string CountWords(
        [Description("The text to count words in.")] string text)
    {
        var count = string.IsNullOrWhiteSpace(text)
            ? 0
            : text.Split((char[]?)null, StringSplitOptions.RemoveEmptyEntries).Length;

        return $"The text contains {count} words.";
    }

    [McpServerTool(Name = "reverse_text"), Description("Reverse a string of text.")]
    public static string ReverseText(
        [Description("The text to reverse.")] string text)
    {
        var characters = text.ToCharArray();
        Array.Reverse(characters);
        return new string(characters);
    }
}
