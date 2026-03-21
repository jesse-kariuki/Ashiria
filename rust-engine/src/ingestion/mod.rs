/*
* Listens on a Unix domain socket for the Java agent's event stream.
* Reads length-prefixed JSON frames and sends them into the analysis channel.
*/

use crate::protocol::{self, AgentEvent, MAX_FRAME_BYTES};
use flume::Sender;
use std::path::Path;
use tokio::io::{AsyncReadExt, BufReader};
use tokio::net::{UnixListener, UnixStream};
use tracing::{error, info, warn};

pub async fn start(
    socket_path: impl AsRef<Path>,
    channel_capacity: usize
)-> anyhow::Result<flume::Receiver<AgentEvent>> {
    let path = socket_path.as_ref().to_path_buf();
    if path.exists(){std::remove_file(&path)?;}
    let listener = UnixListener::bind(&path)?;

    info!("Listening for java agent on {:?}", &path);
    let (tx, rx) = flume::bounded(channel_capacity);

    tokio::spawn(accept_loop(listener, tx));

    Ok(rx)


}

async fn accept_loop(listener: UnixListener, tx: Sender<AgentEvent>) {
    loop {
        match listener.accept().await {
            Ok((stream, _)) =>{
                info!("Java agent connected");
                tokio::spawn(handle_connection(stream, tx.clone()));
            }
            Err(e) => {
                error!("Accept error: {}", e);
                tokio::time::sleep(
                    std::time::Duration::from_millis(100)).await;
            }
        }
    }
}

async fn handle_connection(stream: UnixStream, tx: Sender<AgentEvent>) {
    let mut reader = BufReader::new(stream);
    let mut len_buf = [0u8; 4];
    let mut received = 0u64;

    loop {
        match reader.read_exact(&mut len_buf).await {
            Ok(_) => {}
            Err(e) if e.kind() == std::io::ErrorKind::UnexpectedEof => {
                info!("Agent disconnected after {} events", received);
                return;
            }
            Err(e) => { error!("Read error: {}", e); return; }
        }

        let len = u32::from_le_bytes(len_buf) as usize;
        if len == 0 { continue; }
        if len > MAX_FRAME_BYTES {
            warn!("Oversized frame: {} bytes, dropping connection", len);
            return;
        }
        let mut payload = vec![0u8; len];
        if let Err(e) = reader.read_exact(&mut payload).await {
            error!("Payload read error: {}", e);
            return;
        }

        match serde_json::from_slice::<AgentEvent>(&payload) {
            Ok(event) => {
                received += 1;
                if tx.try_send(event).is_err() {
                    warn!("Analysis channel full — dropping event");
                }
            }
            Err(e) => warn!("JSON decode error: {}", e),
        }

    }
}