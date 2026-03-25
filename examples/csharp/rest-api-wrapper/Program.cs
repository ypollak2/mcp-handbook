using HackerNewsServer.Tools;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using System.Net.Http.Headers;

var builder = Host.CreateApplicationBuilder(args);

builder.Services
    .AddMcpServer()
    .WithStdioServerTransport()
    .WithTools<NewsTools>();

builder.Logging.AddConsole(options =>
{
    options.LogToStandardErrorThreshold = LogLevel.Trace;
});

using var httpClient = new HttpClient
{
    BaseAddress = new Uri("https://hacker-news.firebaseio.com/v0"),
    Timeout = TimeSpan.FromSeconds(10),
};

httpClient.DefaultRequestHeaders.UserAgent.Add(
    new ProductInfoHeaderValue("mcp-handbook-hackernews", "1.0"));

builder.Services.AddSingleton(httpClient);

await builder.Build().RunAsync();
