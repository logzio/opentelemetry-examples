<?php

// Set OpenTelemetry environment variables programmatically
putenv('OTEL_PHP_AUTOLOAD_ENABLED=true');
putenv('OTEL_LOGS_EXPORTER=otlp');
putenv('OTEL_EXPORTER_OTLP_LOGS_PROTOCOL=http/protobuf');
putenv('OTEL_EXPORTER_OTLP_LOGS_ENDPOINT=<<LOGZIO_OTLP_LISTENER>>'); # // e.g. https://otlp-listener.logz.io/v1/logs
putenv('OTEL_EXPORTER_OTLP_LOGS_HEADERS=Authorization=Bearer <LOGZIO_SHIPPING_TOKEN>,user-agent=logzio-php-logs-otlp');
putenv('OTEL_RESOURCE_ATTRIBUTES=service.name=rolldice');


use Monolog\Logger;
use OpenTelemetry\Contrib\Logs\Monolog\Handler as MonologHandler;
use OpenTelemetry\SDK\Logs\LoggerProviderFactory;

use Psr\Http\Message\ResponseInterface as Response;
use Psr\Http\Message\ServerRequestInterface as Request;
use Psr\Log\LogLevel;
use Slim\Factory\AppFactory;

require __DIR__ . '/vendor/autoload.php';

//Initialize Logger
$loggerFactory = new LoggerProviderFactory();
$loggerProvider = $loggerFactory->create();
$handler = new MonologHandler(
    $loggerProvider,
    LogLevel::DEBUG,
);

//Initialize Monolog
$monolog = new Logger('logger', [$handler]);

$app = AppFactory::create();

$app->get('/rolldice', function (Request $request, Response $response) use ($monolog) {

    $result = random_int(1,6);
    $response->getBody()->write(strval($result));

    //log results
    $monolog->info('result rolled ' . strval($result));

    return $response;
});

$app->run();

//shutdown providers
$loggerProvider->shutdown();