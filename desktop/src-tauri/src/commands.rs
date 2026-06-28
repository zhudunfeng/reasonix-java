use serde::{Deserialize, Serialize};
use tauri::State;

#[derive(Debug, Serialize, Deserialize)]
struct ChatRequestPayload {
    message: String,
    session_id: Option<String>,
    model_id: Option<String>,
}

#[derive(Debug, Serialize, Deserialize)]
struct ChatResponsePayload {
    content: String,
    session_id: String,
    model_id: String,
}

#[derive(Debug, Serialize, Deserialize)]
struct StreamResponsePayload {
    #[serde(rename = "type")]
    event_type: String,
    content: String,
    session_id: String,
    model_id: String,
}

#[tauri::command]
async fn chat(payload: ChatRequestPayload, base_url: State<'_, String>) -> Result<ChatResponsePayload, String> {
    let client = reqwest::Client::new();
    let res = client
        .post(format!("{}/api/chat", base_url.inner()))
        .json(&serde_json::json!({
            "query": payload.message,
            "sessionId": payload.session_id,
            "modelId": payload.model_id
        }))
        .send()
        .await
        .map_err(|e| e.to_string())?;

    let data = res
        .json::<ChatResponsePayload>()
        .await
        .map_err(|e| e.to_string())?;

    Ok(data)
}

#[tauri::command]
async fn chat_stream(payload: ChatRequestPayload, base_url: State<'_, String>, app: tauri::AppHandle) -> Result<(), String> {
    let url = format!(
        "{}/api/chat/stream?question={}",
        base_url.inner(),
        urlencoding::encode(&payload.message)
    );

    let client = reqwest::Client::new();
    let mut res = client
        .get(&url)
        .send()
        .await
        .map_err(|e| e.to_string())?;

    if !res.status().is_success() {
        return Err(format!("stream failed: {}", res.status()));
    }

    let mut buffer = String::new();
    let mut stream = res.bytes_stream();
    use futures_util::StreamExt;

    while let Some(chunk) = stream.next().await {
        let chunk = chunk.map_err(|e| e.to_string())?;
        buffer.push_str(std::str::from_utf8(&chunk).map_err(|e| e.to_string())?);

        while let Some(pos) = buffer.find("\n\n") {
            let frame = buffer[..pos].to_string();
            buffer = buffer[pos + 2..].to_string();
            if frame.starts_with("data: ") {
                let payload = frame[6..].trim();
                if payload == "[DONE]" {
                    break;
                }
                let event = serde_json::from_str::<StreamResponsePayload>(payload)
                    .map_err(|e| format!("parse stream event failed: {}, payload={}", e, payload))?;
                app.emit("chat-stream", event)
                    .map_err(|e| e.to_string())?;
            }
        }
    }

    Ok(())
}
