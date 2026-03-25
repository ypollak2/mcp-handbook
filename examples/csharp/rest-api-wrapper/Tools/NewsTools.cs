using ModelContextProtocol;
using ModelContextProtocol.Server;
using System.ComponentModel;
using System.Net;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace HackerNewsServer.Tools;

[McpServerToolType]
public sealed class NewsTools
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true,
    };

    [McpServerTool(Name = "top_stories"), Description("Get the current top stories on Hacker News.")]
    public static async Task<string> TopStories(
        HttpClient client,
        [Description("Number of stories to return (max 30).")] int count = 10)
    {
        count = Math.Clamp(count, 1, 30);

        using var idsDocument = await client.ReadJsonDocumentAsync("/topstories.json");
        var ids = idsDocument.RootElement
            .EnumerateArray()
            .Take(count)
            .Select(item => item.GetInt32())
            .ToArray();

        var stories = new List<HackerNewsItem>();
        foreach (var id in ids)
        {
            var story = await GetItem(client, id);
            if (story is not null)
            {
                stories.Add(story);
            }
        }

        if (stories.Count == 0)
        {
            return "No stories found.";
        }

        return string.Join(
            "\n\n",
            stories.Select((story, index) =>
                $"{index + 1}. {story.Title ?? "(untitled)"} ({story.Score ?? 0} points)\n" +
                $"   {StoryUrl(story)}\n" +
                $"   by {story.By ?? "unknown"} | {story.Descendants ?? 0} comments"));
    }

    [McpServerTool(Name = "get_story"), Description("Get details about a specific Hacker News story including its top comments.")]
    public static async Task<string> GetStory(
        HttpClient client,
        [Description("Hacker News story ID.")] int id,
        [Description("Number of top comments to include.")] int commentCount = 5)
    {
        var story = await GetItem(client, id);
        if (story is null)
        {
            return $"Story {id} not found.";
        }

        var lines = new List<string>
        {
            $"# {story.Title ?? "(untitled)"}",
            string.Empty,
            $"Score: {story.Score ?? 0} | By: {story.By ?? "unknown"} | Comments: {story.Descendants ?? 0}",
            $"URL: {story.Url ?? "N/A"}",
            $"HN: https://news.ycombinator.com/item?id={story.Id}",
        };

        if (!string.IsNullOrWhiteSpace(story.Text))
        {
            lines.Add(string.Empty);
            lines.Add(StripHtml(story.Text));
        }

        if (story.Kids is { Length: > 0 })
        {
            lines.Add(string.Empty);
            lines.Add("--- Top Comments ---");
            lines.Add(string.Empty);

            foreach (var commentId in story.Kids.Take(Math.Max(commentCount, 0)))
            {
                var comment = await GetItem(client, commentId);
                if (comment is null || string.IsNullOrWhiteSpace(comment.Text))
                {
                    continue;
                }

                lines.Add($"[{comment.By ?? "unknown"}]:");
                lines.Add(StripHtml(comment.Text));
                lines.Add(string.Empty);
            }
        }

        return string.Join("\n", lines).TrimEnd();
    }

    [McpServerTool(Name = "search_user"), Description("Get information about a Hacker News user.")]
    public static async Task<string> SearchUser(
        HttpClient client,
        [Description("Hacker News username.")] string username)
    {
        try
        {
            using var userDocument = await client.ReadJsonDocumentAsync(
                $"/user/{Uri.EscapeDataString(username)}.json");

            if (userDocument.RootElement.ValueKind == JsonValueKind.Null)
            {
                return $"User \"{username}\" not found.";
            }

            var user = JsonSerializer.Deserialize<HackerNewsUser>(
                userDocument.RootElement.GetRawText(),
                JsonOptions);

            if (user is null)
            {
                return $"User \"{username}\" not found.";
            }

            var created = DateTimeOffset
                .FromUnixTimeSeconds(user.Created)
                .UtcDateTime
                .ToString("yyyy-MM-dd");

            var lines = new List<string>
            {
                $"User: {user.Id}",
                $"Karma: {user.Karma}",
                $"Member since: {created}",
                $"Submissions: {user.Submitted?.Length ?? 0}",
            };

            if (!string.IsNullOrWhiteSpace(user.About))
            {
                lines.Add(string.Empty);
                lines.Add("About:");
                lines.Add(StripHtml(user.About));
            }

            return string.Join("\n", lines);
        }
        catch
        {
            return $"User \"{username}\" not found.";
        }
    }

    private static async Task<HackerNewsItem?> GetItem(HttpClient client, int id)
    {
        try
        {
            using var itemDocument = await client.ReadJsonDocumentAsync($"/item/{id}.json");
            if (itemDocument.RootElement.ValueKind == JsonValueKind.Null)
            {
                return null;
            }

            return JsonSerializer.Deserialize<HackerNewsItem>(
                itemDocument.RootElement.GetRawText(),
                JsonOptions);
        }
        catch
        {
            return null;
        }
    }

    private static string StoryUrl(HackerNewsItem story)
    {
        return story.Url ?? $"https://news.ycombinator.com/item?id={story.Id}";
    }

    private static string StripHtml(string html)
    {
        var withoutTags = Regex.Replace(html, "<[^>]*>", string.Empty);
        return WebUtility.HtmlDecode(withoutTags);
    }

    private sealed record HackerNewsItem(
        int Id,
        string? Type,
        string? Title,
        string? Text,
        string? By,
        string? Url,
        int? Score,
        int? Descendants,
        long? Time,
        int[]? Kids);

    private sealed record HackerNewsUser(
        string Id,
        long Created,
        int Karma,
        string? About,
        int[]? Submitted);
}
