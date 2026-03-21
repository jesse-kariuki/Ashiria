pub mod allocations;
pub mod leaks;

use crate::proto::AgentEvent;
use allocations::AllocationTracker;
use leaks::LeakDetector;
use flume::Receiver;
use std::sync::Arc;

pub struct EngineState {
    pub tracker:  AllocationTracker,
    pub leaks:    LeakDetector,
    pub start_time: std::time::Instant,
}

impl EngineState {
    pub fn new() -> Self {
        Self {
            tracker:    AllocationTracker::default(),
            leaks:      LeakDetector::default(),
            start_time: std::time::Instant::now(),
        }
    }
}


pub async fn run(rx: Receiver<AgentEvent>, state: Arc<EngineState>) {
    let mut events_processed: u64 = 0;
    let mut last_log = std::time::Instant::now();

    loop {
        // recv_async().await blocks until an event is available
        let Ok(event) = rx.recv_async().await else { break };

        match event {
            AgentEvent::ObjectAllocation(e) => {
                state.tracker.record(&e);
            }
            AgentEvent::HeapSample(_) | AgentEvent::GcNotification(_) => {

            }
            _ => {}
        }

        events_processed += 1;


        if last_log.elapsed().as_secs() >= 10 {
            tracing::info!(
                "Events: {}  |  Classes tracked: {}",
                events_processed,
                state.tracker.per_class.len()
            );
            last_log = std::time::Instant::now();
        }
    }
}
