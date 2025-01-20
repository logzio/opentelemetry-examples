use rand::Rng;
use opentelemetry_appender_log::OpenTelemetryLogBridge;
use opentelemetry_otlp::{WithExportConfig, WithHttpConfig};
use std::collections::HashMap;

fn roll_dice() -> i32 {
    let mut rng = rand::thread_rng();
    rng.gen_range(1..=6)
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error + Send + Sync + 'static>> {

    let endpoint = "https://otlp-listener.logz.io/v1/logs";
    let api_token = "LOGZ_IO_TOKEN";

    let logger_provider = opentelemetry_sdk::logs::LoggerProvider::builder()
        .with_batch_exporter(
            opentelemetry_otlp::LogExporter::builder()
                .with_http()
                .with_endpoint(endpoint)
                .with_headers(HashMap::from([
                    ("Authorization".to_string(), format!("Bearer {}", api_token)),
                    ("User-Agent".to_string(), format!("logzio-rust-logs-otlp"), ),
                ]))
                .build()?,
            opentelemetry_sdk::runtime::Tokio,
        )
        .build();
    
    let log_bridge = OpenTelemetryLogBridge::new(&logger_provider);

    log::set_boxed_logger(Box::new(log_bridge))?;
    log::set_max_level(log::LevelFilter::Info);

    let result = roll_dice();
    log::info!("Player is rolling the dice: {}", result);

    println!("Done");

    // Force flush any pending logs
    logger_provider.force_flush();

    Ok(())
}