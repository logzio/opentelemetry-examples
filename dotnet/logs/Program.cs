using System.Globalization;

using Microsoft.AspNetCore.Mvc;

using OpenTelemetry.Logs;
using OpenTelemetry.Resources;
using OpenTelemetry.Exporter;

var builder = WebApplication.CreateBuilder(args);

const string serviceName = "roll-dice";
const string logzioEndpoint = "<<LOGZIO_OTLP_LISTENER>>"; // e.g. https://otlp-listener.logz.io/v1/logs
const string logzioToken = "<LOGZIO_SHIPPING_TOKEN>";

builder.Logging.AddOpenTelemetry(options =>
{
    options
        .SetResourceBuilder(
            ResourceBuilder.CreateDefault()
                .AddService(serviceName))
        .AddOtlpExporter(otlpOptions =>
        {
            otlpOptions.Endpoint = new Uri(logzioEndpoint);
            otlpOptions.Headers = $"Authorization=Bearer {logzioToken}";
            otlpOptions.Protocol = OtlpExportProtocol.HttpProtobuf;
        });
});

// Additional filter to remove unwanted logs
builder.Logging.AddFilter("Microsoft.Hosting.Lifetime", LogLevel.Warning);

var app = builder.Build();


string HandleRollDice([FromServices]ILogger<Program> logger, string? player)
{
    var result = RollDice();

    if (string.IsNullOrEmpty(player))
    {
        logger.LogInformation("Anonymous player is rolling the dice: {result}", result);
        // logger.LogInformation("Anonymous player is rolling the dice", result);

    }
    else
    {
        logger.LogInformation("{player} is rolling the dice: {result}", player, result);
        // logger.LogInformation("someone I know is rolling the dice", player, result);

    }

    return result.ToString(CultureInfo.InvariantCulture);
}

int RollDice()
{
    return Random.Shared.Next(1, 7);
}

app.MapGet("/rolldice/{player?}", HandleRollDice);

app.Run();