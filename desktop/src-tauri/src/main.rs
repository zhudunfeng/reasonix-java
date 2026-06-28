use tauri::Manager;

mod commands;
mod tray;

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .setup(|app| {
            tray::init_tray(app)?;
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![commands::chat, commands::chat_stream])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
