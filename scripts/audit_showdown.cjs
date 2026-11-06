// Runs the real simulator bundled in Cobblemon 1.8.3; not a substitute for clients.
const assert = require('node:assert/strict');
const path = require('node:path');
const {Battle} = require(path.join(process.argv[2], 'sim/battle.js'));
const {BattleStream} = require(path.join(process.argv[2], 'sim/battle-stream.js'));
const {Dex} = require(path.join(process.argv[2], 'sim/dex.js'));
let passed = 0;
function test(name, body) { body(); passed++; console.log(`PASS ${name}`); }
function team(species, uuid, move) {
  const pp = Dex.moves.get(move).pp;
  return [{species, uuid, moves:[move], movesInfo:[{pp, maxPp:pp}], level:100}];
}
function single() {
  const battle = new Battle({formatid:'gen9customgame', seed:[1,2,3,4]});
  battle.setPlayer('p1',{name:'A', team:team('Garchomp','attacker','dragonclaw')});
  battle.setPlayer('p2',{name:'Boss', team:team('Rayquaza','same-boss-uuid','splash')});
  if (battle.requestState === 'teampreview') battle.makeChoices('team 1','team 1');
  return battle;
}
test('MULTI espera quatro participantes e organiza duas duplas', () => {
  const b = new Battle({format:{id:'auditmulti',name:'Audit',mod:'gen9',gameType:'multi',gen:9,ruleset:[]},seed:[1,2,3,4]});
  assert.equal(b.sides.length,4);
  for(let i=1;i<=3;i++) b.setPlayer(`p${i}`,{name:`P${i}`,team:team('Rayquaza',`u${i}`,'splash')});
  assert.equal(b.started,false);
  b.setPlayer('p4',{name:'P4',team:team('Rayquaza','u4','splash')});
  assert.equal(b.started,true);
  assert.deepEqual(b.sides.map(s=>s.allySide.id),['p3','p4','p1','p2']);
});
test('Uma vaga ocupada não aceita outra equipe após o início', () => {
  const b = single();
  assert.throws(()=>b.setPlayer('p1',{name:'Novo',team:team('Mewtwo','new','psychic')}),/already has a team/);
});
test('O protocolo não aceita ações de um quinto jogador', () => {
  const stream = new BattleStream();
  assert.throws(()=>stream._writeLine('p5','move 1'),/Unrecognized command/);
});
test('O protocolo não oferece um comando tipado sethp', () => {
  const stream = new BattleStream();
  assert.throws(()=>stream._writeLine('sethp','p2a 100'),/Unrecognized command/);
});
test('Showdown aplica dano e consome PP reais', () => {
  const b=single();
  const before=b.sides[1].pokemon[0].hp;
  const beforePP=b.sides[0].pokemon[0].moveSlots[0].pp;
  b.makeChoices('move 1','move 1');
  assert.ok(b.sides[1].pokemon[0].hp < before);
  assert.equal(b.sides[0].pokemon[0].moveSlots[0].pp,beforePP-1);
});
test('Mesmo UUID não sincroniza HP entre sessões independentes', () => {
  const a=single(), b=single();
  const hp=b.sides[1].pokemon[0].hp;
  assert.equal(a.sides[1].pokemon[0].uuid,b.sides[1].pokemon[0].uuid);
  a.makeChoices('move 1','move 1');
  assert.ok(a.sides[1].pokemon[0].hp < hp);
  assert.equal(b.sides[1].pokemon[0].hp,hp);
});
test('Desmaio local encerra a sessão e produz faint/win', () => {
  const b=single();
  b.sides[1].pokemon[0].hp=1;
  b.makeChoices('move 1','move 1');
  assert.equal(b.ended,true);
  assert.ok(b.log.some(line=>line.startsWith('|faint|')));
  assert.ok(b.log.some(line=>line.startsWith('|win|')));
});
test('Dano tem mensagens privada e pública; não contar as duas', () => {
  const b=single(); b.makeChoices('move 1','move 1');
  // Scan each split: another split for the same side may precede this damage.
  const pairs=b.log.flatMap((line,i)=>line==='|split|p2' && b.log[i+1]?.startsWith('|-damage|') ? [[b.log[i+1],b.log[i+2]]] : []);
  assert.equal(pairs.length,1);
  assert.ok(pairs[0][0].includes('same-boss-uuid'));
  assert.ok(pairs[0][1].includes('same-boss-uuid'));
});
console.log(`${passed} verificações reais do simulador passaram. Nenhum teste com três clientes foi executado.`);
