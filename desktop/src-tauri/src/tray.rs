use tauri::{AppHandle, Manager, SystemTray, SystemTrayMenu, SystemTrayMenuItem};

pub fn init_tray(app: &tauri::App) -> Result<(), Box<dyn std::error::Error>> {
    let menu = SystemTrayMenu::new()
        .add_item(CustomTrayMenuItem::NewSession.id(), "新建会话")
        .add_native_item(SystemTrayMenuItem::Separator)
        .add_item(CustomTrayMenuItem::Quit.id(), "退出");

    let tray = SystemTray::new().with_menu(menu);

    app.on_tray_icon_event(|event, app| {
        if event.event() == tauri::SystemTrayEvent::LeftClick {
            let window = app.get_webview_window("main");
            if let Some(window) = window {
                if window.is_visible().unwrap_or_default() {
                    let _ = window.hide();
                } else {
                    let _ = window.show();
                }
            }
        }
    });

    app.set_tray_icon(tray);
    Ok(())
}

#[derive(Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
enum CustomTrayMenuItem {
    NewSession,
    Quit,
}

impl CustomTrayMenuItem {
    fn id(&self) -> &'static str {
        match self {
            Self::NewSession => "new-session",
            Self::Quit => "quit",
        }
    }
}
