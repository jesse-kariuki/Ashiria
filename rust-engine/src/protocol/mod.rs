/* src/proto/mod.rs
*
* Event structs that match the JSON the Java agent sends.
* serde automatically generates JSON deserialization code from these.
*/

use serde::{Deserialize, Serialize};



#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum AgentEvent{
    ObjectAllocation(AllocationEvent),
    HeapSample(HeapSampleEvent),
    GcNotification(GcEvent),
    ClassLoad(ClassLoadEvent),
    TrackingEscalation(EscalationEvent),

}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AllocationEvent{
    pub timestamp_ns: u64,
    pub class_name: String,
    pub allocating_class: String,
    pub allocating_method: String,
    pub thread_id: u64,
    pub thread_name: String,

    #[serde(default)]
    pub stack_frames: Vec<StackFrame>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StackFrame{
    pub class_name: String,
    pub method_name: String,
    pub line_number: i32,
}


#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HeapSampleEvent{
    pub timestamp_ns:        u64,
    pub heap_used_bytes:     u64,
    pub heap_max_bytes:      u64,
    pub heap_committed_bytes: u64,
    pub loaded_class_count:  u32,
    pub top_classes:         Vec<(String, u64)>,
}
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GcEvent {
    pub timestamp_ns:    u64,
    pub gc_name:         String,
    pub gc_action:       String,
    pub gc_cause:        String,
    pub duration_ms:     u64,
    pub heap_used_before: u64,
    pub heap_used_after:  u64,
}


impl GcEvent{

    pub fn reclaimed_bytes(&self) -> u64{
        self.heap_used_before.saturating_sub(self.heap_used_after);
    }

}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ClassLoadEvent{
    pub timestamp_ns:    u64,
    pub class_name: String,
    pub loader_name: Option<String>,

}

pub struct EscalationEvent {
    pub timestamp_ns: u64,
    pub class_name:   String,
    pub trigger_rate: f64,
}

pub const MAX_FRAME_BYTES: usize = 16 * 1024 * 1024; // 16 MB

