use dashmap::DashMap;
use serde::{Deserialize, Serialize};
use std::time::Instant;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, PartialOrd)]
pub enum LeakConfidence { Low, Medium, High, Critical }

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LeakSuspect {
    pub class_name:       String,
    pub confidence:       LeakConfidence,
    pub evidence:         Vec<String>,
    pub total_allocations: u64,
}

struct ClassLeakState {
    snapshots:    Vec<(Instant, u64)>,  // (time, total count)
    rate_history: Vec<f64>,
}

pub struct LeakDetector {
    states:   DashMap<String, ClassLeakState>,
    suspects: DashMap<String, LeakSuspect>,
}

impl LeakDetector {
    pub fn tick(&self, class_name: &str, total_count: u64, rate: f64) {
        let mut state = self.states.entry(class_name.to_string())
            .or_insert_with(|| ClassLeakState {
                snapshots: Vec::new(), rate_history: Vec::new()
            });

        state.snapshots.push((Instant::now(), total_count));
        if state.snapshots.len() > 30 { state.snapshots.remove(0); }
        state.rate_history.push(rate);
        if state.rate_history.len() > 10 { state.rate_history.remove(0); }

        self.evaluate(class_name, &state, total_count, rate);
    }

    fn evaluate(&self, name: &str, state: &ClassLeakState, total: u64, rate: f64) {
        if state.snapshots.len() < 5 { return; }  // Need enough history

        let mut score: u8 = 0;
        let mut evidence: Vec<String> = Vec::new();

        // Heuristic 1: Count never decreases (GC not reclaiming these objects)
        let monotonic = state.snapshots.windows(2)
            .all(|w| w[1].1 >= w[0].1);
        if monotonic && state.snapshots.len() >= 10 {
            evidence.push(format!("Monotonic growth over {} samples",
                                  state.snapshots.len()));
            score += 2;
        }

        // Heuristic 2: Allocation rate accelerating
        if state.rate_history.len() >= 5 {
            let mid = state.rate_history.len() / 2;
            let first: f64 = state.rate_history[..mid].iter().sum::<f64>()
                / mid as f64;
            let second: f64 = state.rate_history[mid..].iter().sum::<f64>()
                / (state.rate_history.len() - mid) as f64;
            if second > first * 1.5 {
                evidence.push(format!("Rate accelerating: {:.0}/s → {:.0}/s",
                                      first, second));
                score += 2;
            }
        }

        // Heuristic 3: High absolute volume sustained
        if total > 100_000 && rate > 50.0 {
            evidence.push(format!("{} total at {:.0}/s", total, rate));
            score += 1;
        }

        if score == 0 { return; }

        let confidence = match score {
            1 => LeakConfidence::Low,
            2 => LeakConfidence::Medium,
            3 => LeakConfidence::High,
            _ => LeakConfidence::Critical,
        };

        self.suspects.insert(name.to_string(), LeakSuspect {
            class_name: name.to_string(),
            confidence, evidence,
            total_allocations: total,
        });
    }

    pub fn suspects(&self) -> Vec<LeakSuspect> {
        let mut v: Vec<_> = self.suspects.iter()
            .map(|e| e.value().clone()).collect();
        v.sort_by(|a, b| b.total_allocations.cmp(&a.total_allocations));
        v
    }
}
