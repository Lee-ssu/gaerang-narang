(() => {
  'use strict';

  const formatNumber = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 2, useGrouping: false });

  function node(tag, className, text) {
    const result = document.createElement(tag);
    result.className = className;
    if (text !== undefined) result.textContent = text;
    return result;
  }

  function button(label, description, handler, primary = false) {
    const result = node('button', 'cabin-button' + (primary ? ' cabin-button-primary' : ''), label);
    result.type = 'button';
    if (description) result.setAttribute('aria-label', description);
    result.addEventListener('click', handler);
    return result;
  }

  function percentage(value, min, max) {
    if (!(max > min)) return null;
    return Math.round(Math.min(100, Math.max(0, (value - min) * 100 / (max - min))));
  }

  function canOpenWindowAlert(row) {
    return row?.kind === 'WINDOW_POSITION' && row.canSet && !row.busy &&
      Number.isFinite(row.value) && percentage(row.value, row.min, row.max) > 10 &&
      row.value > Math.round(row.min + (row.max - row.min) / 10);
  }

  function valueLabel(row) {
    const { value, min, max, kind } = row;
    if (!Number.isFinite(value)) return '—';
    switch (kind) {
      case 'TEMPERATURE': return formatNumber.format(value) + '°C';
      case 'LEVEL': return value === 0 ? '꺼짐' : formatNumber.format(value) + '단';
      case 'WINDOW_POSITION': {
        if (value === min) return '닫힘';
        const percent = percentage(value, min, max);
        return percent == null ? '열림' : percent + '%';
      }
      case 'DOOR_POSITION': return value === min ? '닫힘' : '열림';
      case 'TOGGLE': {
        if (row.title.endsWith('잠금')) return value === max ? '잠김' : '해제';
        return value === max ? '켜짐' : '꺼짐';
      }
      default: return formatNumber.format(value);
    }
  }

  function rowMessage(row) {
    if (row.message) return row.message;
    if (row.busy) return '차량 응답을 확인 중이에요.';
    if (row.available === false) return '이 좌석에서 제공되지 않는 기능이에요.';
    if (!Number.isFinite(row.value)) return '현재 차량 상태를 읽고 있어요.';
    if (!row.canSet) return '상태 표시 전용';
    return '';
  }

  function actionState(row) {
    const { value, min, max, step, kind, title } = row;
    const rangeValid = Number.isFinite(min) && Number.isFinite(max) && max > min;
    const enabled = row.canSet && rangeValid && Number.isFinite(value) && !row.busy;
    const adjust = (key, label, target, description = '') => ({ key, label, target, description,
      enabled: enabled && Number.isFinite(target) && target !== value });

    if (kind === 'TOGGLE') {
      const on = value === max;
      const lock = title.endsWith('잠금');
      const label = !Number.isFinite(value) ? '확인 중'
        : lock ? (on ? '잠금 해제' : '잠그기') : (on ? '끄기' : '켜기');
      const description = lock && on ? title.replace(/잠금$/, '잠금 해제') : title + ' ' + label;
      return [adjust('toggle', label, on ? min : max, description)];
    }
    if (kind === 'DOOR_POSITION') {
      const open = Number.isFinite(value) && value > min;
      return [adjust('toggle', open ? '문 닫기' : '문 열기', open ? min : max)];
    }
    const lower = Math.max(min, value - step);
    const higher = Math.min(max, value + step);
    return [
      adjust('less', '−', lower, title + ' 낮추기'),
      adjust('more', '+', higher, title + ' 높이기')
    ];
  }

  function windowActions(actions, send) {
    let row, presets, choices, draft, saving;
    let selectedSlot = -1;
    let renderedState = '';
    const supported = items => (items || []).filter(item => item && Number.isFinite(item.percent) &&
      Number.isFinite(item.target) && item.percent >= 0 && item.percent <= 100);
    const presetButtons = node('div', 'cabin-window-presets');
    presetButtons.setAttribute('aria-label', '창문 열림 프리셋');
    presetButtons.setAttribute('role', 'group');
    const tools = node('div', 'cabin-window-tools');
    const apply = target => {
      if (row.canSet && !row.busy && Number.isFinite(row.value) && Number.isFinite(target) && target !== row.value)
        send('cabinAdjust', row.id + ':' + Number(target.toFixed(4)));
    };
    const closeWindow = button('완전히 닫기', '창문 완전히 닫기', () => apply(row.min), true);
    const edit = button('프리셋 설정', '', () => {
      if (selectedSlot >= 0) selectedSlot = -1;
      else selectSlot(0);
      renderEditor();
      if (selectedSlot >= 0) picker.querySelector('[aria-pressed="true"]')?.focus();
    });
    edit.setAttribute('aria-controls', 'cabin-preset-editor');
    const startAlert = button('창문 올림 안내', '', () => {
      if (canOpenWindowAlert(row)) send('windowAlert');
    });
    startAlert.classList.add('cabin-button-alert');
    tools.append(closeWindow, edit, startAlert);
    const editor = node('section', 'cabin-preset-editor');
    editor.id = 'cabin-preset-editor';
    const slots = node('div', 'cabin-preset-slots');
    slots.setAttribute('aria-label', '바꿀 프리셋 번호');
    slots.setAttribute('role', 'group');
    const picker = node('div', 'cabin-preset-choices');
    picker.setAttribute('aria-label', '설정할 열림 비율');
    picker.setAttribute('role', 'group');
    const save = button('저장', '창문 프리셋 저장', () => {
      if (save.disabled) return;
      saving = { slot: selectedSlot, percent: draft };
      save.disabled = true;
      send('windowPresetSave', selectedSlot + ':' + draft);
    }, true);
    editor.append(node('h3', 'cabin-preset-title', '열림 프리셋 설정'), slots, picker, save);
    actions.append(presetButtons, tools, editor);

    function selectSlot(index) {
      selectedSlot = index;
      draft = presets[index]?.percent;
      saving = null;
    }

    function renderEditor() {
      const open = selectedSlot >= 0 && presets.length > 0 && choices.length > 0;
      edit.setAttribute('aria-expanded', String(open));
      editor.hidden = !open;
      if (!open) return;
      slots.replaceChildren();
      for (const [index, preset] of presets.entries()) {
        const choice = button('프리셋 ' + (index + 1), '프리셋 ' + (index + 1) + ' · ' + preset.percent + '%', () => {
          selectSlot(index);
          renderEditor();
          (picker.querySelector('[aria-pressed="true"]') || picker.firstElementChild)?.focus();
        });
        choice.setAttribute('aria-pressed', String(index === selectedSlot));
        slots.append(choice);
      }
      const scrollTop = picker.scrollTop;
      picker.replaceChildren();
      for (const option of choices) {
        const choice = button(option.percent + '%', '프리셋 값 ' + option.percent + '%', () => {
          draft = option.percent;
          renderEditor();
          picker.children[choices.indexOf(option)]?.focus({ preventScroll: true });
        });
        choice.setAttribute('aria-pressed', String(option.percent === draft));
        picker.append(choice);
      }
      picker.scrollTop = scrollTop;
      save.disabled = saving != null || !choices.some(option => option.percent === draft) ||
        presets[selectedSlot]?.percent === draft;
    }

    return (current, cabin) => {
      row = current;
      presets = supported(cabin.windowPresets);
      choices = supported(cabin.windowChoices);
      const state = JSON.stringify([row.value, row.canSet, row.busy, row.min, row.max, presets, choices]);
      if (renderedState === state) return;
      renderedState = state;
      const canAdjust = row.canSet && !row.busy && Number.isFinite(row.value);
      presetButtons.replaceChildren();
      presetButtons.hidden = presets.length === 0;
      for (const preset of presets) {
        const choice = button(preset.percent + '%', '창문 ' + preset.percent + '% 열기', () => apply(preset.target));
        choice.disabled = !canAdjust || preset.target === row.value;
        choice.setAttribute('aria-pressed', String(preset.target === row.value));
        presetButtons.append(choice);
      }
      closeWindow.disabled = !canAdjust || row.value === row.min;
      edit.hidden = presets.length === 0 || choices.length === 0;
      startAlert.hidden = !canOpenWindowAlert(row);
      if (saving && presets[saving.slot]?.percent === saving.percent) {
        selectedSlot = -1;
        saving = null;
        edit.focus();
      }
      renderEditor();
    };
  }

  function buildRow(row, prominent, send, cabin) {
    const section = node('section', 'cabin-setting' + (prominent ? ' cabin-setting-prominent' : ''));
    const meta = node('div', 'cabin-setting-meta');
    const value = node('output', 'cabin-value');
    const feedback = node('p', 'cabin-feedback');
    feedback.setAttribute('role', 'status');
    const heading = node('h2', 'cabin-setting-title');
    const zone = node('p', 'cabin-zone');
    meta.append(heading, zone);
    const actions = node('div', 'cabin-actions');
    section.append(meta, value, actions, feedback);
    const controls = new Map();
    const updateWindow = row.kind === 'WINDOW_POSITION' ? windowActions(actions, send) : null;
    let current = row;

    function update(next, cabinState) {
      current = next;
      heading.textContent = next.title;
      zone.textContent = next.zone;
      zone.hidden = !next.zone;
      const followingValue = valueLabel(next);
      if (value.textContent !== followingValue) {
        if (value.textContent && value.closest('.host-motion-enabled') &&
            !matchMedia('(prefers-reduced-motion: reduce)').matches) {
          value.animate([{ opacity: .45, transform: 'translateY(11px)' },
            { opacity: 1, transform: 'none' }], { duration: 280, easing: 'ease-out' });
        }
        value.textContent = followingValue;
      }
      value.hidden = next.available === false;
      const message = rowMessage(next);
      if (feedback.textContent !== message) feedback.textContent = message;
      feedback.hidden = !message;
      feedback.classList.toggle('cabin-feedback-error', Boolean(next.message));
      const isReadOnly = Number.isFinite(next.value) && !next.canSet && !next.busy && !next.message;
      actions.hidden = next.available == null || next.available === false || isReadOnly;
      actions.classList.toggle('cabin-actions-window', next.kind === 'WINDOW_POSITION');
      if (updateWindow) {
        updateWindow(next, cabinState);
        return;
      }
      const states = actionState(next);
      for (const state of states) {
        let control = controls.get(state.key);
        if (!control) {
          const activate = () => {
            const change = actionState(current).find(item => item.key === state.key);
            if (change?.enabled) send('cabinAdjust', current.id + ':' + Number(change.target.toFixed(4)));
          };
          control = button(state.label, state.description, activate,
            state.key === 'toggle');
          control.classList.toggle('cabin-button-step', next.kind === 'TEMPERATURE' || next.kind === 'LEVEL');
          actions.append(control);
          controls.set(state.key, control);
        }
        if (control.textContent !== state.label) control.textContent = state.label;
        if (state.description) control.setAttribute('aria-label', state.description);
        control.disabled = !state.enabled;
      }
    }

    update(row, cabin);
    return { section, update };
  }

  function windowRaiseSurface(send) {
    const section = node('section', 'cabin-raise');
    section.hidden = true;
    const description = node('p', 'cabin-raise-description');
    const clock = node('div', 'cabin-raise-clock');
    const count = node('div', 'cabin-raise-count');
    count.setAttribute('role', 'timer');
    clock.append(count, node('span', 'cabin-raise-caption', '초 뒤에 자동으로 올려요'));
    const buttons = node('div', 'cabin-raise-buttons');
    const now = button('지금 올리기', '', () => send('windowRaiseNow'), true);
    now.classList.add('cabin-button-warning');
    const keep = button('그대로 두기', '', () => send('windowKeep'));
    buttons.append(now, keep);
    section.append(description, clock, buttons);

    function update(alert, row, fallbackTitle) {
      const counting = alert.phase === 'countdown';
      const current = Number.isFinite(row?.value) ? percentage(row.value, row.min, row.max) : null;
      const target = Number.isFinite(alert.percent) ? alert.percent : null;
      const title = {
        countdown: current == null ? '창문을 확인해 주세요' : '창문이 ' + current + '% 열렸어요',
        applying: '창문을 올리고 있어요',
        complete: '창문을 올렸어요',
        failed: '창문을 확인해 주세요'
      }[alert.phase] || fallbackTitle;
      const targetLabel = target == null ? '' : target + '%만 남기고 올릴게요.';
      const copy = {
        countdown: targetLabel,
        applying: '차량에서 변경한 값을 확인하고 있어요.',
        complete: current == null ? '차량이 변경을 확인했어요.' : '현재 열림 ' + current + '%',
        failed: row?.message || '차량에서 변경된 위치를 확인하지 못했어요.'
      }[alert.phase] || '';
      if (description.textContent !== copy) description.textContent = copy;
      description.hidden = !copy;
      clock.hidden = !counting;
      if (counting) {
        const seconds = Number.isFinite(alert.seconds) ? Math.max(0, Math.ceil(alert.seconds)) : 3;
        if (count.textContent !== String(seconds)) {
          count.textContent = seconds;
          count.setAttribute('aria-label', seconds + '초 뒤 창문 자동 올림');
        }
      }
      const failed = alert.phase === 'failed';
      now.hidden = !counting && !failed;
      now.textContent = failed ? '다시 올리기' : '지금 올리기';
      now.disabled = failed && !row?.canSet;
      keep.textContent = counting ? '그대로 두기' : '창문 조작으로 돌아가기';
      return title;
    }

    return { section, update };
  }

  function mount(board, send) {
    const pane = node('section', 'cabin-pane');
    pane.hidden = true;
    pane.setAttribute('role', 'dialog');
    pane.setAttribute('aria-modal', 'true');
    pane.setAttribute('aria-labelledby', 'cabin-title');
    const surface = node('div', 'cabin-surface');
    const header = node('header', 'cabin-header');
    const title = node('h1', 'cabin-title');
    title.id = 'cabin-title';
    title.tabIndex = -1;
    let alertPhase = null;
    let alertReady = false;
    const dismiss = () => send(alertPhase == null ? 'cabinClose' : 'windowKeep');
    const close = button('닫기', '차량 조작 닫기', dismiss);
    header.append(title, close);
    const content = node('div', 'cabin-content');
    const raise = windowRaiseSurface(send);
    surface.append(header, content, raise.section);
    pane.append(surface);
    board.append(pane);

    const rendered = new Map();
    const covered = new Set();
    let oldFocus = null;
    let previousGroup = null;
    let exitAnimation = null;
    let exitMovement = null;

    function layout() {
      pane.classList.toggle('cabin-wide', board.offsetWidth > 800);
    }

    function restoreCovered() {
      for (const element of covered) element.inert = false;
      covered.clear();
    }

    function coverBackground() {
      restoreCovered();
      for (const element of board.children) {
        if (element === pane || element.inert) continue;
        element.inert = true;
        covered.add(element);
      }
    }

    function finishClose() {
      pane.hidden = true;
      pane.inert = false;
      alertPhase = null;
      alertReady = false;
      previousGroup = null;
      restoreCovered();
      if (oldFocus?.isConnected) oldFocus.focus({ preventScroll: true });
      oldFocus = null;
    }

    function closePane() {
      if (pane.hidden || exitAnimation) return;
      const motion = board.closest('.host-motion-enabled') &&
        !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      if (!motion || typeof pane.animate !== 'function') {
        finishClose();
        return;
      }
      pane.inert = true;
      const animation = pane.animate([{ opacity: 1 }, { opacity: 0 }],
        { duration: 230, easing: 'ease-in', fill: 'forwards' });
      exitMovement = surface.animate([{ transform: 'none' },
        { transform: 'translateY(36px) scale(.95)' }],
        { duration: 230, easing: 'ease-in', fill: 'forwards' });
      exitAnimation = animation;
      animation.finished.then(() => {
        if (exitAnimation !== animation) return;
        exitAnimation = null;
        finishClose();
        exitMovement?.cancel();
        exitMovement = null;
        animation.cancel();
      }, () => {});
    }

    function focusHeading() {
      title.focus({ preventScroll: true });
    }

    pane.addEventListener('click', event => {
      if (event.target === pane) dismiss();
    });
    pane.addEventListener('keydown', event => {
      if (event.key === 'Escape') {
        event.stopPropagation();
        event.preventDefault();
        dismiss();
      } else if (event.key === 'Tab') {
        const focusable = Array.from(surface.querySelectorAll('button:not(:disabled)'))
          .filter(element => !element.closest('[hidden]'));
        const active = document.activeElement;
        const endpoint = event.shiftKey ? focusable[0] : focusable[focusable.length - 1];
        if (active === endpoint || !focusable.includes(active)) {
          event.preventDefault();
          const next = event.shiftKey ? focusable[focusable.length - 1] : focusable[0];
          (next || title).focus();
        }
      }
    });
    window.addEventListener('resize', layout);

    return function update(cabin) {
      if (!cabin) {
        closePane();
        return;
      }
      if (exitAnimation) {
        exitAnimation.cancel();
        exitAnimation = null;
        exitMovement?.cancel();
        exitMovement = null;
        pane.inert = false;
      }
      const rows = cabin.rows || [];
      const windowRow = rows.find(row => row.kind === 'WINDOW_POSITION');
      pane.classList.toggle('cabin-window-modal', Boolean(windowRow));
      pane.classList.toggle('cabin-climate-modal', rows[0]?.kind === 'TEMPERATURE');
      if (pane.hidden) {
        oldFocus = document.activeElement;
        coverBackground();
        pane.hidden = false;
        layout();
        window.requestAnimationFrame(focusHeading);
      }
      const group = JSON.stringify([cabin.title, rows.map(row => row.id)]);
      if (previousGroup !== group) {
        previousGroup = group;
        content.replaceChildren();
        rendered.clear();
        const primary = rows[0];
        if (primary) {
          const row = buildRow(primary, true, send, cabin);
          content.append(row.section);
          rendered.set(primary.id, row);
        }
        if (rows.length > 1) {
          const additional = node('div', 'cabin-additional');
          for (const row of rows.slice(1)) {
            const entry = buildRow(row, false, send, cabin);
            additional.append(entry.section);
            rendered.set(row.id, entry);
          }
          content.append(additional);
        }
        content.classList.toggle('cabin-multiple', rows.length > 1);
        content.scrollTop = 0;
      }
      for (const row of rows) rendered.get(row.id)?.update(row, cabin);

      const alert = windowRow ? cabin.windowRaise : null;
      const previousPhase = alertPhase;
      alertPhase = alert?.phase || null;
      pane.dataset.alertPhase = alertPhase || '';
      content.hidden = Boolean(alert);
      raise.section.hidden = !alert;
      close.hidden = Boolean(alert);
      pane.classList.toggle('cabin-alert-open', Boolean(alert));
      if (previousPhase !== alertPhase && (previousPhase == null || alertPhase == null)) {
        window.requestAnimationFrame(focusHeading);
      }
      const heading = alert ? raise.update(alert, windowRow, cabin.title) : cabin.title;
      if (title.textContent !== heading) title.textContent = heading;
      if (!alert) alertReady = false;
      if (alertPhase === 'countdown' && !alertReady) {
        alertReady = true;
        window.requestAnimationFrame(() => window.requestAnimationFrame(() => {
          if (alertReady && alertPhase === 'countdown' && !pane.hidden) send('windowAlertReady');
        }));
      }
    };
  }

  window.GaerangCabin = { mount };
})();
