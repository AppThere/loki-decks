//! What is DeckCraft's own: the session, how methods reach it, and the slide renderer.

use deckcraft_engine::render::{RenderOpts, render_slide};
use deckcraft_mcp::{Backend, Headless};
use serde_json::{Value, json};

use crate::Rendered;

pub(crate) struct Inner {
    h: Headless,
}

/// A session holding a new presentation.
pub(crate) fn new() -> Inner {
    Inner { h: Headless::with_document() }
}

/// Every control-protocol method the headless backend answers (`engine.execute`, `engine.commands`,
/// `document.inspect`, `ui.pointer`, `ui.key`, `ui.text`, `ui.tool.select`, …).
pub(crate) fn call(inner: &mut Inner, method: &str, params: Value) -> Result<Value, String> {
    inner.h.call(method, params)
}

/// The headless backend answers requests itself and drops what is left (`Headless::exec`), so
/// nothing reaches the front end yet. Wiring them is the playbook's phase 6.
pub(crate) fn ui_requests(_inner: &mut Inner) -> Value {
    json!([])
}

pub(crate) fn surface(inner: &mut Inner) -> Value {
    let Some(st) = inner.h.session.active() else { return json!({"kind": "slides", "pages": 0, "current": 0}) };
    json!({"kind": "slides", "pages": st.doc.slides.len(), "current": st.selection.slide})
}

/// Slide `page` as the editor shows it (placeholder prompts and outlines), at `scale` pixels per point.
pub(crate) fn render(inner: &mut Inner, page: u32, scale: f32, max_pixels: u64) -> Option<Rendered> {
    let st = inner.h.session.active()?;
    let i = usize::try_from(page).ok()?;
    if i >= st.doc.slides.len() {
        return None;
    }
    let (w, h) = (st.doc.slide_size.width, st.doc.slide_size.height);
    let px = (w * f64::from(scale)).ceil() * (h * f64::from(scale)).ceil();
    if !px.is_finite() || px > max_pixels as f64 {
        return None;
    }
    let img = render_slide(&st.doc, i, &RenderOpts { scale: f64::from(scale), edit: true, ..Default::default() });
    Some(Rendered { width: img.width, height: img.height, pages: u32::try_from(st.doc.slides.len()).unwrap_or(u32::MAX), rgba: img.to_straight() })
}
