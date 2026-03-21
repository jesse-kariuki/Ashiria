use crate::proto::AllocationEvent;
use dashmap::DashMap;
use serde::{Deserialize, Serialize};
use std::sync::atomic::{AtomicU64, Ordering};

#[derive(Default)]
pub struct ClassStats {
    pub total_count: AtomicU64,
}

impl ClassStats {
    pub fn record(&self) {

        self.total_count.fetch_add(1, Ordering::Relaxed);
    }
    pub fn total(&self) -> u64 { self.total_count.load(Ordering::Relaxed) }
}

#[derive(Default)]
pub struct AllocationTracker {
    pub per_class:    DashMap<String, ClassStats>,
    pub per_callsite: DashMap<String, AtomicU64>,
    pub total_events: AtomicU64,
}

impl AllocationTracker {
    pub fn record(&self, event: &AllocationEvent) {
        self.total_events.fetch_add(1, Ordering::Relaxed);

        self.per_class
            .entry(event.class_name.clone())
            .or_default()
            .record();

        let site = format!("{}.{}",
                           event.allocating_class, event.allocating_method);
        self.per_callsite
            .entry(site)
            .or_insert_with(|| AtomicU64::new(0))
            .fetch_add(1, Ordering::Relaxed);
    }

    pub fn top_by_total(&self, n: usize) -> Vec<ClassSummary> {
        let mut v: Vec<_> = self.per_class.iter().map(|e| ClassSummary {
            class_name: e.key().clone(),
            total_allocations: e.value().total(),
        }).collect();
        v.sort_by(|a, b| b.total_allocations.cmp(&a.total_allocations));
        v.truncate(n);
        v
    }
}

#[derive(Serialize, Deserialize, Clone)]
pub struct ClassSummary {
    pub class_name: String,
    pub total_allocations: u64,
}
