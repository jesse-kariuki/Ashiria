use crate::analysis::EngineState;
use axum::{extract::State, routing::get, Json, Router};
use std::sync::Arc;
use tower_http::cors::CorsLayer;


type AppState = Arc<EngineState>;

pub async fn serve(state: Arc<EngineState>, addr: &str) {
    let app = Router::new()
        .route("/api/status",            get(status_handler))
        .route("/api/allocations/top",   get(top_allocations_handler))
        .route("/api/allocations/rates", get(top_rates_handler))
        .route("/api/leaks",             get(leaks_handler))
        .layer(CorsLayer::permissive())  // Allow browser access from any origin
        .with_state(state);

    let listener = tokio::net::TcpListener::bind(addr).await.unwrap();
    tracing::info!("HTTP API on {}", addr);
    axum::serve(listener, app).await.unwrap();
}

// Handler: GET /api/status
// State<AppState> is like @Autowired EngineState in Spring
async fn status_handler(State(state): State<AppState>) -> Json<serde_json::Value> {
    Json(serde_json::json!({
        "uptime_secs": state.start_time.elapsed().as_secs(),
        "classes_tracked": state.tracker.per_class.len(),
    }))
}

// Handler: GET /api/allocations/top?n=20
async fn top_allocations_handler(
    State(state): State<AppState>,
) -> Json<serde_json::Value> {
    let top = state.tracker.top_by_total(20);
    Json(serde_json::json!({ "classes": top }))
}

async fn top_rates_handler(State(state): State<AppState>) -> Json<serde_json::Value> {
    // Similar — query top_by_rate from tracker
    Json(serde_json::json!({ "classes": [] }))  // Extend with full impl
}

async fn leaks_handler(State(state): State<AppState>) -> Json<serde_json::Value> {
    let suspects = state.leaks.suspects();
    Json(serde_json::json!({ "suspects": suspects }))
}
