(() => {
  'use strict';

  const name = location.pathname.split('/').pop().replace('.html', '');
  const portrait = name.startsWith('P_');
  const screen = name.replace(/^[BP]_/, '').replace(/_Night$/, '');
  const alertMode = { RestAlert10: 'rest10', RestAlert5: 'rest5', Highway: 'highway' }[screen];
  const board = document.body.firstElementChild;
  board.classList.add(portrait ? 'host-compact' : 'host-wide');
  const viewport = document.createElement('div');
  viewport.id = 'host-viewport';
  board.before(viewport);
  viewport.append(board);

  function fit() {
    const available = document.documentElement;
    const scale = Math.min(available.clientWidth / (portrait ? 640 : 1280), available.clientHeight / 906);
    const width = (portrait ? 640 : 1280) * scale;
    viewport.style.transform = 'translate(' + ((available.clientWidth - width) / 2) + 'px,' +
      ((available.clientHeight - 906 * scale) / 2) + 'px) scale(' + scale + ')';
  }
  addEventListener('resize', fit);
  fit();

  let drive;
  if (portrait && (screen === 'Drive' || alertMode) && typeof GaerangDrive !== 'undefined') {
    drive = GaerangDrive.controller({ night: name.endsWith('_Night'), safe: true,
      fr: screen === 'Drive' ? 3 : 0, alert: alertMode });
    drive.mount(board, 'screen');
    drive.jumpTo(alertMode === 'highway' ? 100 : alertMode ? 45 : 0);
  }

  const text = [];
  const walk = document.createTreeWalker(board, NodeFilter.SHOW_TEXT, {
    acceptNode(node) {
      return node.nodeValue.trim() && !node.parentElement.closest('svg,script,style')
        ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
    }
  });
  for (let node = walk.nextNode(); node; node = walk.nextNode()) {
    text.push({ node, original: node.nodeValue, key: node.nodeValue.trim() });
  }

  function replace(key, value) {
    for (const item of text) {
      if (typeof key === 'string' ? item.key === key : key.test(item.key)) item.node.nodeValue = value;
    }
  }

  function textElement(key) {
    return text.find(item => item.key === key)?.node.parentElement;
  }

  function element(label, scope = board) {
    return Array.from(scope.querySelectorAll('button,a')).find(node => node.textContent.trim() === label);
  }

  let interactive = false;
  let windowReadyDeferred = false;
  function action(name, value = '') {
    if (name === 'windowAlertReady' && !interactive) {
      windowReadyDeferred = true;
      return;
    }
    location.href = 'https://action.gaerang.invalid/command?action=' +
      encodeURIComponent(name) + '&value=' + encodeURIComponent(value);
  }

  window.setGaerangInteractive = enabled => {
    interactive = enabled;
    document.documentElement.classList.toggle('host-live', enabled);
    if (!enabled) window.pauseGaerangCareVideo?.();
    if (enabled && windowReadyDeferred) {
      windowReadyDeferred = false;
      action('windowAlertReady');
    }
  };

  window.prepareGaerangReveal = token => {
    document.fonts.ready.then(() => action('frontendReady', token));
  };

  const updateCabin = window.GaerangCabin?.mount(board, action);
  const updateTheme = window.GaerangTheme?.mount(board, action, screen);

  function wire(el, name, value = '') {
    if (!el) return;
    el.onclick = event => { event.preventDefault(); action(name, value); };
  }

  function command(label, name, value = '', primary = false) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'host-control' + (primary ? ' primary' : '');
    button.textContent = label;
    wire(button, name, value);
    return button;
  }

  function tray(parent, floating = false) {
    const row = document.createElement('div');
    row.className = 'host-actions' + (floating ? ' host-float-actions' : '');
    parent.append(row);
    return row;
  }

  function press(button, active) {
    if (!button) return;
    button.setAttribute('aria-pressed', String(active));
    button.style.color = active ? '#FFFFFF' : '#2A201A';
    button.style.background = active ? '#2A201A' : '#F6F1EA';
  }

  const nav = board.querySelector('nav[aria-label="주 메뉴"]');
  if (nav) {
    Array.from(nav.querySelectorAll('a')).slice(0, 4)
      .forEach((link, i) => {
        link.lastElementChild?.classList.add('host-clip-label');
        wire(link, ['home', 'places', 'care', 'profile'][i]);
      });
  }
  board.addEventListener('click', event => {
    if (event.target.closest('a')) event.preventDefault();
  });
  board.addEventListener('click', event => {
    if (!board.classList.contains('host-motion-enabled') ||
        matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const control = event.target.closest('button, a, [role=button]');
    if (!control || control.disabled || control.closest('[inert]')) return;
    const x = board.getBoundingClientRect();
    const y = control.getBoundingClientRect();
    const scale = x.width / board.offsetWidth;
    if (!scale || !y.width || !y.height) return;
    const pulse = document.createElement('span');
    pulse.className = 'host-press-feedback';
    pulse.style.left = (y.left - x.left) / scale + 'px';
    pulse.style.top = (y.top - x.top) / scale + 'px';
    pulse.style.width = y.width / scale + 'px';
    pulse.style.height = y.height / scale + 'px';
    pulse.style.borderRadius = getComputedStyle(control).borderRadius;
    pulse.setAttribute('aria-hidden', 'true');
    board.append(pulse);
    pulse.addEventListener('animationend', () => pulse.remove(), { once: true });
    setTimeout(() => pulse.remove(), 420);
  }, true);

  const primaryHome = element('좋아, 출발!');
  const secondaryHome = element('아니야, 끄기');
  const greeting = screen === 'Main' ? textElement('초코 탔네요!') : null;
  const greetingSize = greeting ? parseFloat(getComputedStyle(greeting).fontSize) : null;
  let measuredGreeting = '';
  function fitGreeting() {
    if (!greeting || !greetingSize) return;
    const available = greeting.clientWidth;
    const signature = available + ':' + greeting.textContent;
    if (!available || signature === measuredGreeting) return;
    measuredGreeting = signature;
    const measure = document.createRange();
    greeting.style.fontSize = greetingSize + 'px';
    measure.selectNodeContents(greeting);
    const width = measure.getBoundingClientRect().width;
    if (width > 0) {
      const scaledWidth = available * greeting.getBoundingClientRect().width / greeting.offsetWidth;
      greeting.style.fontSize = Math.max(24, Math.min(greetingSize,
        Math.floor(greetingSize * scaledWidth / width) - 1)) + 'px';
    }
  }
  if (greeting) {
    greeting.classList.add('host-home-greeting');
    new ResizeObserver(fitGreeting).observe(greeting);
    document.fonts.ready.then(() => { measuredGreeting = ''; fitGreeting(); });
  }
  const cabinRows = [
    ['실내 온도', '공조 조작', 'CLIMATE'], ['초코 자리 시트', '동승 좌석 통풍', 'SEAT'],
    ['공기청정', '동승 쪽 창문 조작', 'WINDOW'], ['뒷문 · 뒷창문', '동승 쪽 문 조작', 'DOOR']
  ];

  function mountCabinAuto() {
    const voice = textElement('“글레오, 펫 모드 꺼줘”')?.parentElement;
    if (!voice) return null;
    const controls = document.createElement('div');
    controls.className = 'host-home-cabin-auto';
    controls.hidden = true;
    const feedback = document.createElement('span');
    feedback.className = 'host-home-cabin-auto-status';
    feedback.setAttribute('role', 'status');
    const toggle = document.createElement('button');
    toggle.className = 'host-home-cabin-auto-toggle';
    toggle.type = 'button';
    toggle.setAttribute('role', 'switch');
    toggle.setAttribute('aria-checked', 'false');
    let enabled = false;
    toggle.onclick = () => action('cabinAutoToggle', enabled ? 'off' : 'on');
    const label = document.createElement('span');
    label.textContent = '공조 자동 조정';
    const track = document.createElement('span');
    track.className = 'host-home-cabin-auto-track';
    track.setAttribute('aria-hidden', 'true');
    toggle.append(label, track);
    controls.append(feedback, toggle);
    voice.classList.add('host-home-cabin-auto-voice');
    voice.parentElement.classList.add('host-home-cabin-auto-header');
    voice.before(controls);

    return function update(status) {
      controls.hidden = status == null;
      if (!status) return;
      enabled = status.enabled === true;
      toggle.setAttribute('aria-checked', String(enabled));
      const summary = status.summary?.trim() || '';
      if (feedback.textContent !== summary) feedback.textContent = summary;
      if (summary) feedback.title = summary;
      else feedback.removeAttribute('title');
      feedback.dataset.phase = status.phase || '';
    };
  }

  const updateCabinAuto = screen === 'Main' ? mountCabinAuto() : null;
  if (screen === 'Main') {
    board.classList.add('host-home');
    textElement('토이 푸들 초코에게 맞췄어요')?.classList.add('host-home-heading');
    wire(primaryHome, 'homePrimary');
    wire(secondaryHome, 'homeSecondary');
    for (const [key, label, group] of cabinRows) {
      const caption = textElement(key);
      const row = caption?.closest('li');
      if (!row) continue;
      caption.classList.add('host-clip-label');
      caption.nextElementSibling?.classList.add('host-clip-label');
      const affordance = document.createElement('span');
      affordance.textContent = '›';
      affordance.setAttribute('aria-hidden', 'true');
      affordance.className = 'host-cabin-chevron';
      row.append(affordance);
      row.classList.add('host-clickable');
      row.tabIndex = 0;
      row.setAttribute('role', 'button');
      row.setAttribute('aria-label', label);
      wire(row, 'cabin', group);
      row.onkeydown = event => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          action('cabin', group);
        }
      };
    }
    const placeLink = textElement('간절곶 가는 길에 쉬어갈 곳 3곳 보기');
    if (placeLink) {
      placeLink.classList.add('host-clip-label');
      const control = placeLink.closest('a,button') || placeLink;
      wire(control, 'places');
      if (control === placeLink && !control.matches('a,button')) {
        control.tabIndex = 0;
        control.setAttribute('role', 'button');
        control.classList.add('host-clickable');
        control.onkeydown = event => { if (event.key === 'Enter' || event.key === ' ') action('places'); };
      }
    }
    const artwork = board.querySelector('svg[aria-label^="좌석 배치도"]');
    if (artwork) artwork.setAttribute('aria-label', '반려견 동승 좌석 배치도');
  }

  function home(s) {
    replace('자동으로 알아봤어요', s.ride ? '보호자가 확인했어요' : '출발 전 동승 확인');
    replace('초코 탔네요!', s.ride ? s.pet + '와 함께 가요!' : s.pet + ', 갈까요?');
    fitGreeting();
    replace('뒷문이 열렸다 닫혔어요', s.boardingPrompt ? '등록한 쪽 문이 열렸다 닫혔어요' : '동승 쪽 문 · ' + s.door);
    replace('2열 오른쪽 문', s.seat);
    replace('2열 오른쪽', s.seat);
    replace('카시트 자리에 무게가 실렸어요', s.occupied == null ? '좌석 신호 응답 대기'
      : s.occupied ? '등록한 자리에 착석 신호가 있어요' : '등록한 자리에 착석 신호가 없어요');
    replace('등록해 둔 초코 자리', s.seat);
    replace('토이 푸들 초코에게 맞췄어요', s.hasDog ? s.breed + ' ' + s.pet + '에게 맞췄어요' : '반려견 프로필을 등록해요');
    replace('4살 · 3.8kg', s.ageWeight);
    replace('실내 온도', s.currentTemp != null ? '실내 온도' : '에어컨 설정 온도');
    replace('소형견 편안 구간 21~23°', s.tempCaption);
    replace('22°', s.temp);
    replace('초코 자리 시트', s.pet + ' 자리 시트');
    replace('카시트가 있는 자리만', s.seat);
    replace('통풍 1단', s.occupied == null ? '응답 대기' : s.occupied ? '착석 신호' : '착석 없음');
    replace('공기청정', '동승 쪽 창문');
    replace('털 날림 · 냄새 줄이기', s.seat);
    replace('켜짐', s.window);
    replace('뒷문 · 뒷창문', '동승 쪽 문');
    replace('창문은 10cm까지만 열려요', s.seat);
    replace('잠김', s.door);
    replace('뒷문 잠김', '동승 쪽 문 ' + s.door);
    replace('초코 카시트', s.pet + ' 자리');
    replace('간절곶 가는 길에 쉬어갈 곳 3곳 보기', s.destination ? s.destination + ' 가는 길 보기' : '함께 갈 곳 보기');
    replace('좋아, 출발!', s.homePrimary);
    replace('아니야, 끄기', s.resting ? '휴식 취소' : s.ride ? '동승 마치기' : s.boardingPrompt ? '아니야, 끄기' : '산책길 보기');
    replace('“글레오, 펫 모드 꺼줘”', '“글레오, 반려견과 갈 곳 찾아줘”');
    updateCabinAuto?.(s.hasDog ? s.cabinAuto : null);
  }

  const list = screen === 'Places' ? board.querySelector('ul') : null;
  const placeRow = list?.firstElementChild?.cloneNode(true);
  const placeHeading = list?.parentElement.querySelector('h2');
  placeHeading?.classList.add('host-clip-label');
  const placeTools = list ? tray(list.parentElement) : null;
  const originalFilters = screen === 'Places' ? Array.from(board.querySelectorAll('button[aria-pressed]')).slice(0, 4) : [];
  const placeKinds = ['ALL', 'CAFE', 'FIELD', 'SEA', 'CHARGE'];
  if (originalFilters.length === 4) {
    const lastFilter = originalFilters[3];
    const chargeFilter = lastFilter.cloneNode(false);
    chargeFilter.textContent = '충전·산책';
    lastFilter.after(chargeFilter);
    originalFilters.push(chargeFilter);
  }
  const placeMap = screen === 'Places' ? board.querySelector('svg[aria-label^="울산 약도"]') : null;
  const mapMarkerGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');
  const svgTag = (tag, opts) => {
    const node = document.createElementNS('http://www.w3.org/2000/svg', tag);
    for (const [key, value] of Object.entries(opts)) node.setAttribute(key, value);
    return node;
  };
  if (list) {
    list.classList.add('host-scroll');
    list.style.flex = '1';
    const spacer = list.nextElementSibling;
    if (spacer && !spacer.hasChildNodes()) spacer.style.flex = '0';
  }
  if (placeMap) {
    Array.from(placeMap.children).slice(0).forEach((node, i) => { if (i > 3) node.remove(); });
    placeMap.append(mapMarkerGroup);
    placeMap.setAttribute('aria-label', '울산 약도 · 추천 장소의 대략적인 위치');
  }
  placeKinds.forEach((value, i) => wire(originalFilters[i], 'filter', value));
  const journalText = text.find(item => item.key === '두 곳 더 가면 간식 세트 [협찬 브랜드]')?.node.parentElement;
  if (screen === 'Places' && journalText) {
    const journal = command('발자국 보기', 'journal');
    journalText.replaceWith(journal);
  }

  let placesSignature = '';
  function places(s) {
    replace('초코가 갈 수 있는 곳', '전체');
    replace('11월 발자국 모으기', '발자국 모으기');
    replace('3 / 5', s.visits + '곳');
    replace(/광고 제휴 매장은/, s.category === 'CHARGE'
      ? '충전기 이용 가능 여부는 방문 전에 확인해요.' : '등록한 방문 기록을 확인해요');
    const award = text.find(item => item.key === '3 / 5')?.node.parentElement.parentElement.nextElementSibling;
    if (award) Array.from(award.children).forEach((icon, i) => {
      icon.style.opacity = i < Math.min(s.visits, 5) ? '1' : '.25';
    });
    originalFilters.forEach((button, i) => press(button, placeKinds[i] === s.category));
    if (!list || !placeRow || !placeTools) return;
    const signature = JSON.stringify([s.places, s.pet, s.category, s.indoor, s.uncertain, s.style,
      s.course, s.share, s.navEnabled, s.canShare, s.routable]);
    if (signature === placesSignature) return;
    placesSignature = signature;
    const scrollTop = list.scrollTop;
    list.replaceChildren();
    mapMarkerGroup.replaceChildren();
    if (placeHeading) placeHeading.textContent = (s.category === 'CHARGE' ? '충전하며 산책할 곳 ' : s.pet + '와 함께 갈 곳 ') + s.places.length + '곳';
    replace(/토이 푸들 3\.8kg 기준/, s.category === 'CHARGE'
      ? '반려견 산책 공간이 가까운 충전소'
      : s.dogInfo ? s.dogInfo + ' 기준' : '반려견 등록 후 동반 조건을 확인해요');
    s.places.forEach((place, i) => {
      const row = placeRow.cloneNode(true);
      const [index, main] = row.children;
      const add = row.querySelector('button');
      index.textContent = i + 1;
      main.firstElementChild.textContent = place.name;
      main.lastElementChild.textContent = [place.area,
        place.walk ? [place.walk, place.connectors].filter(Boolean).join(' · ') : place.conditions,
        place.demo ? '시연 장소' : ''].filter(Boolean).join(' · ');
      main.tabIndex = 0;
      main.setAttribute('role', 'button');
      main.classList.add('host-clickable', 'host-place-copy');
      main.firstElementChild.classList.add('host-clip-label');
      main.lastElementChild.classList.add('host-clip-label');
      main.setAttribute('aria-label', place.name + ' 상세 · 후기');
      main.onkeydown = event => { if (event.key === 'Enter' || event.key === ' ') action('place', place.id); };
      wire(main, 'place', place.id);
      row.replaceChildren(index, main);
      if (Number.isInteger(place.demoFriends) && place.demoFriends >= 0) {
        const badge = document.createElement('span');
        badge.className = 'host-place-demo-friends';
        badge.textContent = place.demoFriends > 0
          ? '시연 · ' + place.demoFriends + '마리 동행 중' : '시연 · 동행 0마리';
        row.append(badge);
      }
      row.append(add);
      row.style.borderTop = i ? '1px solid #E9DFD2' : 'none';
      add.textContent = place.selected ? '−' : '+';
      add.setAttribute('aria-label', place.name + (place.selected ? ' 코스에서 빼기'
        : place.chargeWaypoint ? ' 충전 경유지로 추가' : ' 코스에 추가'));
      add.disabled = !place.canAdd && !place.selected;
      wire(add, 'addPlace', place.id);
      list.append(row);
      if (placeMap && Number.isFinite(place.latitude) && Number.isFinite(place.longitude)) {
        const x = Math.min(292, Math.max(32, 30 + (place.longitude - 129) * 480));
        const y = Math.min(382, Math.max(30, 30 + (35.66 - place.latitude) * 1090));
        const g = svgTag('g', { transform: 'translate(' + x + ' ' + y + ')' });
        const circle = svgTag('circle', { r: 15, fill: place.selected ? '#C98B5B' : '#2A201A', stroke: '#FFFFFF', 'stroke-width': 2 });
        const label = svgTag('text', { y: 5, fill: '#FFFFFF', 'text-anchor': 'middle', 'font-size': 15, 'font-weight': 700 });
        label.textContent = i + 1;
        g.append(circle, label);
        mapMarkerGroup.append(g);
      }
    });
    if (!s.places.length) {
      const empty = document.createElement('li');
      empty.textContent = s.hasDog ? '지금 조건에 맞는 장소가 없어요.' : '반려견을 등록하면 추천 장소를 볼 수 있어요.';
      list.append(empty);
    }
    list.scrollTop = scrollTop;
    placeTools.replaceChildren();
    if (!s.hasDog) placeTools.append(command('반려견 등록', 'profile', '', true));
    if (s.course.length) {
      const selection = document.createElement('span');
      selection.textContent = s.course.join(' → ');
      selection.className = 'host-route-selection host-clip-label';
      const route = command(s.routable ? '내비로 안내' : '코스 미리보기', 'route', '', true);
      route.disabled = s.routable && !s.navEnabled;
      placeTools.append(selection, route, command('내비 동행 미리보기', 'navPreview'), command('코스 비우기', 'clearCourse'));
    }
    placeTools.append(command('추천 조건', 'placeOptions'));
  }

  const profile = screen.startsWith('Profile');
  const seatButtons = [
    [element('운전석'), null], [element('조수석'), 'FRONT_RIGHT'], [element('2열 왼쪽'), 'REAR_LEFT'],
    [element('가운데'), null], [element('오른쪽'), 'REAR_RIGHT']
  ];
  const boardingButtons = [['카시트', 'CAR_SEAT'], ['좌석에', 'SEAT'], ['안고', 'HELD']]
    .map(([label, value]) => [element(label), value]);
  const themeButtons = [['카라멜 테마', 'BROWN'], ['초코 테마', 'GRAY'], ['크림 테마', 'CREAM'], ['목줄 빨강 테마', null]]
    .map(([label, value]) => [board.querySelector('button[aria-label="' + label + '"]'), value]);
  const shareSwitch = board.querySelector('button[aria-label="마릿수에 참여"]');
  const editor = element('폰에서 새 사진 보내기');
  if (profile) {
    wire(editor, 'editDog');
    wire(element('초코 기준'), 'profileSeats', 'no');
    wire(element('타는 자리 · 친구'), 'profileSeats', 'yes');
    for (const [key, buttons] of [['seat', seatButtons], ['boarding', boardingButtons], ['coat', themeButtons]]) {
      buttons.forEach(([button, value]) => {
        if (!button) return;
        if (value) wire(button, key, value);
        else button.disabled = true;
      });
    }
    wire(shareSwitch, 'share');
    board.querySelector('button[role="switch"][aria-label="초코 이름 보여주기"]')?.closest('button')?.setAttribute('disabled', '');
    const profileTools = editor?.parentElement;
    if (profileTools) profileTools.append(command('반려견 관리', 'manageDogs'));
  }

  function dogProfile(s) {
    replace('토이 푸들 · 4살 · 3.8kg', s.dogInfo);
    replace('폰에서 새 사진 보내기', s.hasDog ? '프로필 편집 · 사진 선택' : '반려견 등록');
    replace('견종 · 나이 · 체중으로 계산했어요', '등록한 프로필과 차량 신호를 표시해요');
    replace('소형견 표준 구간', s.tempCaption);
    replace('21~23°', s.temp);
    replace('60분', '90분 · 120분');
    replace('성견 표준, 노령견은 더 자주', '90분 사전 안내 · 120분 휴식 권장');
    replace('뒷창문', '동승 쪽 창문');
    replace('소형견이라 좁게', s.seat);
    replace('10cm', s.window);
    replace('혼자 두는 시간', '타는 방식');
    replace('넘기면 돌아오라고 알려요', '등록한 반려견 프로필 기준');
    replace('20분', { CAR_SEAT: '카시트', SEAT: '좌석', HELD: '보호자 품' }[s.boarding] || '—');
    replace('15kg 이하', '장소별 조건');
    replace('기준값은 수의사 자문으로 확정할 예정이에요', '실내·실외와 체급 조건은 장소별로 확인해요.');
    seatButtons.forEach(([button, value]) => press(button, value === s.seatKey));
    boardingButtons.forEach(([button, value]) => press(button, value === s.boarding));
    themeButtons.forEach(([button, value]) => button?.setAttribute('aria-pressed', String(value === s.coat)));
    if (shareSwitch) {
      shareSwitch.setAttribute('aria-checked', String(s.share));
      shareSwitch.disabled = !s.canShare;
      shareSwitch.style.opacity = s.share ? '1' : '.45';
    }
    replace('이름 없이 “3마리”로 세요', '동의한 사용자만 목적지별로 집계해요');
    replace('“초코가 가고 있어요”', '현재 집계는 익명으로 표시돼요');
  }

  const arrivalChecks = screen === 'Arrive' ? Array.from(board.querySelectorAll('button[role=checkbox]')) : [];
  if (screen === 'Arrive') {
    arrivalChecks.forEach((button, i) => wire(button, 'check', ['leash', 'bags', 'water'][i]));
    wire(element('뒷문 열기'), 'recordVisit');
    wire(element('나중에'), 'home');
  }
  function arrive(s) {
    replace('카페이소 도착 · 주차했어요', s.arrived && s.destination ? s.destination + ' 도착 · 주차했어요' : '주차 중 · 하차 준비');
    replace('“뒷문 열어줘”', '하차할 자리 · ' + s.seat);
    replace('도착했어요', s.arrived ? '도착했어요' : '하차 준비');
    replace('48분 달렸어요. 물부터 주세요.', s.elapsed + ' 이동했어요. 물과 목줄을 챙겨요.');
    replace('뒷문 열기', '발자국 남기기');
    replace('네 번째 발자국!', '방문 기록 ' + s.visits + '곳');
    replace('한 곳 더 가면 간식 세트', '함께 다녀온 곳을 기록해요');
    replace('한 곳 더 가면 간식', '함께 다녀온 곳을 기록해요');
    replace('도착한 걸 차가 확인했어요', s.arrived ? '내비에서 도착을 받았어요' : '하차 체크를 마치면 기록할 수 있어요');
    replace('4마리 있어요', '같은 목적지 동행');
    replace(/.*있는 강아지/, '도착 장소의 실시간 체류 인원은 제공되지 않아요');
    replace('소형견 3, 대형견 1', '');
    arrivalChecks.forEach((button, i) => {
      const checked = !!s[['leash', 'bags', 'water'][i]];
      button.setAttribute('aria-checked', String(checked));
      const box = button.firstElementChild;
      if (box) { box.style.opacity = checked ? '1' : '.4'; }
    });
  }

  const careBar = screen === 'Calm' ? board.querySelector('footer') : null;
  let carePlayButton;
  let carePlayPath;
  let originalCarePlayPath;
  let careMedia;
  if (careBar) {
    board.classList.add('host-care');
    careBar.classList.add('host-care-actions');
    careBar.replaceChildren();
    textElement('0:12')?.parentElement.remove();
    textElement('“초코야, 금방 올게”')?.classList.add('host-care-play-label');
    const audioPanel = textElement('[보호자가 녹화한 영상]')?.parentElement.closest('section');
    if (audioPanel) {
      audioPanel.classList.add('host-care-audio');
      const icon = audioPanel.querySelector('span > svg')?.parentElement;
      if (icon) {
        carePlayButton = document.createElement('button');
        carePlayButton.type = 'button';
        carePlayButton.className = 'host-care-play';
        carePlayButton.style.cssText = icon.style.cssText;
        carePlayButton.append(...icon.childNodes);
        icon.replaceWith(carePlayButton);
        carePlayPath = carePlayButton.querySelector('path');
        originalCarePlayPath = carePlayPath?.getAttribute('d');
        careMedia = mountCareVideo(carePlayButton.parentElement);
      }
    }
    const climate = textElement('실내 22°');
    const window = textElement('에어컨 켜짐');
    climate?.parentElement.classList.add('host-care-pills');
    for (const [pill, label, group] of [
      [climate, '공조 조작', 'CLIMATE'], [window, '동승 쪽 창문 조작', 'WINDOW']
    ]) {
      if (!pill) continue;
      const button = command('', 'cabin', group);
      button.className = 'host-care-pill';
      button.setAttribute('aria-label', label);
      button.append(...pill.childNodes);
      pill.replaceWith(button);
    }
    for (const key of ['권장 20분 안', '배터리 64%', '펫 모드로 유지']) {
      const caption = textElement(key);
      caption?.classList.add('host-care-caption');
      caption?.previousElementSibling.classList.add('host-care-value');
      caption?.previousElementSibling.previousElementSibling.classList.add('host-clip-label');
      caption?.parentElement.classList.add('host-care-metric');
      caption?.parentElement.parentElement.classList.add('host-care-grid');
    }
    careBar.classList.add('host-actions');
  }

  function mountCareVideo(frame) {
    const video = document.createElement('video');
    video.className = 'host-care-video';
    video.hidden = true;
    video.playsInline = true;
    video.preload = 'metadata';
    video.disablePictureInPicture = true;
    video.poster = '/zip-ui/media/guardian_voice.jpg';
    const feedback = document.createElement('div');
    feedback.className = 'host-care-video-feedback';
    feedback.setAttribute('role', 'status');
    feedback.hidden = true;
    frame.prepend(video);
    frame.append(feedback);

    function updateButton() {
      if (video.hidden) return;
      const playing = !video.paused && !video.ended;
      frame.classList.toggle('host-care-video-playing', playing);
      carePlayButton.setAttribute('aria-label', playing ? '보호자 영상 멈추기' : '보호자 영상 재생');
      if (carePlayPath && originalCarePlayPath) {
        carePlayPath.setAttribute('d', playing ? 'M9 6v12m6-12v12' : originalCarePlayPath);
      }
    }

    function error() {
      if (video.hidden) return;
      feedback.textContent = '영상을 재생할 수 없어요.';
      feedback.hidden = false;
      updateButton();
    }

    let loading = false;
    function prepare() {
      if (video.src || loading) return;
      loading = true;
      carePlayButton.disabled = true;
      // A local blob lets WebView decode the bundled clip without issuing media range requests.
      fetch('/zip-ui/media/guardian_voice.mp4').then(response => {
        if (!response.ok) throw new Error('Unable to load guardian video');
        return response.blob();
      }).then(blob => { video.src = URL.createObjectURL(blob); }).catch(error).finally(() => {
        loading = false;
        if (!video.hidden) carePlayButton.disabled = false;
      });
    }

    for (const event of ['play', 'pause', 'ended']) video.addEventListener(event, updateButton);
    video.addEventListener('error', error);
    window.pauseGaerangCareVideo = () => video.pause();
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) video.pause();
    });

    return enabled => {
      if (!enabled) video.pause();
      video.hidden = !enabled;
      feedback.hidden = true;
      frame.classList.toggle('host-care-video-frame', enabled);
      if (!enabled) {
        carePlayButton.disabled = false;
        frame.classList.remove('host-care-video-playing');
        return;
      }
      prepare();
      updateButton();
      carePlayButton.onclick = () => {
        if (!interactive) return;
        if (!video.paused && !video.ended) { video.pause(); return; }
        feedback.hidden = true;
        if (!video.src) { prepare(); return; }
        if (video.error) video.load();
        if (video.ended) video.currentTime = 0;
        video.play().catch(failure => { if (failure.name !== 'AbortError') error(); });
      };
    };
  }

  let careSignature = '';
  function care(s) {
    replace('실내 22°', (s.currentTemp != null ? '실내 ' : '설정 온도 ') + s.temp);
    replace('에어컨 켜짐', '창문 ' + s.window);
    replace('초코에게 틀어 주는 중', s.playing ? s.pet + '에게 들려주는 중' : '보호자 목소리');
    replace('3분마다 반복', '주차 중 재생');
    replace('[보호자가 녹화한 영상]', s.audio ? '저장한 음성' : '음성 파일을 선택해요');
    replace('“초코야, 금방 올게”', s.playing ? '재생 멈추기' : s.audio ? '목소리 들려주기' : '보호자 목소리 추가');
    replace('보호자가 내린 지', '동승 쪽 문');
    replace('8분', s.door);
    replace('권장 20분 안', s.seat);
    replace('공조 유지', '에어컨 설정');
    replace('약 2시간', s.temp);
    replace('배터리 64%', s.tempCaption);
    replace('초코 기준', '동승 좌석');
    replace('21~23°', s.seat);
    replace('펫 모드로 유지', '');
    if (careBar) {
      const signature = JSON.stringify([s.audio, s.playing, s.drivePreviewEnabled]);
      if (signature === careSignature) return;
      careSignature = signature;
      careBar.replaceChildren(command('하차 준비', 'arrival'));
      if (s.audio) careBar.append(command('음성 바꾸기', 'careAudio'));
      else if (careMedia) careBar.append(command('음성 파일 추가', 'careAudio'));
      if (s.drivePreviewEnabled) careBar.append(command('D단 화면 미리보기', 'previewDrive'));
      careMedia?.(!s.audio);
      if (carePlayButton && (s.audio || !careMedia)) {
        carePlayButton.setAttribute('aria-label', s.playing ? '재생 멈추기' : s.audio ? '목소리 들려주기' : '보호자 목소리 추가');
        wire(carePlayButton, s.audio ? 'carePlay' : 'careAudio');
        if (carePlayPath && originalCarePlayPath) {
          carePlayPath.setAttribute('d', s.playing ? 'M9 6v12m6-12v12' : originalCarePlayPath);
        }
      }
    }
  }

  const driveTools = ['Drive', 'Rest'].includes(screen) ? tray(board, true) : null;
  const driveTemperature = screen === 'Drive' && !portrait ? textElement('22°') : null;
  const driveTemperatureArt = driveTemperature?.nextElementSibling;
  const driveTemperatureSize = driveTemperature ? parseFloat(getComputedStyle(driveTemperature).fontSize) : 0;
  let measuredDriveTemperature = '';
  function fitDriveTemperature() {
    if (!driveTemperature || !driveTemperatureArt || !driveTemperatureSize) return;
    const available = driveTemperatureArt.getBoundingClientRect().left - driveTemperature.getBoundingClientRect().left;
    const key = available + ':' + driveTemperature.textContent;
    if (available <= 0 || key === measuredDriveTemperature) return;
    measuredDriveTemperature = key;
    driveTemperature.style.whiteSpace = 'nowrap';
    driveTemperature.style.fontSize = driveTemperatureSize + 'px';
    const range = document.createRange();
    range.selectNodeContents(driveTemperature);
    const width = range.getBoundingClientRect().width;
    if (width > 0) driveTemperature.style.fontSize =
      Math.min(driveTemperatureSize, Math.floor(driveTemperatureSize * available * .94 / width)) + 'px';
  }
  if (driveTemperature) {
    addEventListener('resize', fitDriveTemperature);
    document.fonts.ready.then(() => { measuredDriveTemperature = ''; fitDriveTemperature(); });
  }
  const driveFriendsCopy = driveTools ? [
    textElement('두부 포함 3마리가 가는 중'),
    textElement('두부와 콩이가 먼저 가고 있어요')
  ].filter(Boolean) : [];
  const driveStatus = board.querySelector('#screen-status');
  const driveStrip = board.querySelector('#screen-strip');
  const driveGait = board.querySelector('#screen-gline');
  const driveFriendsTile = board.querySelector('#screen-friends');
  const driveFriendsFaces = driveFriendsTile?.firstElementChild;
  const driveFriendsTitle = driveFriendsTile?.lastElementChild?.firstElementChild;
  const driveFriendsCaption = driveFriendsTile?.lastElementChild?.lastElementChild;
  const driveDestination = driveTools ? textElement('간절곶까지') : null;
  let activeMotion = null;
  let drivingToolsSignature = '';

  function updateDriveMotion(moving, reduceMotion) {
    const signature = moving + ':' + Boolean(reduceMotion);
    if (!drive || activeMotion === signature) return;
    activeMotion = signature;
    drive.setSafe(!moving || reduceMotion);
    drive.jumpTo(moving ? 45 : 0);
    if (moving && !reduceMotion) drive.start();
    else drive.stop();
  }

  function parkedDriveDemo(s) {
    updateDriveMotion(true, s.reduceMotion);
    driveTools.style.display = 'none';
    driveStatus.textContent = 'P단 · D단 시연';
    driveStrip.setAttribute('aria-label', '시연에서 달리는 ' + s.pet);
    driveGait.textContent = driveGait.textContent.replace('초코', s.pet);
    if (driveDestination) {
      const destination = s.destination ? s.destination + '까지' : '목적지 선택';
      driveDestination.classList.add('host-clip-label');
      driveDestination.style.maxWidth = '320px';
      driveDestination.title = destination;
      driveDestination.textContent = destination;
    }
    replace('D', 'P');
    replace('32분', s.guideTime || '—');
    replace('오후 2:42 도착', s.guideArrival ? s.guideArrival + ' 도착 예정' : '내비 도착 정보 대기');
    replace('22° 딱 좋아요', s.temp);
    replace('닫힘', s.window);
    replace('잠김', s.door);
    const friends = s.friendsDemo && Number.isInteger(s.friends) ? s.friends : 0;
    if (driveFriendsFaces) {
      const [first, second, rest] = driveFriendsFaces.children;
      if (first) first.style.display = friends > 0 ? 'inline-flex' : 'none';
      if (second) second.style.display = friends > 1 ? 'inline-flex' : 'none';
      if (rest) {
        rest.style.display = friends > 2 ? 'inline-flex' : 'none';
        if (friends > 2) rest.textContent = '+' + (friends - 2);
      }
    }
    if (driveFriendsTitle) driveFriendsTitle.textContent = friends
      ? friends + '마리가 같은 곳으로 가는 중' : '이 목적지의 시연 동행 수가 없어요';
    if (driveFriendsCaption) driveFriendsCaption.textContent = friends ? '시연용 동행 수' : '같은 목적지로 가는 반려견';
  }

  function driving(s) {
    if (s.parkedDriveDemo && drive && screen === 'Drive') {
      parkedDriveDemo(s);
      return;
    }
    const tripName = s.destination || '오늘의 산책길';
    const status = s.side ? 'P단 · 내비 동행' : s.preview ? 'P단 · D단 미리보기' : 'P단 · 잠시 멈췄어요';
    const validFriends = Number.isInteger(s.friends) && s.friends >= 0;
    const friends = validFriends && (!s.friendsDemo || s.destination) ? s.friends : null;
    const demoFriends = s.friendsDemo && friends !== null ? (friends > 0
      ? '시연 · ' + friends + '마리 동행 중' : '시연 · 동행 0마리') : null;
    driveFriendsCopy.forEach(label => label.classList.toggle('host-drive-demo-friends', demoFriends !== null));
    updateDriveMotion(Boolean(s.preview || s.side), s.reduceMotion);
    if (drive) {
      const statusLabel = board.querySelector('#screen-status');
      if (statusLabel) statusLabel.textContent = status;
      const tile = board.querySelector('#screen-friends');
      if (tile) {
        tile.textContent = demoFriends ?? (friends == null ? '같은 목적지 동행 정보를 기다리는 중'
          : friends ? '다른 반려견 ' + friends + '마리도 같은 곳으로 가는 중' : '같은 곳으로 가는 다른 반려견이 아직 없어요');
        tile.style.fontWeight = '700';
      }
    }
    replace(/^주행 중[ ·,]/, status);
    replace('D', 'P');
    replace('간절곶까지', tripName);
    replace('32분', s.guideTime);
    replace('오후 2:42 도착', s.guideArrival ? s.guideArrival + ' 도착 예정' : '내비 도착 정보 대기');
    replace('22°', s.temp);
    fitDriveTemperature();
    replace('22° 딱 좋아요', s.temp);
    replace('딱 좋아요', s.tempCaption);
    replace('“초코 자리 시원하게”', '온도는 주차 중에 조절해요');
    replace('18°', '');
    replace('26°', '');
    replace('초코 편안 구간 21~23°', s.tempCaption);
    replace('닫힘', s.window);
    replace('잠김', s.door);
    replace('공기청정', '동승 자리');
    replace('켜짐', s.seat);
    replace('남구 집에서 출발', '동승 시작');
    replace('오후 1:10', '');
    replace(/^(지금 · |지금 )?48분째/, '동승 후 ' + s.elapsed);
    replace('초코는 편안해요', s.seat);
    replace('카페이소에서 쉬어가기', s.restStop ? s.restStop + '에서 쉬어가기' : '쉬어갈 곳 찾기');
    replace(/^(12분 뒤|12분 앞|경로에서 [+-]?3분)/, '');
    replace('두부 포함 3마리가 가는 중', demoFriends ?? (friends ? friends + '마리가 같은 곳으로 가는 중' : ''));
    replace('간절곶 도착', s.destination || '목적지 선택');
    replace('오후 2:42', s.guideArrival);
    replace('쉬고 나서 10분', s.guideTime);
    replace('쉬어가기까지 (60분마다)', '다음 휴식 안내까지');
    replace('12분', s.restRemaining);
    replace('카페이소에서', s.restStop ? s.restStop + '에서' : '가까운 곳에서');
    replace('토이 푸들은 60분마다 쉬는 게 좋아요', '동승 시간 기준으로 쉬어갈 때를 안내해요.');
    replace('정원이 넓은 애견 동반 카페예요', '반려견 동반 조건을 확인해요.');
    replace('3.8kg 소형견도 들어갈 수 있어요', s.dogInfo);
    replace('초코 조건에 맞아요', s.pet + '와 갈 수 있는 장소를 찾을 수 있어요.');
    replace('두부와 콩이가 먼저 가고 있어요', demoFriends ?? (friends
      ? friends + '마리가 같은 곳으로 가고 있어요' : '동행 인원 응답 대기'));
    replace('이름은 보호자가 허용한 강아지만 보여요', demoFriends ? '' : '공유에 동의한 반려견만 집계해요.');
    const signature = JSON.stringify([s.preview, s.side, s.parkedCare, screen]);
    if (driveTools && signature !== drivingToolsSignature) {
      drivingToolsSignature = signature;
      driveTools.replaceChildren();
      if (s.preview) driveTools.append(command('미리보기 닫기', 'endPreview'));
      if (s.side) driveTools.append(command('산책길 보기', 'closeSide'));
      if (s.parkedCare) driveTools.append(command('기본 내비로 돌아가기', 'returnNav', '', true),
        command(s.canBreak ? '휴식 시작' : '우리 집', s.canBreak ? 'homePrimary' : 'home'));
      if (screen === 'Rest') driveTools.append(command('쉼터 보기', 'restOptions'));
    }
  }
  if (screen === 'Rest') {
    wire(element('경유지로 추가'), 'restOptions');
    wire(element('다음에'), 'endPreview');
  }
  const raiseWindow = screen === 'Alert' ? element('지금 올리기') : null;
  const keepWindow = screen === 'Alert' ? element('그대로 두기') : null;
  const windowTimer = screen === 'Alert' ? board.querySelector('[role=timer]') : null;
  let notifiedWindowReady = false;
  wire(raiseWindow, 'windowRaiseNow');
  wire(keepWindow, 'windowKeep');
  function windowAlert(s) {
    const alert = s.windowRaise;
    if (!alert) {
      notifiedWindowReady = false;
      if (raiseWindow) raiseWindow.disabled = true;
      return;
    }
    if (!notifiedWindowReady) {
      notifiedWindowReady = true;
      action('windowAlertReady');
    }
    const phase = alert.phase;
    const current = alert.preview ? (phase === 'complete' ? '10cm 열림' : '24cm 열림') : s.window;
    replace('D', 'P');
    replace(/^주행 중[ ·,]/, alert.preview ? 'P단 · D단 미리보기' : 'P단 · 창문 올림 안내');
    replace('24cm 열림', current);
    if (!alert.preview || phase === 'complete') {
      replace('24cm 열렸어요', current);
      replace('뒷창문이 24cm 열렸어요', '창문 · ' + current);
    }
    if (!alert.preview) {
      const target = alert.percent === 0 ? '닫힘' : alert.percent + '% 열림';
      replace('뒷창문', '동승 쪽 창문');
      replace('뒷창문이', '동승 쪽 창문');
      replace('10cm만 남기고 올릴게요.', alert.percent === 0 ? '창문을 닫을게요.' : alert.percent + '%만 남기고 올릴게요.');
      replace('초코 기준: 뒷창문 10cm까지', s.seat + ' · ' + target);
      replace('말로 해 보세요', '창문이 움직일 수 있어요.');
      replace('“지금 올려줘”', '');
      replace('“그대로 둬”', '');
    }
    const copy = {
      countdown: '초 뒤에 자동으로 올려요',
      applying: '차량에서 창문 위치를 확인하고 있어요',
      complete: alert.preview ? '미리보기에서 창문을 올렸어요' : '설정한 위치로 창문을 올렸어요',
      failed: '차량에서 변경된 위치를 확인하지 못했어요'
    }[phase];
    replace('3', phase === 'countdown' ? alert.seconds : phase === 'complete' ? '✓' : '—');
    replace('초 뒤에 자동으로 올려요', copy);
    windowTimer?.setAttribute('aria-label', phase === 'countdown' ? alert.seconds + copy : copy);
    if (raiseWindow) {
      raiseWindow.disabled = phase === 'applying' || phase === 'complete';
      replace('지금 올리기', phase === 'failed' ? '다시 올리기' : '지금 올리기');
    }
    replace('그대로 두기', phase === 'countdown' ? '그대로 두기' : '닫기');
  }

  if (alertMode) {
    wire(element('다음에'), 'dismissRestAlert');
    const label = alertMode === 'highway'
      ? (portrait ? '휴게소 들르기' : '죽암휴게소 들르기')
      : alertMode === 'rest5' ? '지금 들르기' : portrait ? '경유지로 추가' : '카페이소 경유지로 추가';
    const primary = element(label);
    wire(primary, 'chooseRestWaypoint');
  }

  const alertProgress = alertMode ? board.querySelectorAll('[style*="width:83%"],[style*="width:92%"]') : [];
  function restAlert(s) {
    const status = s.preview ? 'P단 · 알림 미리보기' : 'P단 · 쉬어가기 안내';
    replace('D', 'P');
    replace(/^(고속도로 )?주행 중 · 말로/, status);
    const runtimeStatus = board.querySelector('#screen-status');
    if (drive) {
      drive.setSafe(!s.preview || s.reduceMotion);
      if (s.preview && !s.reduceMotion) drive.start();
      if (runtimeStatus) runtimeStatus.textContent = status;
    }
    replace('대답해 주세요', s.preview ? '알림 화면 예시' : '쉬어갈 곳을 골라주세요');
    if (s.preview) return;
    for (const item of text) {
      if (/^(“|가는 길$|가장 가까워요$|2마리 가 있어요$)/.test(item.key)) {
        const label = item.node.parentElement;
        const row = label.closest('li');
        (row && item.key === '2마리 가 있어요' ? label.parentElement : label).style.display = 'none';
      }
    }
    replace('토이 푸들 60분마다', '동승 후 주행 시간 기준');
    replace('쉬어가기까지 (60분마다)', '쉬어가기까지');
    const elapsed = s.restAlertMinutes;
    const remaining = Math.max(1, s.restAlertDue - elapsed);
    replace(/^(10|5)분 남았어요!$/, remaining + '분 남았어요!');
    replace('10분', remaining + '분');
    replace('55분', elapsed + '분');
    replace('50 / 60분', elapsed + ' / ' + s.restAlertDue + '분');
    replace('지금 50분째 달리는 중', '동승 후 ' + elapsed + '분 주행');
    replace(/^지금 50분째 달리고 있어요/, '동승 후 ' + elapsed + '분 주행했어요. 쉴 곳을 골라요.');
    replace(/^초코가 55분째/, s.pet + '와 ' + elapsed + '분 주행했어요. 잠깐 걸어볼까요?');
    replace(/^들르면 간절곶/, '');
    replace('10분 뒤 초코', remaining + '분 뒤 ' + s.pet);
    replace('10분 뒤 초코 쉬는 시간이에요', remaining + '분 뒤 쉬는 시간이에요');
    replace('5분 뒤에는', remaining + '분 뒤에는');
    replace('5분 뒤에는 꼭 쉬어 가요', remaining + '분 뒤에는 꼭 쉬어 가요');
    replace('다리가 저려요', '잠깐 걸어볼까요');
    replace(/^(32분|간절곶까지|오후 2:42 도착)$/, '');
    const hero = board.querySelector('#screen-hero');
    if (hero && s.destination) hero.firstElementChild.firstElementChild.textContent = s.destination;
    const gait = board.querySelector('#screen-gline');
    if (gait) gait.textContent = '주차 중';
    alertProgress.forEach(node => { node.style.width = (100 * elapsed / s.restAlertDue) + '%'; });
    const first = s.restChoices[0];
    const second = s.restChoices[1];
    replace('카페이소', first ? first.name : '반려견 쉼터 보기');
    replace('간절바당', (alertMode === 'rest5' ? first?.name : second?.name) || '반려견 쉼터 보기');
    replace(/^8분 뒤 도착/, first ? first.area : '산책길에서 동반 조건을 확인해요');
    replace(/^4분 뒤 도착/, first ? first.area : '산책길에서 동반 조건을 확인해요');
    replace(/^11분 뒤 도착/, second ? second.area : '');
    const rows = board.querySelectorAll('ul > li');
    if (rows.length === 2) rows[1].style.display = second ? 'flex' : 'none';
    // The supplied portrait sample shows two example dogs at the first stop.
    const firstName = text.find(item => item.key === '카페이소' && !item.node.parentElement.closest('button'));
    const exampleDogs = portrait && firstName?.node.parentElement.parentElement.parentElement.nextElementSibling;
    if (exampleDogs) exampleDogs.style.display = 'none';
    replace('경유지로 추가', '쉼터 선택');
    replace('카페이소 경유지로 추가', '쉼터 선택');
    replace('지금 들르기', '쉼터 선택');
  }

  window.renderGaerangState = encoded => {
    const state = JSON.parse(encoded);
    board.classList.toggle('host-motion-enabled', !state.reduceMotion);
    for (const item of text) {
      if (item.original.includes('초코')) item.node.nodeValue = item.original.replaceAll('초코', state.pet);
    }
    if (screen === 'Main') home(state);
    else if (screen === 'Places') places(state);
    else if (profile) dogProfile(state);
    else if (screen === 'Arrive') arrive(state);
    else if (screen === 'Calm') care(state);
    else if (alertMode) restAlert(state);
    else if (screen === 'Alert') windowAlert(state);
    else if (driveTools) driving(state);
    updateCabin?.(state.cabin);
    updateTheme?.(state);
  };
})();
