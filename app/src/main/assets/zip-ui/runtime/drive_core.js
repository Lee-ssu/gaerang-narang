/* 개랑나랑 세로 주행 창 (640 x 906)
 * 주행 중: 달리는 칸이 커지고, 아래에 같은 목적지로 가는 강아지 수가 보여요.
 * 정차(1km/h 미만 1.5초): 초코가 멈추고, 칸이 줄어들고, 오늘의 산책길이 나타나요.
 * 사용법: var c = GaerangDrive.controller(); c.mount(element); c.setSpeed(kmh); c.start();
 */
var GaerangDrive = (function(){
'use strict';
// ---------------- 그림 재료 ----------------
var C = {base:'#C98B5B', light:'#DFA978', hi:'#EEC9A0', dark:'#A86B3E', ear:'#9A5F36', muzzle:'#F7E6D0', curl:'#8F5730', eye:'#2B1A12', collar:'#E2574C', gold:'#F2C14E', goldd:'#C89A2E', tongue:'#EE8577', blush:'#F2998A'};
function rng(seed){var s=seed%2147483647; if(s<=0)s+=2147483646; return function(){s=s*16807%2147483647; return (s-1)/2147483646;};}
function P(){return {sil:[], art:[], r:rng(7)};}
function f(n){return Math.round(n*10)/10;}
function circ(p,x,y,r,fill,sil,op){p.art.push('<circle cx="'+f(x)+'" cy="'+f(y)+'" r="'+f(r)+'" fill="'+fill+'"'+(op?' opacity="'+op+'"':'')+'></circle>'); if(sil!==false)p.sil.push('<circle cx="'+f(x)+'" cy="'+f(y)+'" r="'+f(r)+'"></circle>');}
function ell(p,x,y,rx,ry,fill,sil,op){p.art.push('<ellipse cx="'+f(x)+'" cy="'+f(y)+'" rx="'+f(rx)+'" ry="'+f(ry)+'" fill="'+fill+'"'+(op?' opacity="'+op+'"':'')+'></ellipse>'); if(sil!==false)p.sil.push('<ellipse cx="'+f(x)+'" cy="'+f(y)+'" rx="'+f(rx)+'" ry="'+f(ry)+'"></ellipse>');}
function rrect(p,x,y,w,h,fill){var s='<rect x="'+x+'" y="'+y+'" width="'+w+'" height="'+h+'" rx="'+(w/2)+'"';p.art.push(s+' fill="'+fill+'"></rect>');p.sil.push(s+'></rect>');}
function cloud(p,cx,cy,rx,ry,rc,n,fill,sil){ell(p,cx,cy,rx,ry,fill,sil);for(var i=0;i<n;i++){var a=2*Math.PI*i/n+(p.r()-.5)*.16;circ(p,cx+rx*Math.cos(a),cy+ry*Math.sin(a),rc*(.86+.26*p.r()),fill,sil);}}
function curls(p,cx,cy,rx,ry,n,color,op,w){for(var i=0;i<n;i++){var a=p.r()*6.283,d=Math.sqrt(p.r()),x=cx+rx*d*Math.cos(a),y=cy+ry*d*Math.sin(a),r=3.2+p.r()*2.2,rot=(p.r()-.5)*80;p.art.push('<path d="M'+f(x-r)+' '+f(y)+'a'+f(r)+' '+f(r)+' 0 0 1 '+f(2*r)+' 0" transform="rotate('+f(rot)+' '+f(x)+' '+f(y)+')" stroke="'+color+'" stroke-width="'+(w||2)+'" fill="none" stroke-linecap="round" opacity="'+op+'"></path>');}}
function raw(p,s){p.art.push(s);}

// 부위 만들기 (좌표: 300 x 200, 오른쪽을 봄, 바닥 y=188)
function partTail(){var p=P();raw(p,'');p.sil.push('<path d="M94 102 L76 84" stroke-width="18" stroke-linecap="round"></path>');raw(p,'<path d="M94 102 L76 84" stroke="'+C.base+'" stroke-width="10" stroke-linecap="round"></path>');cloud(p,70,76,10,9,8,9,C.base);curls(p,68,72,6,4,2,C.hi,.7,2.4);return p;}
function partThigh(back,far){var p=P(),w=back?20:17;rrect(p,-w/2,-6,w,34,far?C.dark:C.base);if(back)cloud(p,0,4,10,9,7,7,far?C.dark:C.base);curls(p,0,12,5,10,2,C.curl,.25);return p;}
function partShin(far){var p=P();rrect(p,-7.5,-3,15,29,far?C.dark:C.base);cloud(p,0,27,8,5,6,7,far?C.base:C.light);return p;}
function partTorso(){var p=P();cloud(p,150,112,60,30,13,18,C.base);cloud(p,192,104,18,22,10,8,C.base);curls(p,138,96,38,8,5,C.hi,.65,2.4);curls(p,150,114,50,20,9,C.curl,.26);return p;}
function partHead(){var p=P();cloud(p,224,72,26,24,11,13,C.base);cloud(p,218,44,16,10,9,9,C.base);curls(p,214,40,10,5,3,C.hi,.7,2.4);curls(p,222,72,18,14,5,C.curl,.25);cloud(p,250,86,15,10,8,9,C.muzzle);ell(p,240,92,9,5,C.blush,false,.45);ell(p,236,67,6.5,7.6,C.eye,false);circ(p,233.6,63.8,2.6,'#fff',false);circ(p,238.6,69.6,1.2,'#fff',false);ell(p,266,80,6.6,5.6,C.eye,false);ell(p,264,78,2.2,1.3,'#8A6656',false,.85);raw(p,'<path d="M265 87q-6 8-16 4" stroke="'+C.eye+'" stroke-width="2.6" fill="none" stroke-linecap="round"></path>');return p;}
function partTongue(){var p=P();raw(p,'<path d="M250 90q4 14 11 2z" fill="'+C.tongue+'"></path><path d="M255 92v6" stroke="#D2675B" stroke-width="1.6" stroke-linecap="round"></path>');return p;}
function partEar(){var p=P();[[210,62,11],[206,76,12],[203,90,12],[202,103,10],[205,112,7]].forEach(function(a){circ(p,a[0],a[1],a[2],C.ear);});curls(p,205,86,5,18,3,'#B57849',.6,2.4);return p;}
function partCollar(){var p=P();raw(p,'<path d="M192 90q8 20 30 20" stroke="'+C.collar+'" stroke-width="7" fill="none" stroke-linecap="round"></path><circle cx="211" cy="113" r="5" fill="'+C.gold+'" stroke="'+C.goldd+'" stroke-width="1.5"></circle>');return p;}

var LEGS = [ // 그리는 순서: 먼 쪽 다리 먼저
  {id:'LH', back:true, far:true, x:114, y:124},
  {id:'LF', back:false, far:true, x:184, y:124},
  {id:'RH', back:true, far:false, x:106, y:128},
  {id:'RF', back:false, far:false, x:190, y:128}
];
var parts = {tail:partTail(), torso:partTorso(), head:partHead(), tongue:partTongue(), ear:partEar(), collar:partCollar()};
LEGS.forEach(function(l){parts['th'+l.id]=partThigh(l.back,l.far);parts['sh'+l.id]=partShin(l.far);});

function rigMarkup(layer){
  function g(name){var p=parts[name]; return layer==='sil'? p.sil.join('') : p.art.join('');}
  var legs = function(far){return LEGS.filter(function(l){return l.far===far;}).map(function(l){return '<g data-k="leg'+l.id+'" transform="translate('+l.x+' '+l.y+')">'+g('th'+l.id)+'<g data-k="knee'+l.id+'" transform="translate(0 26)">'+g('sh'+l.id)+'</g></g>';}).join('');};
  return '<g data-k="body">'+
    '<g data-k="tail">'+g('tail')+'</g>'+
    legs(true)+g('torso')+legs(false)+
    '<g data-k="head">'+g('head')+'<g data-k="tongue">'+g('tongue')+'</g><g data-k="ear">'+g('ear')+'</g>'+g('collar')+'</g>'+
  '</g>';
}
function buildRig(svgRoot, scale, tx, ty){
  var wrap = '<g transform="translate('+tx+' '+ty+') scale('+scale+')">'+
    '<ellipse data-k="shadow" cx="150" cy="190" rx="70" ry="6" fill="#2A201A" opacity="0.12"></ellipse>'+
    '<g fill="#FFFFFF" stroke="#FFFFFF" stroke-width="7" stroke-linejoin="round" data-layer="sil">'+rigMarkup('sil')+'</g>'+
    '<g data-layer="art">'+rigMarkup('art')+'</g></g>';
  svgRoot.insertAdjacentHTML('beforeend', wrap);
  var nodes = {}; // 이름 -> [윤곽, 그림]
  svgRoot.querySelectorAll('[data-k]').forEach(function(n){var k=n.getAttribute('data-k');(nodes[k]=nodes[k]||[]).push(n);});
  return nodes;
}
function setT(nodes,k,t){var a=nodes[k];if(!a)return;for(var i=0;i<a.length;i++)a[i].setAttribute('transform',t);}


// ---------------- 걸음걸이 ----------------
var K = 2.4; // 1km/h당 바닥 흐름 px/s (그림 배율 1 기준)
var GAITS = {
  idle:  {name:'쉬는 중', L:0,   amp:0,  knee:0,  off:{LH:0,LF:0,RH:0,RF:0}},
  walk:  {name:'걷기',     L:34,  amp:13, knee:24, off:{LH:0,LF:.25,RH:.5,RF:.75}, bob:1.4},
  trot:  {name:'총총 뛰기', L:58,  amp:19, knee:34, off:{LH:.5,LF:0,RH:0,RF:.5},   bob:2.6},
  gallop:{name:'전력 질주', L:104, amp:28, knee:44, off:{LH:0,LF:.55,RH:.08,RF:.45}, bob:5}
};
function nextGait(cur, v, stillFor){
  if (v < 1) return stillFor > 1.5 ? 'idle' : (cur==='idle'?'idle':'walk');
  if (cur==='gallop') return v < 58 ? (v < 20 ? 'walk':'trot') : 'gallop';
  if (cur==='trot') return v >= 65 ? 'gallop' : (v < 20 ? 'walk' : 'trot');
  return v >= 65 ? 'gallop' : (v >= 25 ? 'trot' : 'walk');
}


// ---------------- 포즈 계산 ----------------
function pose(gk, ph, v){
  var G=GAITS[gk], out={legs:{}, bob:0, pitch:0, ear:0, tail:0, head:0};
  if (gk==='idle'){ LEGS.forEach(function(l){out.legs[l.id]=[0,0];}); return out; }
  LEGS.forEach(function(l){
    var a=2*Math.PI*(ph+G.off[l.id]);
    var th=G.amp*Math.sin(a);
    var swing=Math.max(0,-Math.cos(a));
    var kn=(l.back?-1:1)*G.knee*swing;
    out.legs[l.id]=[th,kn];
  });
  var a2=2*Math.PI*ph;
  if (gk==='walk'){out.bob=G.bob*Math.sin(2*a2);out.head=1.5*Math.sin(2*a2);}
  if (gk==='trot'){out.bob=-G.bob*Math.abs(Math.sin(a2));out.head=2*Math.sin(2*a2);}
  if (gk==='gallop'){out.bob=-G.bob*Math.max(0,Math.sin(a2));out.pitch=4*Math.sin(a2+.6);out.head=-3*Math.sin(a2);}
  out.ear=Math.min(28,v*.32)+({walk:4,trot:8,gallop:14}[gk])*Math.sin(a2*(gk==='walk'?2:1)-1.2);
  return out;
}
function mix(a,b,t){return a+(b-a)*t;}
function apply(nodes, P, t, tail, showTongue){
  LEGS.forEach(function(l){var v=P.legs[l.id];setT(nodes,'leg'+l.id,'translate('+l.x+' '+l.y+') rotate('+f(v[0])+')');setT(nodes,'knee'+l.id,'translate(0 26) rotate('+f(v[1])+')');});
  setT(nodes,'body','translate(0 '+f(P.bob)+') rotate('+f(P.pitch)+' 150 120)');
  setT(nodes,'head','rotate('+f(P.head)+' 200 96)');
  setT(nodes,'ear','rotate('+f(P.ear)+' 210 58)');
  setT(nodes,'tail','rotate('+f(tail)+' 94 102)');
  var tg=nodes.tongue; if(tg) for(var i=0;i<tg.length;i++) tg[i].style.display=showTongue?'':'none';
  var sh=nodes.shadow; if(sh) for(i=0;i<sh.length;i++) sh[i].setAttribute('rx', f(70+P.bob*2));
}


var HEADS = {"choco": "<svg viewBox=\"10 18 220 214\" width=\"100%\" height=\"100%\" aria-hidden=\"true\" style=\"display: block; flex: none; overflow: visible;\"><circle cx=\"58\" cy=\"112\" r=\"24\" fill=\"#9A5F36\"></circle><circle cx=\"52\" cy=\"134\" r=\"25\" fill=\"#9A5F36\"></circle><circle cx=\"50\" cy=\"158\" r=\"25\" fill=\"#9A5F36\"></circle><circle cx=\"52\" cy=\"182\" r=\"23\" fill=\"#9A5F36\"></circle><circle cx=\"60\" cy=\"202\" r=\"20\" fill=\"#9A5F36\"></circle><circle cx=\"70\" cy=\"212\" r=\"15\" fill=\"#9A5F36\"></circle><circle cx=\"57\" cy=\"130\" r=\"9\" fill=\"#B57849\" opacity=\"0.55\"></circle><circle cx=\"55\" cy=\"154\" r=\"9\" fill=\"#B57849\" opacity=\"0.55\"></circle><circle cx=\"57\" cy=\"178\" r=\"9\" fill=\"#B57849\" opacity=\"0.55\"></circle><circle cx=\"65\" cy=\"198\" r=\"9\" fill=\"#B57849\" opacity=\"0.55\"></circle><path d=\"M46.3 173.9a4.9 4.9 0 0 1 9.8 0\" transform=\"rotate(2.9 51.2 173.9)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M46.8 167.2a4.6 4.6 0 0 1 9.2 0\" transform=\"rotate(-5.3 51.4 167.2)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M53.9 165.1a4.4 4.4 0 0 1 8.9 0\" transform=\"rotate(-30.1 58.4 165.1)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M50.6 191.2a5.5 5.5 0 0 1 11 0\" transform=\"rotate(-8.3 56.1 191.2)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M52.1 158.7a5.3 5.3 0 0 1 10.6 0\" transform=\"rotate(-28.5 57.4 158.7)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M55.3 175a5.2 5.2 0 0 1 10.5 0\" transform=\"rotate(6.5 60.6 175)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><circle cx=\"182\" cy=\"112\" r=\"24\" fill=\"#9A5F36\"></circle><circle cx=\"188\" cy=\"134\" r=\"25\" fill=\"#9A5F36\"></circle><circle cx=\"190\" cy=\"158\" r=\"25\" fill=\"#9A5F36\"></circle><circle cx=\"188\" cy=\"182\" r=\"23\" fill=\"#9A5F36\"></circle><circle cx=\"180\" cy=\"202\" r=\"20\" fill=\"#9A5F36\"></circle><circle cx=\"170\" cy=\"212\" r=\"15\" fill=\"#9A5F36\"></circle><circle cx=\"183\" cy=\"130\" r=\"9\" fill=\"#B57849\" opacity=\"0.55\"></circle><circle cx=\"185\" cy=\"154\" r=\"9\" fill=\"#B57849\" opacity=\"0.55\"></circle><circle cx=\"183\" cy=\"178\" r=\"9\" fill=\"#B57849\" opacity=\"0.55\"></circle><circle cx=\"175\" cy=\"198\" r=\"9\" fill=\"#B57849\" opacity=\"0.55\"></circle><path d=\"M175 141.3a4.7 4.7 0 0 1 9.4 0\" transform=\"rotate(-35.2 179.7 141.3)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M185.2 191.7a4.5 4.5 0 0 1 8.9 0\" transform=\"rotate(6.8 189.6 191.7)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M172.4 166.3a5.2 5.2 0 0 1 10.4 0\" transform=\"rotate(-20.5 177.6 166.3)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M170.3 146.9a5.4 5.4 0 0 1 10.7 0\" transform=\"rotate(-17 175.6 146.9)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M187 158.3a4.4 4.4 0 0 1 8.9 0\" transform=\"rotate(-27.8 191.5 158.3)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M177.9 160.5a4.9 4.9 0 0 1 9.9 0\" transform=\"rotate(5.8 182.8 160.5)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><ellipse cx=\"120\" cy=\"128\" rx=\"64\" ry=\"60\" fill=\"#C98B5B\"></ellipse><circle cx=\"183.9\" cy=\"131.6\" r=\"17.9\" fill=\"#C98B5B\"></circle><circle cx=\"178.3\" cy=\"152.7\" r=\"19.3\" fill=\"#C98B5B\"></circle><circle cx=\"164.7\" cy=\"171\" r=\"18.6\" fill=\"#C98B5B\"></circle><circle cx=\"141.2\" cy=\"184.6\" r=\"21\" fill=\"#C98B5B\"></circle><circle cx=\"120.3\" cy=\"188\" r=\"19.6\" fill=\"#C98B5B\"></circle><circle cx=\"99.7\" cy=\"184.9\" r=\"19.8\" fill=\"#C98B5B\"></circle><circle cx=\"73.7\" cy=\"169.4\" r=\"21.2\" fill=\"#C98B5B\"></circle><circle cx=\"59.7\" cy=\"148.1\" r=\"17.7\" fill=\"#C98B5B\"></circle><circle cx=\"56\" cy=\"129.1\" r=\"19.6\" fill=\"#C98B5B\"></circle><circle cx=\"59.2\" cy=\"109.3\" r=\"18.6\" fill=\"#C98B5B\"></circle><circle cx=\"72.4\" cy=\"87.9\" r=\"16.9\" fill=\"#C98B5B\"></circle><circle cx=\"91.4\" cy=\"74.3\" r=\"20.1\" fill=\"#C98B5B\"></circle><circle cx=\"116.2\" cy=\"68.1\" r=\"17.6\" fill=\"#C98B5B\"></circle><circle cx=\"143.5\" cy=\"72.2\" r=\"20.6\" fill=\"#C98B5B\"></circle><circle cx=\"162.1\" cy=\"82.8\" r=\"18.6\" fill=\"#C98B5B\"></circle><circle cx=\"179.3\" cy=\"105.5\" r=\"20.7\" fill=\"#C98B5B\"></circle><ellipse cx=\"120\" cy=\"156\" rx=\"52\" ry=\"26\" fill=\"#A86B3E\" opacity=\"0.22\"></ellipse><path d=\"M108.8 85.1a4.6 4.6 0 0 1 9.1 0\" transform=\"rotate(-11.3 113.3 85.1)\" stroke=\"#EEC9A0\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.65\"></path><path d=\"M120.5 89a4.3 4.3 0 0 1 8.6 0\" transform=\"rotate(-21.4 124.8 89)\" stroke=\"#EEC9A0\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.65\"></path><path d=\"M97.3 115.9a5.2 5.2 0 0 1 10.4 0\" transform=\"rotate(-39.7 102.5 115.9)\" stroke=\"#EEC9A0\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.65\"></path><path d=\"M76.8 107.9a5.1 5.1 0 0 1 10.3 0\" transform=\"rotate(15.2 82 107.9)\" stroke=\"#EEC9A0\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.65\"></path><path d=\"M68.1 100.5a5.4 5.4 0 0 1 10.7 0\" transform=\"rotate(32 73.4 100.5)\" stroke=\"#EEC9A0\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.65\"></path><path d=\"M123.9 86.8a5.2 5.2 0 0 1 10.4 0\" transform=\"rotate(-8.1 129.1 86.8)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M149.2 134.4a3.7 3.7 0 0 1 7.4 0\" transform=\"rotate(-23.3 153 134.4)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M132.2 134.9a3.7 3.7 0 0 1 7.4 0\" transform=\"rotate(-27.9 135.9 134.9)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M141.5 130.2a3.7 3.7 0 0 1 7.3 0\" transform=\"rotate(9.1 145.2 130.2)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M131.2 131.7a4.3 4.3 0 0 1 8.6 0\" transform=\"rotate(-30.2 135.5 131.7)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M145.6 90.5a4.5 4.5 0 0 1 9.1 0\" transform=\"rotate(-33.1 150.2 90.5)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M140.2 129.9a4.1 4.1 0 0 1 8.3 0\" transform=\"rotate(-27.1 144.4 129.9)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M165.5 122.8a4.7 4.7 0 0 1 9.3 0\" transform=\"rotate(3.5 170.2 122.8)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M151.7 122.2a5.6 5.6 0 0 1 11.1 0\" transform=\"rotate(15.7 157.2 122.2)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><ellipse cx=\"120\" cy=\"64\" rx=\"40\" ry=\"24\" fill=\"#C98B5B\"></ellipse><circle cx=\"158.6\" cy=\"70.2\" r=\"16.2\" fill=\"#C98B5B\"></circle><circle cx=\"147.3\" cy=\"81.5\" r=\"18\" fill=\"#C98B5B\"></circle><circle cx=\"124.9\" cy=\"87.8\" r=\"18.1\" fill=\"#C98B5B\"></circle><circle cx=\"103.9\" cy=\"86\" r=\"15.6\" fill=\"#C98B5B\"></circle><circle cx=\"85\" cy=\"75.7\" r=\"19\" fill=\"#C98B5B\"></circle><circle cx=\"80.1\" cy=\"62.3\" r=\"18.2\" fill=\"#C98B5B\"></circle><circle cx=\"87.8\" cy=\"49.7\" r=\"17.9\" fill=\"#C98B5B\"></circle><circle cx=\"102.3\" cy=\"42.5\" r=\"16.9\" fill=\"#C98B5B\"></circle><circle cx=\"125.3\" cy=\"40.2\" r=\"14.7\" fill=\"#C98B5B\"></circle><circle cx=\"144.3\" cy=\"44.9\" r=\"15.9\" fill=\"#C98B5B\"></circle><circle cx=\"158.1\" cy=\"56.7\" r=\"17.7\" fill=\"#C98B5B\"></circle><path d=\"M122.2 55.8a5.9 5.9 0 0 1 11.7 0\" transform=\"rotate(36.4 128 55.8)\" stroke=\"#EEC9A0\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.7\"></path><path d=\"M96.9 62.2a4.5 4.5 0 0 1 8.9 0\" transform=\"rotate(-23.7 101.3 62.2)\" stroke=\"#EEC9A0\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.7\"></path><path d=\"M85.4 50a5.7 5.7 0 0 1 11.4 0\" transform=\"rotate(12.2 91.1 50)\" stroke=\"#EEC9A0\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.7\"></path><path d=\"M107.2 54.7a5.3 5.3 0 0 1 10.6 0\" transform=\"rotate(22.6 112.5 54.7)\" stroke=\"#EEC9A0\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.7\"></path><path d=\"M120.1 54.9a4 4 0 0 1 7.9 0\" transform=\"rotate(-13.4 124 54.9)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M129.5 51a4.4 4.4 0 0 1 8.8 0\" transform=\"rotate(35.7 133.9 51)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M118.1 59.5a3.9 3.9 0 0 1 7.7 0\" transform=\"rotate(32.4 121.9 59.5)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M123 60.3a5.3 5.3 0 0 1 10.5 0\" transform=\"rotate(12.6 128.3 60.3)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M106.2 75.6a3.9 3.9 0 0 1 7.7 0\" transform=\"rotate(37.7 110 75.6)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M104.8 56.6a5.5 5.5 0 0 1 10.9 0\" transform=\"rotate(29.7 110.3 56.6)\" stroke=\"#8F5730\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><circle cx=\"100\" cy=\"166\" r=\"27\" fill=\"#F7E6D0\"></circle><circle cx=\"140\" cy=\"166\" r=\"27\" fill=\"#F7E6D0\"></circle><circle cx=\"120\" cy=\"178\" r=\"24\" fill=\"#F7E6D0\"></circle><circle cx=\"120\" cy=\"152\" r=\"22\" fill=\"#F7E6D0\"></circle><ellipse cx=\"120\" cy=\"188\" rx=\"26\" ry=\"8\" fill=\"#EACDAD\" opacity=\"0.8\"></ellipse><ellipse cx=\"76\" cy=\"160\" rx=\"13\" ry=\"8\" fill=\"#F2998A\" opacity=\"0.45\"></ellipse><ellipse cx=\"164\" cy=\"160\" rx=\"13\" ry=\"8\" fill=\"#F2998A\" opacity=\"0.45\"></ellipse><ellipse cx=\"92\" cy=\"130\" rx=\"13\" ry=\"15\" fill=\"#2B1A12\"></ellipse><ellipse cx=\"93\" cy=\"133\" rx=\"9\" ry=\"10\" fill=\"#5B3826\" opacity=\"0.55\"></ellipse><circle cx=\"87.5\" cy=\"124\" r=\"5.2\" fill=\"#FFFFFF\"></circle><circle cx=\"97\" cy=\"135\" r=\"2.3\" fill=\"#FFFFFF\"></circle><ellipse cx=\"148\" cy=\"130\" rx=\"13\" ry=\"15\" fill=\"#2B1A12\"></ellipse><ellipse cx=\"149\" cy=\"133\" rx=\"9\" ry=\"10\" fill=\"#5B3826\" opacity=\"0.55\"></ellipse><circle cx=\"143.5\" cy=\"124\" r=\"5.2\" fill=\"#FFFFFF\"></circle><circle cx=\"153\" cy=\"135\" r=\"2.3\" fill=\"#FFFFFF\"></circle><path d=\"M106 150q14 -9 28 0q-1 13 -14 17q-13 -4 -14 -17z\" fill=\"#2B1A12\"></path><ellipse cx=\"114\" cy=\"151\" rx=\"5.500\" ry=\"2.600\" fill=\"#8A6656\" opacity=\"0.850\"></ellipse><path d=\"M112 174q8 17 16 0z\" fill=\"#EE8577\"></path><path d=\"M120 176v6\" stroke=\"#D2675B\" stroke-width=\"2\" stroke-linecap=\"round\"></path><path d=\"M120 166v6M120 172q-8 9 -16 3M120 172q8 9 16 3\" stroke=\"#2B1A12\" stroke-width=\"3.400\" fill=\"none\" stroke-linecap=\"round\" stroke-linejoin=\"round\"></path></svg>", "dubu": "<svg viewBox=\"10 18 220 214\" width=\"100%\" height=\"100%\" aria-hidden=\"true\" style=\"display: block; flex: none; overflow: visible;\"><g fill=\"#CDBFAB\" stroke=\"#CDBFAB\" stroke-width=\"3\"><circle cx=\"58\" cy=\"112\" r=\"24\"></circle><circle cx=\"52\" cy=\"134\" r=\"25\"></circle><circle cx=\"50\" cy=\"158\" r=\"25\"></circle><circle cx=\"52\" cy=\"182\" r=\"23\"></circle><circle cx=\"60\" cy=\"202\" r=\"20\"></circle><circle cx=\"70\" cy=\"212\" r=\"15\"></circle><circle cx=\"182\" cy=\"112\" r=\"24\"></circle><circle cx=\"188\" cy=\"134\" r=\"25\"></circle><circle cx=\"190\" cy=\"158\" r=\"25\"></circle><circle cx=\"188\" cy=\"182\" r=\"23\"></circle><circle cx=\"180\" cy=\"202\" r=\"20\"></circle><circle cx=\"170\" cy=\"212\" r=\"15\"></circle><ellipse cx=\"120\" cy=\"128\" rx=\"64\" ry=\"60\"></ellipse><circle cx=\"183.9\" cy=\"131.6\" r=\"17.9\"></circle><circle cx=\"178.3\" cy=\"152.7\" r=\"19.3\"></circle><circle cx=\"164.7\" cy=\"171\" r=\"18.6\"></circle><circle cx=\"141.2\" cy=\"184.6\" r=\"21\"></circle><circle cx=\"120.3\" cy=\"188\" r=\"19.6\"></circle><circle cx=\"99.7\" cy=\"184.9\" r=\"19.8\"></circle><circle cx=\"73.7\" cy=\"169.4\" r=\"21.2\"></circle><circle cx=\"59.7\" cy=\"148.1\" r=\"17.7\"></circle><circle cx=\"56\" cy=\"129.1\" r=\"19.6\"></circle><circle cx=\"59.2\" cy=\"109.3\" r=\"18.6\"></circle><circle cx=\"72.4\" cy=\"87.9\" r=\"16.9\"></circle><circle cx=\"91.4\" cy=\"74.3\" r=\"20.1\"></circle><circle cx=\"116.2\" cy=\"68.1\" r=\"17.6\"></circle><circle cx=\"143.5\" cy=\"72.2\" r=\"20.6\"></circle><circle cx=\"162.1\" cy=\"82.8\" r=\"18.6\"></circle><circle cx=\"179.3\" cy=\"105.5\" r=\"20.7\"></circle><ellipse cx=\"120\" cy=\"64\" rx=\"40\" ry=\"24\"></ellipse><circle cx=\"158.6\" cy=\"70.2\" r=\"16.2\"></circle><circle cx=\"147.3\" cy=\"81.5\" r=\"18\"></circle><circle cx=\"124.9\" cy=\"87.8\" r=\"18.1\"></circle><circle cx=\"103.9\" cy=\"86\" r=\"15.6\"></circle><circle cx=\"85\" cy=\"75.7\" r=\"19\"></circle><circle cx=\"80.1\" cy=\"62.3\" r=\"18.2\"></circle><circle cx=\"87.8\" cy=\"49.7\" r=\"17.9\"></circle><circle cx=\"102.3\" cy=\"42.5\" r=\"16.9\"></circle><circle cx=\"125.3\" cy=\"40.2\" r=\"14.7\"></circle><circle cx=\"144.3\" cy=\"44.9\" r=\"15.9\"></circle><circle cx=\"158.1\" cy=\"56.7\" r=\"17.7\"></circle></g><circle cx=\"58\" cy=\"112\" r=\"24\" fill=\"#EDE3D3\"></circle><circle cx=\"52\" cy=\"134\" r=\"25\" fill=\"#EDE3D3\"></circle><circle cx=\"50\" cy=\"158\" r=\"25\" fill=\"#EDE3D3\"></circle><circle cx=\"52\" cy=\"182\" r=\"23\" fill=\"#EDE3D3\"></circle><circle cx=\"60\" cy=\"202\" r=\"20\" fill=\"#EDE3D3\"></circle><circle cx=\"70\" cy=\"212\" r=\"15\" fill=\"#EDE3D3\"></circle><circle cx=\"57\" cy=\"130\" r=\"9\" fill=\"#F8F2E8\" opacity=\"0.55\"></circle><circle cx=\"55\" cy=\"154\" r=\"9\" fill=\"#F8F2E8\" opacity=\"0.55\"></circle><circle cx=\"57\" cy=\"178\" r=\"9\" fill=\"#F8F2E8\" opacity=\"0.55\"></circle><circle cx=\"65\" cy=\"198\" r=\"9\" fill=\"#F8F2E8\" opacity=\"0.55\"></circle><path d=\"M46.3 173.9a4.9 4.9 0 0 1 9.8 0\" transform=\"rotate(2.9 51.2 173.9)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M46.8 167.2a4.6 4.6 0 0 1 9.2 0\" transform=\"rotate(-5.3 51.4 167.2)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M53.9 165.1a4.4 4.4 0 0 1 8.9 0\" transform=\"rotate(-30.1 58.4 165.1)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M50.6 191.2a5.5 5.5 0 0 1 11 0\" transform=\"rotate(-8.3 56.1 191.2)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M52.1 158.7a5.3 5.3 0 0 1 10.6 0\" transform=\"rotate(-28.5 57.4 158.7)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M55.3 175a5.2 5.2 0 0 1 10.5 0\" transform=\"rotate(6.5 60.6 175)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><circle cx=\"182\" cy=\"112\" r=\"24\" fill=\"#EDE3D3\"></circle><circle cx=\"188\" cy=\"134\" r=\"25\" fill=\"#EDE3D3\"></circle><circle cx=\"190\" cy=\"158\" r=\"25\" fill=\"#EDE3D3\"></circle><circle cx=\"188\" cy=\"182\" r=\"23\" fill=\"#EDE3D3\"></circle><circle cx=\"180\" cy=\"202\" r=\"20\" fill=\"#EDE3D3\"></circle><circle cx=\"170\" cy=\"212\" r=\"15\" fill=\"#EDE3D3\"></circle><circle cx=\"183\" cy=\"130\" r=\"9\" fill=\"#F8F2E8\" opacity=\"0.55\"></circle><circle cx=\"185\" cy=\"154\" r=\"9\" fill=\"#F8F2E8\" opacity=\"0.55\"></circle><circle cx=\"183\" cy=\"178\" r=\"9\" fill=\"#F8F2E8\" opacity=\"0.55\"></circle><circle cx=\"175\" cy=\"198\" r=\"9\" fill=\"#F8F2E8\" opacity=\"0.55\"></circle><path d=\"M175 141.3a4.7 4.7 0 0 1 9.4 0\" transform=\"rotate(-35.2 179.7 141.3)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M185.2 191.7a4.5 4.5 0 0 1 8.9 0\" transform=\"rotate(6.8 189.6 191.7)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M172.4 166.3a5.2 5.2 0 0 1 10.4 0\" transform=\"rotate(-20.5 177.6 166.3)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M170.3 146.9a5.4 5.4 0 0 1 10.7 0\" transform=\"rotate(-17 175.6 146.9)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M187 158.3a4.4 4.4 0 0 1 8.9 0\" transform=\"rotate(-27.8 191.5 158.3)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M177.9 160.5a4.9 4.9 0 0 1 9.9 0\" transform=\"rotate(5.8 182.8 160.5)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><ellipse cx=\"120\" cy=\"128\" rx=\"64\" ry=\"60\" fill=\"#F6F0E6\"></ellipse><circle cx=\"183.9\" cy=\"131.6\" r=\"17.9\" fill=\"#F6F0E6\"></circle><circle cx=\"178.3\" cy=\"152.7\" r=\"19.3\" fill=\"#F6F0E6\"></circle><circle cx=\"164.7\" cy=\"171\" r=\"18.6\" fill=\"#F6F0E6\"></circle><circle cx=\"141.2\" cy=\"184.6\" r=\"21\" fill=\"#F6F0E6\"></circle><circle cx=\"120.3\" cy=\"188\" r=\"19.6\" fill=\"#F6F0E6\"></circle><circle cx=\"99.7\" cy=\"184.9\" r=\"19.8\" fill=\"#F6F0E6\"></circle><circle cx=\"73.7\" cy=\"169.4\" r=\"21.2\" fill=\"#F6F0E6\"></circle><circle cx=\"59.7\" cy=\"148.1\" r=\"17.7\" fill=\"#F6F0E6\"></circle><circle cx=\"56\" cy=\"129.1\" r=\"19.6\" fill=\"#F6F0E6\"></circle><circle cx=\"59.2\" cy=\"109.3\" r=\"18.6\" fill=\"#F6F0E6\"></circle><circle cx=\"72.4\" cy=\"87.9\" r=\"16.9\" fill=\"#F6F0E6\"></circle><circle cx=\"91.4\" cy=\"74.3\" r=\"20.1\" fill=\"#F6F0E6\"></circle><circle cx=\"116.2\" cy=\"68.1\" r=\"17.6\" fill=\"#F6F0E6\"></circle><circle cx=\"143.5\" cy=\"72.2\" r=\"20.6\" fill=\"#F6F0E6\"></circle><circle cx=\"162.1\" cy=\"82.8\" r=\"18.6\" fill=\"#F6F0E6\"></circle><circle cx=\"179.3\" cy=\"105.5\" r=\"20.7\" fill=\"#F6F0E6\"></circle><ellipse cx=\"120\" cy=\"156\" rx=\"52\" ry=\"26\" fill=\"#E2D6C4\" opacity=\"0.22\"></ellipse><path d=\"M108.8 85.1a4.6 4.6 0 0 1 9.1 0\" transform=\"rotate(-11.3 113.3 85.1)\" stroke=\"#FFFFFF\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.65\"></path><path d=\"M120.5 89a4.3 4.3 0 0 1 8.6 0\" transform=\"rotate(-21.4 124.8 89)\" stroke=\"#FFFFFF\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.65\"></path><path d=\"M97.3 115.9a5.2 5.2 0 0 1 10.4 0\" transform=\"rotate(-39.7 102.5 115.9)\" stroke=\"#FFFFFF\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.65\"></path><path d=\"M76.8 107.9a5.1 5.1 0 0 1 10.3 0\" transform=\"rotate(15.2 82 107.9)\" stroke=\"#FFFFFF\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.65\"></path><path d=\"M68.1 100.5a5.4 5.4 0 0 1 10.7 0\" transform=\"rotate(32 73.4 100.5)\" stroke=\"#FFFFFF\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.65\"></path><path d=\"M123.9 86.8a5.2 5.2 0 0 1 10.4 0\" transform=\"rotate(-8.1 129.1 86.8)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M149.2 134.4a3.7 3.7 0 0 1 7.4 0\" transform=\"rotate(-23.3 153 134.4)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M132.2 134.9a3.7 3.7 0 0 1 7.4 0\" transform=\"rotate(-27.9 135.9 134.9)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M141.5 130.2a3.7 3.7 0 0 1 7.3 0\" transform=\"rotate(9.1 145.2 130.2)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M131.2 131.7a4.3 4.3 0 0 1 8.6 0\" transform=\"rotate(-30.2 135.5 131.7)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M145.6 90.5a4.5 4.5 0 0 1 9.1 0\" transform=\"rotate(-33.1 150.2 90.5)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M140.2 129.9a4.1 4.1 0 0 1 8.3 0\" transform=\"rotate(-27.1 144.4 129.9)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M165.5 122.8a4.7 4.7 0 0 1 9.3 0\" transform=\"rotate(3.5 170.2 122.8)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M151.7 122.2a5.6 5.6 0 0 1 11.1 0\" transform=\"rotate(15.7 157.2 122.2)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><ellipse cx=\"120\" cy=\"64\" rx=\"40\" ry=\"24\" fill=\"#F6F0E6\"></ellipse><circle cx=\"158.6\" cy=\"70.2\" r=\"16.2\" fill=\"#F6F0E6\"></circle><circle cx=\"147.3\" cy=\"81.5\" r=\"18\" fill=\"#F6F0E6\"></circle><circle cx=\"124.9\" cy=\"87.8\" r=\"18.1\" fill=\"#F6F0E6\"></circle><circle cx=\"103.9\" cy=\"86\" r=\"15.6\" fill=\"#F6F0E6\"></circle><circle cx=\"85\" cy=\"75.7\" r=\"19\" fill=\"#F6F0E6\"></circle><circle cx=\"80.1\" cy=\"62.3\" r=\"18.2\" fill=\"#F6F0E6\"></circle><circle cx=\"87.8\" cy=\"49.7\" r=\"17.9\" fill=\"#F6F0E6\"></circle><circle cx=\"102.3\" cy=\"42.5\" r=\"16.9\" fill=\"#F6F0E6\"></circle><circle cx=\"125.3\" cy=\"40.2\" r=\"14.7\" fill=\"#F6F0E6\"></circle><circle cx=\"144.3\" cy=\"44.9\" r=\"15.9\" fill=\"#F6F0E6\"></circle><circle cx=\"158.1\" cy=\"56.7\" r=\"17.7\" fill=\"#F6F0E6\"></circle><path d=\"M122.2 55.8a5.9 5.9 0 0 1 11.7 0\" transform=\"rotate(36.4 128 55.8)\" stroke=\"#FFFFFF\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.7\"></path><path d=\"M96.9 62.2a4.5 4.5 0 0 1 8.9 0\" transform=\"rotate(-23.7 101.3 62.2)\" stroke=\"#FFFFFF\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.7\"></path><path d=\"M85.4 50a5.7 5.7 0 0 1 11.4 0\" transform=\"rotate(12.2 91.1 50)\" stroke=\"#FFFFFF\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.7\"></path><path d=\"M107.2 54.7a5.3 5.3 0 0 1 10.6 0\" transform=\"rotate(22.6 112.5 54.7)\" stroke=\"#FFFFFF\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.7\"></path><path d=\"M120.1 54.9a4 4 0 0 1 7.9 0\" transform=\"rotate(-13.4 124 54.9)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M129.5 51a4.4 4.4 0 0 1 8.8 0\" transform=\"rotate(35.7 133.9 51)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M118.1 59.5a3.9 3.9 0 0 1 7.7 0\" transform=\"rotate(32.4 121.9 59.5)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M123 60.3a5.3 5.3 0 0 1 10.5 0\" transform=\"rotate(12.6 128.3 60.3)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M106.2 75.6a3.9 3.9 0 0 1 7.7 0\" transform=\"rotate(37.7 110 75.6)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M104.8 56.6a5.5 5.5 0 0 1 10.9 0\" transform=\"rotate(29.7 110.3 56.6)\" stroke=\"#CDBFAB\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><circle cx=\"100\" cy=\"166\" r=\"27\" fill=\"#FFFFFF\"></circle><circle cx=\"140\" cy=\"166\" r=\"27\" fill=\"#FFFFFF\"></circle><circle cx=\"120\" cy=\"178\" r=\"24\" fill=\"#FFFFFF\"></circle><circle cx=\"120\" cy=\"152\" r=\"22\" fill=\"#FFFFFF\"></circle><ellipse cx=\"120\" cy=\"188\" rx=\"26\" ry=\"8\" fill=\"#EFE6D8\" opacity=\"0.8\"></ellipse><ellipse cx=\"76\" cy=\"160\" rx=\"13\" ry=\"8\" fill=\"#F2998A\" opacity=\"0.45\"></ellipse><ellipse cx=\"164\" cy=\"160\" rx=\"13\" ry=\"8\" fill=\"#F2998A\" opacity=\"0.45\"></ellipse><ellipse cx=\"92\" cy=\"130\" rx=\"13\" ry=\"15\" fill=\"#2B1A12\"></ellipse><ellipse cx=\"93\" cy=\"133\" rx=\"9\" ry=\"10\" fill=\"#5B3826\" opacity=\"0.55\"></ellipse><circle cx=\"87.5\" cy=\"124\" r=\"5.2\" fill=\"#FFFFFF\"></circle><circle cx=\"97\" cy=\"135\" r=\"2.3\" fill=\"#FFFFFF\"></circle><ellipse cx=\"148\" cy=\"130\" rx=\"13\" ry=\"15\" fill=\"#2B1A12\"></ellipse><ellipse cx=\"149\" cy=\"133\" rx=\"9\" ry=\"10\" fill=\"#5B3826\" opacity=\"0.55\"></ellipse><circle cx=\"143.5\" cy=\"124\" r=\"5.2\" fill=\"#FFFFFF\"></circle><circle cx=\"153\" cy=\"135\" r=\"2.3\" fill=\"#FFFFFF\"></circle><path d=\"M106 150q14 -9 28 0q-1 13 -14 17q-13 -4 -14 -17z\" fill=\"#2B1A12\"></path><ellipse cx=\"114\" cy=\"151\" rx=\"5.500\" ry=\"2.600\" fill=\"#8A6656\" opacity=\"0.850\"></ellipse><path d=\"M112 174q8 17 16 0z\" fill=\"#EE8577\"></path><path d=\"M120 176v6\" stroke=\"#D2675B\" stroke-width=\"2\" stroke-linecap=\"round\"></path><path d=\"M120 166v6M120 172q-8 9 -16 3M120 172q8 9 16 3\" stroke=\"#2B1A12\" stroke-width=\"3.400\" fill=\"none\" stroke-linecap=\"round\" stroke-linejoin=\"round\"></path></svg>", "kongi": "<svg viewBox=\"10 18 220 214\" width=\"100%\" height=\"100%\" aria-hidden=\"true\" style=\"display: block; flex: none; overflow: visible;\"><circle cx=\"58\" cy=\"112\" r=\"24\" fill=\"#30241D\"></circle><circle cx=\"52\" cy=\"134\" r=\"25\" fill=\"#30241D\"></circle><circle cx=\"50\" cy=\"158\" r=\"25\" fill=\"#30241D\"></circle><circle cx=\"52\" cy=\"182\" r=\"23\" fill=\"#30241D\"></circle><circle cx=\"60\" cy=\"202\" r=\"20\" fill=\"#30241D\"></circle><circle cx=\"70\" cy=\"212\" r=\"15\" fill=\"#30241D\"></circle><circle cx=\"57\" cy=\"130\" r=\"9\" fill=\"#45352B\" opacity=\"0.55\"></circle><circle cx=\"55\" cy=\"154\" r=\"9\" fill=\"#45352B\" opacity=\"0.55\"></circle><circle cx=\"57\" cy=\"178\" r=\"9\" fill=\"#45352B\" opacity=\"0.55\"></circle><circle cx=\"65\" cy=\"198\" r=\"9\" fill=\"#45352B\" opacity=\"0.55\"></circle><path d=\"M46.3 173.9a4.9 4.9 0 0 1 9.8 0\" transform=\"rotate(2.9 51.2 173.9)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M46.8 167.2a4.6 4.6 0 0 1 9.2 0\" transform=\"rotate(-5.3 51.4 167.2)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M53.9 165.1a4.4 4.4 0 0 1 8.9 0\" transform=\"rotate(-30.1 58.4 165.1)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M50.6 191.2a5.5 5.5 0 0 1 11 0\" transform=\"rotate(-8.3 56.1 191.2)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M52.1 158.7a5.3 5.3 0 0 1 10.6 0\" transform=\"rotate(-28.5 57.4 158.7)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M55.3 175a5.2 5.2 0 0 1 10.5 0\" transform=\"rotate(6.5 60.6 175)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><circle cx=\"182\" cy=\"112\" r=\"24\" fill=\"#30241D\"></circle><circle cx=\"188\" cy=\"134\" r=\"25\" fill=\"#30241D\"></circle><circle cx=\"190\" cy=\"158\" r=\"25\" fill=\"#30241D\"></circle><circle cx=\"188\" cy=\"182\" r=\"23\" fill=\"#30241D\"></circle><circle cx=\"180\" cy=\"202\" r=\"20\" fill=\"#30241D\"></circle><circle cx=\"170\" cy=\"212\" r=\"15\" fill=\"#30241D\"></circle><circle cx=\"183\" cy=\"130\" r=\"9\" fill=\"#45352B\" opacity=\"0.55\"></circle><circle cx=\"185\" cy=\"154\" r=\"9\" fill=\"#45352B\" opacity=\"0.55\"></circle><circle cx=\"183\" cy=\"178\" r=\"9\" fill=\"#45352B\" opacity=\"0.55\"></circle><circle cx=\"175\" cy=\"198\" r=\"9\" fill=\"#45352B\" opacity=\"0.55\"></circle><path d=\"M175 141.3a4.7 4.7 0 0 1 9.4 0\" transform=\"rotate(-35.2 179.7 141.3)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M185.2 191.7a4.5 4.5 0 0 1 8.9 0\" transform=\"rotate(6.8 189.6 191.7)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M172.4 166.3a5.2 5.2 0 0 1 10.4 0\" transform=\"rotate(-20.5 177.6 166.3)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M170.3 146.9a5.4 5.4 0 0 1 10.7 0\" transform=\"rotate(-17 175.6 146.9)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M187 158.3a4.4 4.4 0 0 1 8.9 0\" transform=\"rotate(-27.8 191.5 158.3)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><path d=\"M177.9 160.5a4.9 4.9 0 0 1 9.9 0\" transform=\"rotate(5.8 182.8 160.5)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.3\"></path><ellipse cx=\"120\" cy=\"128\" rx=\"64\" ry=\"60\" fill=\"#4E3C31\"></ellipse><circle cx=\"183.9\" cy=\"131.6\" r=\"17.9\" fill=\"#4E3C31\"></circle><circle cx=\"178.3\" cy=\"152.7\" r=\"19.3\" fill=\"#4E3C31\"></circle><circle cx=\"164.7\" cy=\"171\" r=\"18.6\" fill=\"#4E3C31\"></circle><circle cx=\"141.2\" cy=\"184.6\" r=\"21\" fill=\"#4E3C31\"></circle><circle cx=\"120.3\" cy=\"188\" r=\"19.6\" fill=\"#4E3C31\"></circle><circle cx=\"99.7\" cy=\"184.9\" r=\"19.8\" fill=\"#4E3C31\"></circle><circle cx=\"73.7\" cy=\"169.4\" r=\"21.2\" fill=\"#4E3C31\"></circle><circle cx=\"59.7\" cy=\"148.1\" r=\"17.7\" fill=\"#4E3C31\"></circle><circle cx=\"56\" cy=\"129.1\" r=\"19.6\" fill=\"#4E3C31\"></circle><circle cx=\"59.2\" cy=\"109.3\" r=\"18.6\" fill=\"#4E3C31\"></circle><circle cx=\"72.4\" cy=\"87.9\" r=\"16.9\" fill=\"#4E3C31\"></circle><circle cx=\"91.4\" cy=\"74.3\" r=\"20.1\" fill=\"#4E3C31\"></circle><circle cx=\"116.2\" cy=\"68.1\" r=\"17.6\" fill=\"#4E3C31\"></circle><circle cx=\"143.5\" cy=\"72.2\" r=\"20.6\" fill=\"#4E3C31\"></circle><circle cx=\"162.1\" cy=\"82.8\" r=\"18.6\" fill=\"#4E3C31\"></circle><circle cx=\"179.3\" cy=\"105.5\" r=\"20.7\" fill=\"#4E3C31\"></circle><ellipse cx=\"120\" cy=\"156\" rx=\"52\" ry=\"26\" fill=\"#372A22\" opacity=\"0.22\"></ellipse><path d=\"M108.8 85.1a4.6 4.6 0 0 1 9.1 0\" transform=\"rotate(-11.3 113.3 85.1)\" stroke=\"#7C6555\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.65\"></path><path d=\"M120.5 89a4.3 4.3 0 0 1 8.6 0\" transform=\"rotate(-21.4 124.8 89)\" stroke=\"#7C6555\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.65\"></path><path d=\"M97.3 115.9a5.2 5.2 0 0 1 10.4 0\" transform=\"rotate(-39.7 102.5 115.9)\" stroke=\"#7C6555\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.65\"></path><path d=\"M76.8 107.9a5.1 5.1 0 0 1 10.3 0\" transform=\"rotate(15.2 82 107.9)\" stroke=\"#7C6555\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.65\"></path><path d=\"M68.1 100.5a5.4 5.4 0 0 1 10.7 0\" transform=\"rotate(32 73.4 100.5)\" stroke=\"#7C6555\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.65\"></path><path d=\"M123.9 86.8a5.2 5.2 0 0 1 10.4 0\" transform=\"rotate(-8.1 129.1 86.8)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M149.2 134.4a3.7 3.7 0 0 1 7.4 0\" transform=\"rotate(-23.3 153 134.4)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M132.2 134.9a3.7 3.7 0 0 1 7.4 0\" transform=\"rotate(-27.9 135.9 134.9)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M141.5 130.2a3.7 3.7 0 0 1 7.3 0\" transform=\"rotate(9.1 145.2 130.2)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M131.2 131.7a4.3 4.3 0 0 1 8.6 0\" transform=\"rotate(-30.2 135.5 131.7)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M145.6 90.5a4.5 4.5 0 0 1 9.1 0\" transform=\"rotate(-33.1 150.2 90.5)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M140.2 129.9a4.1 4.1 0 0 1 8.3 0\" transform=\"rotate(-27.1 144.4 129.9)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M165.5 122.8a4.7 4.7 0 0 1 9.3 0\" transform=\"rotate(3.5 170.2 122.8)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M151.7 122.2a5.6 5.6 0 0 1 11.1 0\" transform=\"rotate(15.7 157.2 122.2)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><ellipse cx=\"120\" cy=\"64\" rx=\"40\" ry=\"24\" fill=\"#4E3C31\"></ellipse><circle cx=\"158.6\" cy=\"70.2\" r=\"16.2\" fill=\"#4E3C31\"></circle><circle cx=\"147.3\" cy=\"81.5\" r=\"18\" fill=\"#4E3C31\"></circle><circle cx=\"124.9\" cy=\"87.8\" r=\"18.1\" fill=\"#4E3C31\"></circle><circle cx=\"103.9\" cy=\"86\" r=\"15.6\" fill=\"#4E3C31\"></circle><circle cx=\"85\" cy=\"75.7\" r=\"19\" fill=\"#4E3C31\"></circle><circle cx=\"80.1\" cy=\"62.3\" r=\"18.2\" fill=\"#4E3C31\"></circle><circle cx=\"87.8\" cy=\"49.7\" r=\"17.9\" fill=\"#4E3C31\"></circle><circle cx=\"102.3\" cy=\"42.5\" r=\"16.9\" fill=\"#4E3C31\"></circle><circle cx=\"125.3\" cy=\"40.2\" r=\"14.7\" fill=\"#4E3C31\"></circle><circle cx=\"144.3\" cy=\"44.9\" r=\"15.9\" fill=\"#4E3C31\"></circle><circle cx=\"158.1\" cy=\"56.7\" r=\"17.7\" fill=\"#4E3C31\"></circle><path d=\"M122.2 55.8a5.9 5.9 0 0 1 11.7 0\" transform=\"rotate(36.4 128 55.8)\" stroke=\"#7C6555\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.7\"></path><path d=\"M96.9 62.2a4.5 4.5 0 0 1 8.9 0\" transform=\"rotate(-23.7 101.3 62.2)\" stroke=\"#7C6555\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.7\"></path><path d=\"M85.4 50a5.7 5.7 0 0 1 11.4 0\" transform=\"rotate(12.2 91.1 50)\" stroke=\"#7C6555\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.7\"></path><path d=\"M107.2 54.7a5.3 5.3 0 0 1 10.6 0\" transform=\"rotate(22.6 112.5 54.7)\" stroke=\"#7C6555\" stroke-width=\"2.6\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.7\"></path><path d=\"M120.1 54.9a4 4 0 0 1 7.9 0\" transform=\"rotate(-13.4 124 54.9)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M129.5 51a4.4 4.4 0 0 1 8.8 0\" transform=\"rotate(35.7 133.9 51)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M118.1 59.5a3.9 3.9 0 0 1 7.7 0\" transform=\"rotate(32.4 121.9 59.5)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M123 60.3a5.3 5.3 0 0 1 10.5 0\" transform=\"rotate(12.6 128.3 60.3)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M106.2 75.6a3.9 3.9 0 0 1 7.7 0\" transform=\"rotate(37.7 110 75.6)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><path d=\"M104.8 56.6a5.5 5.5 0 0 1 10.9 0\" transform=\"rotate(29.7 110.3 56.6)\" stroke=\"#2A1F19\" stroke-width=\"2.2\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"></path><circle cx=\"100\" cy=\"166\" r=\"27\" fill=\"#9A7C68\"></circle><circle cx=\"140\" cy=\"166\" r=\"27\" fill=\"#9A7C68\"></circle><circle cx=\"120\" cy=\"178\" r=\"24\" fill=\"#9A7C68\"></circle><circle cx=\"120\" cy=\"152\" r=\"22\" fill=\"#9A7C68\"></circle><ellipse cx=\"120\" cy=\"188\" rx=\"26\" ry=\"8\" fill=\"#836654\" opacity=\"0.8\"></ellipse><ellipse cx=\"76\" cy=\"160\" rx=\"13\" ry=\"8\" fill=\"#F2998A\" opacity=\"0.45\"></ellipse><ellipse cx=\"164\" cy=\"160\" rx=\"13\" ry=\"8\" fill=\"#F2998A\" opacity=\"0.45\"></ellipse><ellipse cx=\"92\" cy=\"130\" rx=\"13\" ry=\"15\" fill=\"#2B1A12\"></ellipse><ellipse cx=\"93\" cy=\"133\" rx=\"9\" ry=\"10\" fill=\"#5B3826\" opacity=\"0.55\"></ellipse><circle cx=\"87.5\" cy=\"124\" r=\"5.2\" fill=\"#FFFFFF\"></circle><circle cx=\"97\" cy=\"135\" r=\"2.3\" fill=\"#FFFFFF\"></circle><ellipse cx=\"148\" cy=\"130\" rx=\"13\" ry=\"15\" fill=\"#2B1A12\"></ellipse><ellipse cx=\"149\" cy=\"133\" rx=\"9\" ry=\"10\" fill=\"#5B3826\" opacity=\"0.55\"></ellipse><circle cx=\"143.5\" cy=\"124\" r=\"5.2\" fill=\"#FFFFFF\"></circle><circle cx=\"153\" cy=\"135\" r=\"2.3\" fill=\"#FFFFFF\"></circle><path d=\"M106 150q14 -9 28 0q-1 13 -14 17q-13 -4 -14 -17z\" fill=\"#2B1A12\"></path><ellipse cx=\"114\" cy=\"151\" rx=\"5.500\" ry=\"2.600\" fill=\"#8A6656\" opacity=\"0.850\"></ellipse><path d=\"M112 174q8 17 16 0z\" fill=\"#EE8577\"></path><path d=\"M120 176v6\" stroke=\"#D2675B\" stroke-width=\"2\" stroke-linecap=\"round\"></path><path d=\"M120 166v6M120 172q-8 9 -16 3M120 172q8 9 16 3\" stroke=\"#2B1A12\" stroke-width=\"3.400\" fill=\"none\" stroke-linecap=\"round\" stroke-linejoin=\"round\"></path></svg>"};

var NAMES = {dubu:'두부', kongi:'콩이'};
var FRIENDS = [{n:0,named:[]},{n:1,named:['dubu']},{n:2,named:['dubu','kongi']},{n:7,named:['dubu','kongi']}];
var GAIT_LINE = {idle:'초코도 잠깐 쉬어요', walk:'초코가 천천히 걸어요', trot:'초코가 총총 뛰어요', gallop:'초코가 신나게 달려요'};

// 화면 배치 값 (px, 세로 창 기준)
var L = {
  heroTop:88, heroStop:262, heroDrive:524, textH:112,
  friendsH:92, friendsStop:364, friendsDrive:626,
  routeTop:470, routeH:248,
  cabinTop:730, cabinH:88, voiceTop:832, voiceH:52,
  kStop:0.62, kDrive:1.15, ground:24, dashTile:44, hillTile:480, cloudTile:600,
  modeSec:0.6,
  alertHeroDrive:300, kAlertDrive:0.80, alertBottom:818, alertSec:0.45
};
// 목적지 예시 (앱에서는 NaviHelper 값으로 바꿔요)
var TRIPS = {
  gangjeol: {dest:'간절곶', remain:'32분', arrive:'오후 2:42 도착'},
  seoul: {dest:'서울숲', remain:'1시간 52분', arrive:'오후 4:38 도착'}
};
var ALERT_VOICE = {
  rest10: ['“응, 들렀다 가자”', '“다음에”'],
  rest5: ['“지금 들르자”', '“다음에”'],
  highway: ['“휴게소 들르자”', '“다음에”']
};

function friendCopy(sc){
  var nm = sc.named.map(function(k){return NAMES[k];});
  if (sc.n===0) return {long:'간절곶으로 가는 친구는 아직 없어요', sub:'친구가 출발하면 여기에 알려 드려요'};
  if (sc.n===1 && nm.length===1) return {long:'간절곶으로 '+nm[0]+'가 가고 있어요', sub:'도착하면 인사 나눠 보세요'};
  if (sc.n===2 && nm.length===2) return {long:'간절곶으로 '+nm[0]+'와 '+nm[1]+'가 가고 있어요', sub:'도착하면 인사 나눠 보세요'};
  return {long:'간절곶으로 강아지 '+sc.n+'마리가 가고 있어요', sub:nm.join(', ')+' 외 '+(sc.n-nm.length)+'마리. 이름은 공개한 친구만 보여요'};
}
function faceStack(sc, size, ring, ink, bg){
  if (!sc.n) return '<span style="width:'+size+'px;height:'+size+'px;border-radius:50%;border:2px dashed '+ring+';flex:none;display:inline-block;box-sizing:border-box"></span>';
  var s='<span style="display:inline-flex;align-items:center;flex:none">';
  sc.named.forEach(function(k,i){s+='<span style="width:'+size+'px;height:'+size+'px;margin-left:'+(i?-Math.round(size*.28):0)+'px;display:inline-flex;align-items:center;justify-content:center">'+HEADS[k]+'</span>';});
  var rest=sc.n-sc.named.length;
  if (rest>0) s+='<span style="width:'+size+'px;height:'+size+'px;box-sizing:border-box;margin-left:'+(-Math.round(size*.2))+'px;border-radius:50%;background:'+ink+';color:'+bg+';border:2px solid '+bg+';display:inline-flex;align-items:center;justify-content:center;font-size:'+Math.round(size*.38)+'px;font-weight:800">+'+rest+'</span>';
  return s+'</span>';
}
function pal(night){
  return night ? {bg:'#15120F', panel:'#231E1A', tint:'#3A2D23', ink:'#F4EDE4', mut:'#B8AB9D', line:'#3A322B', ground:'#3A322B', dash:'#5A4B3E', hill:'#332A23', cloud:'#2E2620', chip:'#231E1A', bar:'#3A322B', fur:'#C98B5B', ring:'#5A4B3E', line2:'#4A3F35', alr:'#FF8E74', alrt:'#43201A'}
               : {bg:'#F6F1EA', panel:'#FFFFFF', tint:'#F3E3D0', ink:'#2A201A', mut:'#6F6257', line:'#E9DFD2', ground:'#E9DCC9', dash:'#C9B59C', hill:'#EAD6BD', cloud:'#FBF3E8', chip:'#FFFFFF', bar:'#F6F1EA', fur:'#C98B5B', ring:'#D9CBB9', line2:'#D9CBB9', alr:'#B8361F', alrt:'#FBE3DC'};
}
function ic(name, c, sz){
  sz=sz||22; var s='<svg viewBox="0 0 24 24" width="'+sz+'" height="'+sz+'" aria-hidden="true" fill="none" stroke="'+c+'" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" style="display:block;flex:none">';
  if(name==='paw') s='<svg viewBox="0 0 24 24" width="'+sz+'" height="'+sz+'" aria-hidden="true" style="display:block;flex:none"><g fill="'+c+'"><ellipse cx="5.6" cy="10.4" rx="1.9" ry="2.5" transform="rotate(-18 5.6 10.4)"></ellipse><ellipse cx="9.6" cy="6.4" rx="2" ry="2.7" transform="rotate(-6 9.6 6.4)"></ellipse><ellipse cx="14.4" cy="6.4" rx="2" ry="2.7" transform="rotate(6 14.4 6.4)"></ellipse><ellipse cx="18.4" cy="10.4" rx="1.9" ry="2.5" transform="rotate(18 18.4 10.4)"></ellipse><path d="M12 11.3c-2.7 0-5.2 2.7-5.2 5.4 0 1.9 1.4 2.8 3 2.8 0.9 0 1.4-0.5 2.2-0.5s1.3 0.5 2.2 0.5c1.6 0 3-0.9 3-2.8 0-2.7-2.5-5.4-5.2-5.4z"></path></g>';
  if(name==='pin') s+='<path d="M12 21s-6.5-6.2-6.5-11.2a6.5 6.5 0 0 1 13 0C18.5 14.8 12 21 12 21z"></path><circle cx="12" cy="9.8" r="2.2"></circle>';
  if(name==='mic') s+='<rect x="9" y="3" width="6" height="11" rx="3"></rect><path d="M5.5 11.5a6.5 6.5 0 0 0 13 0M12 18v3"></path>';
  if(name==='temp') s+='<path d="M10 14.3V5.5a2 2 0 0 1 4 0v8.8a4 4 0 1 1-4 0z"></path><path d="M12 10v6"></path>';
  if(name==='win') s+='<path d="M4 19v-7.2c0-0.5 0.2-1 0.5-1.4l4.2-5c0.4-0.5 1-0.7 1.6-0.7H18a2 2 0 0 1 2 2V19z"></path><path d="M4 12.5h16"></path>';
  if(name==='route') s+='<circle cx="6" cy="18" r="2.3"></circle><circle cx="18" cy="6" r="2.3"></circle><path d="M8.3 18H15a3 3 0 0 0 0-6H9a3 3 0 0 1 0-6h6.7"></path>';
  if(name==='clock') s+='<circle cx="12" cy="12" r="8.5"></circle><path d="M12 7.5V12l3 2"></path>';
  if(name==='lock') s+='<rect x="5.5" y="10.5" width="13" height="9.5" rx="1.5"></rect><path d="M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5"></path>';
  return s+'</svg>';
}

function windowHTML(p, st){
  var c = pal(st.night), sc = FRIENDS[st.fr], fc = friendCopy(sc), tr = TRIPS[st.trip] || TRIPS.gangjeol;
  if (sc.n) fc.long = fc.long.replace('간절곶으로', tr.dest+'으로');
  var F = 'font-family:Poppins,\'Noto Sans KR\',\'Noto Sans CJK KR\',system-ui,sans-serif;';
  var s = '<div id="'+p+'-root" style="position:relative;width:640px;height:906px;box-sizing:border-box;background:'+c.bg+';color:'+c.ink+';'+F+'overflow:hidden;line-height:1.35;word-break:keep-all">';
  // 상단
  s += '<div style="position:absolute;left:20px;right:20px;top:20px;height:56px;display:flex;align-items:center;justify-content:space-between">';
  s += '<div style="height:56px;box-sizing:border-box;padding:0 22px 0 8px;background:'+c.panel+';border-radius:28px;display:flex;align-items:center;gap:12px;font-size:19px;font-weight:700;white-space:nowrap"><span style="width:40px;height:40px;border-radius:50%;background:'+c.ink+';color:'+c.panel+';font-size:21px;font-weight:700;display:flex;align-items:center;justify-content:center;flex:none">D</span><span id="'+p+'-status"></span></div>';
  s += '<div style="display:flex;align-items:center;gap:8px;font-size:21px;font-weight:800;padding-right:8px">'+ic('paw',c.fur,24)+'개랑나랑</div></div>';
  // 남은 시간 + 달리는 초코
  s += '<section id="'+p+'-hero" aria-label="남은 시간과 달리는 초코" style="position:absolute;left:20px;right:20px;top:'+L.heroTop+'px;height:'+L.heroDrive+'px;background:'+c.tint+';border-radius:36px;overflow:hidden">';
  s += '<div style="position:absolute;left:26px;top:20px"><div style="font-size:18px;font-weight:700;color:'+c.mut+'">'+tr.dest+'까지</div><div style="display:flex;align-items:baseline;gap:12px;white-space:nowrap"><span style="font-size:'+(tr.remain.length>4?50:64)+'px;font-weight:700;letter-spacing:-0.04em;line-height:1.05;display:inline-block;min-height:67px">'+tr.remain+'</span><span style="font-size:18px;font-weight:700;color:'+c.mut+'">'+tr.arrive+'</span></div></div>';
  s += '<div id="'+p+'-gline" aria-live="polite" style="position:absolute;right:20px;top:22px;background:'+c.chip+';border-radius:20px;height:40px;padding:0 16px;display:flex;align-items:center;font-size:17px;font-weight:800;white-space:nowrap"></div>';
  s += '<svg id="'+p+'-strip" style="position:absolute;left:0;bottom:0;display:block" width="600" height="400" viewBox="0 0 600 400" role="img" aria-label="차량 속도에 맞춰 달리는 초코"><g id="'+p+'-clouds"></g><g id="'+p+'-hillsO"><g id="'+p+'-hills"></g></g><rect id="'+p+'-groundR" x="0" width="600" height="'+L.ground+'" fill="'+c.ground+'"></rect><g id="'+p+'-dash"></g><g id="'+p+'-rigwrap"><g id="'+p+'-rig"></g></g></svg>';
  s += '</section>';
  // 오늘의 산책길 (정차 때만)
  s += '<section id="'+p+'-route" aria-label="오늘의 산책길" style="position:absolute;left:20px;right:20px;top:'+L.routeTop+'px;height:'+L.routeH+'px;box-sizing:border-box;background:'+c.panel+';border-radius:28px;padding:16px 22px">';
  s += '<div style="font-size:19px;font-weight:800">오늘의 산책길</div>';
  var rows = [
    {icon:'<span style="width:36px;height:36px;display:inline-flex">'+HEADS.choco+'</span>', bg:c.tint, t:'지금 48분째 달리는 중', d:'초코는 편안해요'},
    {icon:ic('paw',c.panel,18), bg:c.ink, t:'카페이소에서 쉬어가기', d:'12분 뒤, 경로에서 3분 더'},
    {icon:ic('pin',c.ink,18), bg:c.panel, border:true, t:'간절곶 도착', d:'오후 2:42'}
  ];
  s += '<div style="position:relative;margin-top:6px">';
  s += '<div style="position:absolute;left:17px;top:22px;height:88px;border-left:2px dashed '+c.line+'"></div>';
  rows.forEach(function(r){
    s += '<div style="position:relative;display:flex;align-items:center;gap:14px;height:44px"><span style="width:36px;height:36px;box-sizing:border-box;border-radius:50%;background:'+r.bg+';'+(r.border?'border:2px solid '+c.ink+';':'')+'display:flex;align-items:center;justify-content:center;flex:none;overflow:hidden">'+r.icon+'</span><div style="min-width:0;display:flex;align-items:baseline;gap:10px;white-space:nowrap"><span style="font-size:19px;font-weight:800">'+r.t+'</span><span style="font-size:17px;color:'+c.mut+'">'+r.d+'</span></div></div>';
  });
  s += '</div>';
  s += '<div style="display:flex;justify-content:space-between;align-items:baseline;margin-top:8px"><span style="font-size:17px;color:'+c.mut+'">쉬어가기까지 (60분마다)</span><b style="font-size:21px;font-weight:800">12분</b></div>';
  s += '<div style="margin-top:6px;height:12px;border-radius:6px;background:'+c.bar+'"><div style="width:80%;height:12px;border-radius:6px;background:'+c.fur+'"></div></div>';
  s += '</section>';
  // 같은 곳으로 가는 강아지
  s += '<section id="'+p+'-friends" aria-live="polite" aria-label="같은 목적지로 가는 강아지" style="position:absolute;left:20px;right:20px;top:'+L.friendsDrive+'px;height:'+L.friendsH+'px;box-sizing:border-box;background:'+c.panel+';border-radius:28px;display:flex;align-items:center;gap:14px;padding:0 20px 0 16px">'+faceStack(sc,52,c.ring,c.ink,c.panel)+'<div style="min-width:0"><div style="font-size:20px;font-weight:800;white-space:nowrap">'+fc.long+'</div><div style="font-size:16px;color:'+c.mut+';margin-top:2px;white-space:nowrap">'+fc.sub+'</div></div></section>';
  // 초코 자리 상태
  var tiles = [['temp','초코 자리','22° 딱 좋아요'],['win','뒷창문','닫힘'],['lock','뒷문','잠김']];
  s += '<div id="'+p+'-cabin" style="position:absolute;left:20px;right:20px;top:'+L.cabinTop+'px;height:'+L.cabinH+'px;display:grid;grid-template-columns:1.3fr 1fr 1fr;gap:10px">';
  tiles.forEach(function(t){ s += '<div style="background:'+c.panel+';border-radius:24px;display:flex;align-items:center;gap:10px;padding:0 14px;min-width:0"><span style="width:42px;height:42px;border-radius:50%;background:'+c.tint+';display:flex;align-items:center;justify-content:center;flex:none">'+ic(t[0],c.ink,22)+'</span><div style="min-width:0"><div style="font-size:16px;color:'+c.mut+'">'+t[1]+'</div><div style="font-size:19px;font-weight:800;white-space:nowrap">'+t[2]+'</div></div></div>'; });
  s += '</div>';
  // 말로 해 보세요
  s += '<footer style="position:absolute;left:20px;right:20px;top:'+L.voiceTop+'px;height:'+(L.voiceH+2)+'px;box-sizing:border-box;background:'+c.panel+';border-radius:28px;display:flex;align-items:center;gap:10px;padding:0 10px 0 20px;white-space:nowrap">'+ic('mic',c.ink,22)+'<span style="font-size:18px;font-weight:800">'+(st.alert?'대답해 주세요':'말로 해 보세요')+'</span>'+(st.alert?ALERT_VOICE[st.alert]:['“쉬어갈 곳 찾아줘”','“초코 자리 시원하게”']).map(function(v,i){return '<span style="'+(i?'':'margin-left:auto;')+'background:'+c.bg+';border-radius:18px;padding:6px 14px;font-size:17px;color:'+c.mut+'">'+v+'</span>';}).join('')+'</footer>';
  if (st.alert) s += alertHTML(p, st, c);
  s += '</div>';
  return s;
}


// ---------- 알림 카드 (쉬는 시간 10분 전 / 5분 전 / 고속도로 휴게소) ----------
function chipH(t, bg, col){ return '<span style="background:'+bg+';color:'+col+';border-radius:18px;padding:5px 14px;font-size:17px;font-weight:700;white-space:nowrap">'+t+'</span>'; }
function btnRow(c, label, icon, primaryBg, primaryCol){
  return '<div style="display:flex;gap:10px;flex:none"><button type="button" style="flex:1;height:78px;border:none;border-radius:39px;background:'+primaryBg+';color:'+primaryCol+';font-family:inherit;font-size:22px;font-weight:800;display:flex;align-items:center;justify-content:center;gap:10px">'+(icon?ic(icon,primaryCol,28):'')+'<span>'+label+'</span></button><button type="button" style="width:150px;height:78px;flex:none;box-sizing:border-box;border:2px solid '+c.line+';border-radius:39px;background:'+c.panel+';color:'+c.ink+';font-family:inherit;font-size:19px;font-weight:700">다음에</button></div>';
}
function pawG(x, y, s, fill){
  return '<g transform="translate('+(x-12*s)+' '+(y-12*s)+') scale('+s+')" fill="'+fill+'"><ellipse cx="5.6" cy="10.4" rx="1.9" ry="2.5" transform="rotate(-18 5.6 10.4)"></ellipse><ellipse cx="9.6" cy="6.4" rx="2" ry="2.7" transform="rotate(-6 9.6 6.4)"></ellipse><ellipse cx="14.4" cy="6.4" rx="2" ry="2.7" transform="rotate(6 14.4 6.4)"></ellipse><ellipse cx="18.4" cy="10.4" rx="1.9" ry="2.5" transform="rotate(18 18.4 10.4)"></ellipse><path d="M12 11.3c-2.7 0-5.2 2.7-5.2 5.4 0 1.9 1.4 2.8 3 2.8 0.9 0 1.4-0.5 2.2-0.5s1.3 0.5 2.2 0.5c1.6 0 3-0.9 3-2.8 0-2.7-2.5-5.4-5.2-5.4z"></path></g>';
}
// 고속도로 타임라인: 지금 → 죽암휴게소(6분) → 쉬는 시간(12분) → 천안삼거리휴게소(30분)
function highwayTimeline(c, W, H){
  var x0=26, x1=W-26, y=50, per=(x1-x0)/32, X=function(m){return x0+m*per;};
  var F='font-family="Poppins, Noto Sans KR, sans-serif"';
  var s='<svg viewBox="0 0 '+W+' '+H+'" width="'+W+'" height="'+H+'" role="img" aria-label="지금 위치에서 6분 뒤 죽암휴게소, 12분 뒤 쉬는 시간, 30분 뒤 천안삼거리휴게소" style="display:block;flex:none">';
  s+='<rect x="'+x0+'" y="'+(y-4)+'" width="'+(x1-x0)+'" height="8" rx="4" fill="'+c.line2+'"></rect>';
  s+='<rect x="'+x0+'" y="'+(y-4)+'" width="'+(X(6)-x0)+'" height="8" rx="4" fill="'+c.fur+'"></rect>';
  s+='<path d="M'+X(12)+' '+(y-24)+'V'+(y+14)+'" stroke="'+c.fur+'" stroke-width="3" stroke-dasharray="4 5" stroke-linecap="round"></path>';
  s+='<text x="'+X(12)+'" y="'+(y-32)+'" text-anchor="middle" '+F+' font-size="15" font-weight="700" fill="'+c.ink+'">쉬는 시간 12분</text>';
  s+='<circle cx="'+X(30)+'" cy="'+y+'" r="13" fill="'+c.panel+'" stroke="'+c.line2+'" stroke-width="3"></circle>'+pawG(X(30), y, 0.62, c.line2);
  s+='<text x="'+X(30)+'" y="'+(y+38)+'" text-anchor="middle" '+F+' font-size="15" font-weight="700" fill="'+c.mut+'">천안삼거리</text><text x="'+X(30)+'" y="'+(y+57)+'" text-anchor="middle" '+F+' font-size="14" fill="'+c.mut+'">30분</text>';
  s+='<circle cx="'+X(6)+'" cy="'+y+'" r="20" fill="'+c.fur+'" stroke="'+c.panel+'" stroke-width="4"></circle>'+pawG(X(6), y, 0.95, '#FFFFFF');
  s+='<text x="'+X(6)+'" y="'+(y+42)+'" text-anchor="middle" '+F+' font-size="16" font-weight="800" fill="'+c.ink+'">죽암휴게소</text><text x="'+X(6)+'" y="'+(y+60)+'" text-anchor="middle" '+F+' font-size="14" fill="'+c.mut+'">6분, 9km</text>';
  s+='<circle cx="'+x0+'" cy="'+y+'" r="13" fill="'+c.ink+'"></circle><path d="M'+(x0-5)+' '+(y-6)+'L'+(x0+7)+' '+y+'L'+(x0-5)+' '+(y+6)+'z" fill="'+c.panel+'"></path>';
  s+='<text x="'+x0+'" y="'+(y+38)+'" text-anchor="middle" '+F+' font-size="14" fill="'+c.mut+'">지금</text>';
  return s+'</svg>';
}
function alertHTML(p, st, c){
  var a=st.alert, s, base='position:absolute;left:20px;right:20px;top:400px;height:418px;box-sizing:border-box;border-radius:32px;display:flex;flex-direction:column;opacity:0;visibility:hidden;';
  if (a==='rest10'){
    s='<section id="'+p+'-alert" role="alert" aria-label="쉬는 시간 10분 전 알림" style="'+base+'background:'+c.panel+';padding:20px 22px 18px">';
    s+='<div style="display:flex;gap:8px">'+chipH('쉬는 시간 알림',c.tint,c.ink)+chipH('토이 푸들 60분마다',c.bg,c.mut)+'</div>';
    s+='<div style="margin-top:10px;font-size:32px;font-weight:800;letter-spacing:-0.03em;line-height:1.15">10분 뒤 초코 쉬는 시간이에요</div>';
    s+='<div style="margin-top:14px;display:flex;justify-content:space-between;align-items:baseline"><span style="font-size:17px;color:'+c.mut+'">지금 50분째 달리는 중</span><b style="font-size:20px;font-weight:800">50 / 60분</b></div>';
    s+='<div style="margin-top:6px;height:12px;border-radius:6px;background:'+c.bar+'"><div style="width:83%;height:12px;border-radius:6px;background:'+c.fur+'"></div></div>';
    s+='<div style="margin-top:14px;background:'+c.bg+';border-radius:22px;padding:12px 14px;display:flex;align-items:center;gap:12px"><span style="width:46px;height:46px;border-radius:50%;background:'+c.tint+';display:flex;align-items:center;justify-content:center;flex:none">'+ic('pin',c.ink,24)+'</span><div style="flex:1;min-width:0"><div style="display:flex;align-items:center;gap:8px"><span style="font-size:20px;font-weight:800;white-space:nowrap">카페이소</span><span style="background:'+c.tint+';border-radius:14px;padding:2px 10px;font-size:15px;font-weight:700;white-space:nowrap">가는 길</span></div><div style="font-size:16px;color:'+c.mut+';white-space:nowrap">8분 뒤 도착, 경로에서 3분 더</div></div>'+faceStack({n:2,named:['dubu','kongi']},34,c.ring,c.ink,c.bg)+'</div>';
    s+='<div style="flex:1"></div>'+btnRow(c,'경유지로 추가','route',c.ink,c.panel)+'</section>';
  } else if (a==='rest5'){
    s='<section id="'+p+'-alert" role="alert" aria-label="쉬는 시간 5분 전 알림" style="'+base+'background:'+c.alrt+';padding:20px 22px 18px">';
    s+='<div style="display:flex;gap:8px">'+chipH('쉬는 시간 5분 전',c.panel,c.alr)+'</div>';
    s+='<div style="margin-top:10px;font-size:32px;font-weight:800;letter-spacing:-0.03em;line-height:1.15;color:'+c.alr+'">5분 뒤에는 꼭 쉬어 가요</div>';
    s+='<div style="margin-top:8px;font-size:18px;line-height:1.45">초코가 55분째 차에 있어요. 다리를 펴 줄 때예요.</div>';
    s+='<div style="margin-top:12px;height:12px;border-radius:6px;background:'+c.panel+'"><div style="width:92%;height:12px;border-radius:6px;background:'+c.alr+'"></div></div>';
    s+='<div style="margin-top:14px;background:'+c.panel+';border-radius:22px;padding:12px 14px;display:flex;align-items:center;gap:12px"><span style="width:46px;height:46px;border-radius:50%;background:'+c.alrt+';display:flex;align-items:center;justify-content:center;flex:none">'+ic('pin',c.alr,24)+'</span><div style="flex:1;min-width:0"><div style="display:flex;align-items:center;gap:8px"><span style="font-size:20px;font-weight:800;white-space:nowrap">간절바당</span><span style="background:'+c.alrt+';color:'+c.alr+';border-radius:14px;padding:2px 10px;font-size:15px;font-weight:700;white-space:nowrap">가장 가까워요</span></div><div style="font-size:16px;color:'+c.mut+';white-space:nowrap">4분 뒤 도착, 경로에서 1분 더</div></div></div>';
    s+='<div style="flex:1"></div>'+btnRow(c,'지금 들르기',null,c.alr,c.panel)+'</section>';
  } else {
    s='<section id="'+p+'-alert" role="alert" aria-label="고속도로 휴게소 알림" style="'+base+'background:'+c.panel+';padding:18px 22px 18px">';
    s+='<div style="display:flex;gap:8px">'+chipH('고속도로 · 서울 방향',c.ink,c.panel)+chipH('쉬는 시간까지 12분',c.tint,c.ink)+'</div>';
    s+='<div style="margin-top:10px;font-size:30px;font-weight:800;letter-spacing:-0.03em;line-height:1.15">다음 휴게소에서 쉬어 가세요</div>';
    s+='<div style="margin-top:6px">'+highwayTimeline(c,556,112)+'</div>';
    s+='<div style="display:flex;align-items:center;gap:10px;height:40px"><span style="width:34px;height:34px;border-radius:50%;background:'+c.tint+';display:flex;align-items:center;justify-content:center;flex:none">'+ic('paw',c.ink,20)+'</span><span style="font-size:17px;font-weight:700;white-space:nowrap">죽암휴게소에 반려견 놀이터가 있어요</span></div>';
    s+='<div style="display:flex;align-items:center;gap:10px;height:40px"><span style="width:34px;height:34px;border-radius:50%;background:'+c.bg+';display:flex;align-items:center;justify-content:center;flex:none">'+ic('clock',c.ink,20)+'</span><span style="font-size:17px;font-weight:700;white-space:nowrap;color:'+c.mut+'">다음 놀이터는 30분 뒤라 쉬는 시간이 지나요</span></div>';
    s+='<div style="flex:1"></div>'+btnRow(c,'휴게소 들르기','route',c.ink,c.panel)+'</section>';
  }
  return s;
}

function mixv(a,b,t){return a+(b-a)*t;}
function ease(t){return t<.5 ? 2*t*t : 1-Math.pow(-2*t+2,2)/2;}

function Instance(host, p, st){
  this.host=host; this.p=p; this.st=st; this.lastD=-1; this.lastGline=''; this.lastStatus='';
  host.innerHTML = windowHTML(p, st);
  var c = pal(st.night), q=function(id){return host.querySelector('#'+p+'-'+id);};
  this.el = {hero:q('hero'), route:q('route'), friends:q('friends'), strip:q('strip'), groundR:q('groundR'), dash:q('dash'), hills:q('hills'), hillsO:q('hillsO'), clouds:q('clouds'), rigwrap:q('rigwrap'), gline:q('gline'), status:q('status'), cabin:q('cabin'), alert:q('alert')};
  var dash='', hills='', clouds='';
  for (var i=-1;i<Math.ceil(600/L.dashTile)+2;i++) dash+='<rect x="'+(i*L.dashTile)+'" y="0" width="22" height="5" rx="2.5" fill="'+c.dash+'"></rect>';
  for (i=-1;i<4;i++){var x=i*L.hillTile; hills+='<g transform="translate('+x+' 0)"><circle cx="90" cy="-24" r="42" fill="'+c.hill+'"></circle><circle cx="132" cy="-14" r="29" fill="'+c.hill+'"></circle><rect x="104" y="-20" width="9" height="20" rx="4.5" fill="'+c.hill+'"></rect><circle cx="350" cy="-10" r="22" fill="'+c.hill+'"></circle><rect x="346" y="-12" width="7" height="12" rx="3.5" fill="'+c.hill+'"></rect></g>';}
  for (i=-1;i<3;i++){var cx=i*L.cloudTile; clouds+='<g transform="translate('+cx+' 0)"><ellipse cx="120" cy="0" rx="46" ry="16" fill="'+c.cloud+'"></ellipse><ellipse cx="150" cy="-10" rx="30" ry="16" fill="'+c.cloud+'"></ellipse><ellipse cx="420" cy="40" rx="36" ry="12" fill="'+c.cloud+'"></ellipse><ellipse cx="444" cy="32" rx="22" ry="12" fill="'+c.cloud+'"></ellipse></g>';}
  this.el.dash.innerHTML=dash; this.el.hills.innerHTML=hills; this.el.clouds.innerHTML=clouds;
  this.nodes = buildRig(this.el.rigwrap.querySelector('#'+p+'-rig'), 1, 0, 0);
}
Instance.prototype.layout = function(d, a){
  a = a || 0;
  if (Math.abs(d-this.lastD) < 0.0005 && Math.abs(a-(this.lastA||0)) < 0.0005) return; this.lastD=d; this.lastA=a;
  var e=this.el;
  var heroH=mixv(mixv(L.heroStop,L.heroDrive,d), mixv(L.heroStop,L.alertHeroDrive,d), a);
  var k=mixv(mixv(L.kStop,L.kDrive,d), mixv(L.kStop,L.kAlertDrive,d), a);
  var Hs=heroH-L.textH, gy=Hs-L.ground;
  e.hero.style.height=f(heroH)+'px';
  e.strip.setAttribute('height', f(Hs)); e.strip.setAttribute('viewBox','0 0 600 '+f(Hs));
  e.groundR.setAttribute('y', f(gy));
  e.hillsO.setAttribute('transform','translate(0 '+f(gy)+') scale('+k.toFixed(4)+')');
  this.k=k; this.gy=gy;
  e.rigwrap.setAttribute('transform','translate('+f(280-168*k)+' '+f(gy-190*k)+') scale('+k.toFixed(4)+')');
  e.friends.style.top=f(mixv(L.friendsStop,L.friendsDrive,d))+'px';
  e.friends.style.opacity=(1-a).toFixed(3); e.friends.style.visibility = a>0.995 ? 'hidden' : 'visible';
  e.cabin.style.opacity=(1-a).toFixed(3); e.cabin.style.visibility = a>0.995 ? 'hidden' : 'visible';
  var ro=(1-d)*(1-a);
  e.route.style.opacity=ro.toFixed(3);
  e.route.style.transform='translateY('+f(d*28)+'px)';
  e.route.style.visibility = ro<0.005 ? 'hidden' : 'visible';
  e.route.setAttribute('aria-hidden', ro<0.5 ? 'true' : 'false');
  if (e.alert){
    var top=L.heroTop+heroH+12;
    e.alert.style.top=f(top)+'px'; e.alert.style.height=f(L.alertBottom-top)+'px';
    e.alert.style.opacity=a.toFixed(3); e.alert.style.transform='translateY('+f((1-a)*24)+'px)';
    e.alert.style.visibility = a<0.005 ? 'hidden' : 'visible';
  }
};
Instance.prototype.draw = function(P, tail, tongue, dashOff, hillOff, cloudOff, gline, status){
  apply(this.nodes,P,0,tail,tongue);
  var e=this.el;
  e.dash.setAttribute('transform','translate('+f(-dashOff)+' '+f(this.gy+10)+')');
  e.hills.setAttribute('transform','translate('+f(-hillOff)+' 0)');
  e.clouds.setAttribute('transform','translate('+f(-cloudOff)+' '+f(this.gy-300*this.k)+')');
  if (gline!==this.lastGline){ this.lastGline=gline; e.gline.textContent=gline; }
  if (status!==this.lastStatus){ this.lastStatus=status; e.status.textContent=status; }
};

function controller(opts){
  opts=opts||{};
  var st = {night:!!opts.night, safe:!!opts.safe, fr:(opts.fr==null?3:opts.fr), alert:opts.alert||null, trip:opts.trip||(opts.alert==='highway'?'seoul':'gangjeol')};
  var alertOn = !!st.alert, aLin = 0;
  var mounts=[], insts=[];
  var target=0, smooth=0, phase=0, gait='idle', prevGait='idle', blend=1, stillFor=10, clock=0;
  var dLin=0, dashOff=0, hillOff=0, cloudOff=0, onTick=null, running=false, last=0;
  function remount(){ insts = mounts.map(function(m){ return new Instance(m.host, m.p, st); }); insts.forEach(function(i){i.layout(ease(dLin), ease(aLin));}); step(0); }
  function step(dt){
    clock+=dt;
    smooth += (target - smooth) * (1 - Math.exp(-dt/0.25));
    if (Math.abs(smooth-target)<.05) smooth=target;
    stillFor = smooth < 1 ? stillFor + dt : 0;
    var ng = nextGait(gait, smooth, stillFor);
    if (ng!==gait){ prevGait=gait; gait=ng; blend=0; }
    blend=Math.min(1, blend+dt/0.35);
    var drive = gait!=='idle' ? 1 : 0;
    dLin = drive ? Math.min(1, dLin+dt/L.modeSec) : Math.max(0, dLin-dt/L.modeSec);
    var d = ease(dLin);
    aLin = alertOn ? Math.min(1, aLin+dt/L.alertSec) : Math.max(0, aLin-dt/L.alertSec);
    var a = ease(aLin);
    if (!alertOn && st.alert && aLin===0){ st.alert=null; remount(); return; }
    var G=GAITS[gait], groundSpeed=smooth*K, freq=G.L?groundSpeed/G.L:0;
    var frozen = st.safe && smooth >= 1;
    var k = mixv(L.kStop,L.kDrive,d);
    if (!frozen){
      phase=(phase+freq*dt)%1;
      dashOff=(dashOff+groundSpeed*k*dt)%L.dashTile;
      hillOff=(hillOff+groundSpeed*0.3*dt)%L.hillTile;
      cloudOff=(cloudOff+groundSpeed*k*0.08*dt)%L.cloudTile;
    }
    var ph = frozen ? 0.12 : phase;
    var A=pose(gait,ph,smooth), B=pose(prevGait,ph,smooth), P={legs:{}};
    LEGS.forEach(function(l){P.legs[l.id]=[mix(B.legs[l.id][0],A.legs[l.id][0],blend), mix(B.legs[l.id][1],A.legs[l.id][1],blend)];});
    ['bob','pitch','ear','head'].forEach(function(key){P[key]=mix(B[key],A[key],blend);});
    if (gait==='idle' && !frozen){ P.bob = 0.8*Math.sin(clock*2.4); }
    var tail = frozen ? 10 : (gait==='idle' ? 16*Math.sin(clock*2*Math.PI*2.2) : (gait==='gallop'? -18 + 6*Math.sin(2*Math.PI*phase) : 10*Math.sin(2*Math.PI*phase*2)));
    var tongue = smooth>=40;
    var status = alertOn ? '주행 중, 말로 대답해 주세요' : (drive ? '주행 중, 말로 조작해요' : '잠깐 멈췄어요');
    insts.forEach(function(i){ i.layout(d, a); i.draw(P,tail,tongue,dashOff,hillOff,cloudOff,GAIT_LINE[gait],status); });
    if (onTick) onTick({speed:smooth, gait:gait, gaitName:G.name, groundSpeed:groundSpeed, stride:G.L, freq:freq, frozen:frozen, mode:d});
  }
  function loop(now){ if(!running) return; var dt=Math.min(.05,(now-last)/1000); last=now; step(dt); requestAnimationFrame(loop); }
  return {
    state: st,
    mount: function(host, p){ mounts.push({host:host, p:p||('w'+mounts.length)}); remount(); },
    setSpeed: function(v){ target=Math.max(0,Math.min(200,+v||0)); },
    jumpTo: function(v, ph){ // 테스트용: 전환 없이 바로 그 속도와 상태로
      target=smooth=Math.max(0,+v||0);
      if (target<1){ stillFor=10; gait=prevGait='idle'; dLin=0; } else { stillFor=0; gait=prevGait=nextGait('walk',target,0); dLin=1; }
      blend=1; if (ph!=null) phase=ph; aLin = alertOn ? 1 : 0; insts.forEach(function(i){i.lastD=-1;}); step(0);
    },
    setAlert: function(type, trip){ // 'rest10' | 'rest5' | 'highway' | null
      if (type){ st.alert=type; alertOn=true; st.trip = trip || (type==='highway' ? 'seoul' : st.trip); remount(); }
      else { alertOn=false; }
    },
    setTrip: function(key){ st.trip=key; remount(); },
    setNight: function(b){ st.night=!!b; remount(); },
    setFriends: function(i){ st.fr=i; remount(); },
    setSafe: function(b){ st.safe=!!b; },
    onTick: function(fn){ onTick=fn; },
    step: step,
    start: function(){ if(running) return; running=true; last=performance.now(); requestAnimationFrame(loop); },
    stop: function(){ running=false; },
    constants: {K:K, GAITS:GAITS, L:L}
  };
}
return {controller:controller, FRIENDS:FRIENDS, L:L};
})();
