
mod protocol;
mod ingestion;
mod analysis;
mod api;

use std::sync::Arc;
use clap::Parser;

#[derive(Parser)]
struct Args {
    #[arg(long, default_value = "/tmp/memory-intel.sock")]
    socket: String,

    #[arg(long, default_value = "127.0.0.1:7777")]
    api: String,

    #[arg(long, default_value = "65536")]
    channel: usize,
}

// #[tokio::main] turns main() into an async function running on tokio's thread pool
#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Initialize logging
    tracing_subscriber::fmt::init();

    let args = Args::parse();

    // Shared analysis state — Arc allows multiple tasks to hold a reference
    let state = Arc::new(analysis::EngineState::new());

    // Start the Unix socket ingestion server
    // Returns the receiving end of the event channel
    let rx = ingestion::start(&args.socket, args.channel).await?;

    // Spawn the analysis task (runs concurrently on tokio's thread pool)
    let state2 = state.clone();  // Clone Arc: increments reference count
    tokio::spawn(analysis::run(rx, state2));

    // Serve the HTTP API (blocks until the process exits)
    api::serve(state, &args.api).await;

    Ok(())
}
