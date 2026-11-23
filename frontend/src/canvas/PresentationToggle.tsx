import { useEffect, useState } from 'react';
import { useCanvasStore } from './canvasStore';

/** FR-040: larger text, badges and strokes for screen sharing, plus a Fullscreen API toggle. */
export function PresentationToggle() {
  const on = useCanvasStore((s) => s.presentationMode);
  const setOn = useCanvasStore((s) => s.setPresentationMode);
  const [fullscreen, setFullscreen] = useState(false);

  useEffect(() => {
    const onChange = () => setFullscreen(document.fullscreenElement !== null);
    document.addEventListener('fullscreenchange', onChange);
    return () => document.removeEventListener('fullscreenchange', onChange);
  }, []);

  const toggleFullscreen = () => {
    if (document.fullscreenElement) void document.exitFullscreen();
    else void document.documentElement.requestFullscreen?.();
  };

  return (
    <div className="button-group">
      <button type="button" aria-pressed={on} className={on ? 'active' : ''} onClick={() => setOn(!on)}>
        Presentation
      </button>
      <button type="button" aria-pressed={fullscreen} onClick={toggleFullscreen} title="Full screen">
        ⛶
      </button>
    </div>
  );
}
