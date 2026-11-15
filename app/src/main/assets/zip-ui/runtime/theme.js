(() => {
  'use strict';

  const paints = {
    '255,255,255': { surface: 'surface', text: 'white-ink' },
    '246,241,234': { surface: 'paper', text: 'ink' },
    '243,227,208': { surface: 'tint', text: 'tint-ink' },
    '233,223,210': { surface: 'line', text: 'muted' },
    '217,203,185': { surface: 'boundary', text: 'muted' },
    '42,32,26': { surface: 'filled', text: 'ink' },
    '111,98,87': { surface: 'muted', text: 'muted' },
    '138,90,60': { surface: 'accent-fill', text: 'accent' },
    '201,139,91': { surface: 'fur', text: 'accent' },
    '184,54,31': { surface: 'warning-fill', text: 'warning' },
    '251,227,220': { surface: 'warning-tint', text: 'warning' },
    '232,103,74': { surface: 'red-accent', text: 'warning' }
  };
  const properties = ['background-color', 'color', 'border-block-start-color', 'border-block-end-color',
    'border-top-color', 'border-right-color', 'border-bottom-color', 'border-left-color', 'outline-color'];
  const modes = [['SYSTEM', '시스템'], ['LIGHT', '라이트'], ['DARK', '다크']];

  function roleFor(value) {
    const rgb = /^rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*1)?\s*\)$/.exec(value);
    return rgb ? paints[rgb.slice(1).join(',')] : null;
  }

  function shade(element) {
    if (!(element instanceof HTMLElement) || element.closest('svg') || element.matches('button[aria-label$="테마"]')) return;
    const css = element.style;
    for (const property of properties) {
      const value = css.getPropertyValue(property);
      const paint = roleFor(value);
      if (!paint) continue;
      const role = property === 'color' ? paint.text : paint.surface;
      css.setProperty(property, 'var(--gaerang-' + role + ', ' + value + ')', css.getPropertyPriority(property));
    }
    const background = css.getPropertyValue('background-color');
    element.toggleAttribute('data-gaerang-filled', /^var\(--gaerang-(filled|accent-fill|fur|red-accent),/.test(background));
  }

  function shadeTree(element) {
    if (!(element instanceof HTMLElement)) return;
    if (element.hasAttribute('style')) shade(element);
    element.querySelectorAll('[style]').forEach(shade);
  }

  function addAppearancePicker(board, action) {
    const header = board.querySelector('main')?.firstElementChild || board.firstElementChild;
    if (!header || header.childElementCount < 2) return null;
    const chooser = document.createElement('div');
    chooser.className = 'gaerang-appearance';
    chooser.setAttribute('role', 'group');
    chooser.setAttribute('aria-label', '화면 모드');
    const label = document.createElement('span');
    label.className = 'gaerang-appearance-label';
    label.textContent = '화면 모드';
    label.setAttribute('aria-hidden', 'true');
    chooser.append(label);
    for (const [mode, text] of modes) {
      const button = document.createElement('button');
      button.type = 'button';
      button.dataset.appearance = mode;
      button.setAttribute('aria-pressed', 'false');
      button.textContent = text;
      button.addEventListener('click', () => action('appearance', mode));
      chooser.append(button);
    }
    // The existing slot is the profile header's static voice example.
    header.lastElementChild.replaceWith(chooser);
    return chooser;
  }

  function mount(board, action, screen) {
    const nightTemplate = /_Night\.html$/.test(location.pathname);
    const profile = screen.startsWith('Profile');
    if (profile) board.classList.add('gaerang-profile');
    const chooser = profile ? addAppearancePicker(board, action) : null;
    let active = null;
    let observer = null;
    return state => {
      const night = nightTemplate || state.darkMode === true;
      if (night !== active) {
        active = night;
        document.documentElement.classList.toggle('gaerang-night', night);
        board.classList.toggle('gaerang-night-board', night);
        if (night && !nightTemplate) {
          shadeTree(board);
          observer = new MutationObserver(records => {
            for (const record of records) {
              if (record.type === 'attributes') shade(record.target);
              else record.addedNodes.forEach(shadeTree);
            }
          });
          observer.observe(board, { childList: true, subtree: true, attributes: true, attributeFilter: ['style'] });
        } else {
          observer?.disconnect();
          observer = null;
        }
      }
      const selected = state.appearance || 'SYSTEM';
      chooser?.querySelectorAll('button').forEach(button => {
        const pressed = String(button.dataset.appearance === selected);
        if (button.getAttribute('aria-pressed') !== pressed) button.setAttribute('aria-pressed', pressed);
      });
    };
  }

  window.GaerangTheme = { mount };
})();
