//! DeckCraft's own: a new slide is a second page to render.

use super::*;

#[test]
fn a_new_slide_is_a_new_page() {
    let s = loki_session_new();
    let before = call(s, "loki.surface", None)["result"]["pages"].as_u64().unwrap();
    let r = call(s, "engine.execute", Some(r#"{"command":"slide.new","params":{}}"#));
    assert_eq!(r["ok"], true, "{r}");
    let info = call(s, "loki.surface", None)["result"].clone();
    assert_eq!(info["pages"].as_u64().unwrap(), before + 1, "{info}");
    let (mut w, mut h, mut n) = (0, 0, 0);
    // SAFETY: valid session and out-pointers.
    let px = unsafe { loki_render_page(s, (before) as u32, 1.0, &mut w, &mut h, &mut n) };
    assert!(!px.is_null() && w > 100 && h > 100 && u64::from(n) == before + 1);
    unsafe { loki_buffer_free(px, w as usize * h as usize * 4) };
    unsafe { loki_session_free(s) };
}
