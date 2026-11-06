// Executes the original external raid condition inside the real 1.8.3 simulator.
// No Raid Dens source is bundled; this is an audit harness, not a runtime adapter.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {Battle} = require(path.join(process.argv[2], 'sim/battle.js'));
const {Dex} = require(path.join(process.argv[2], 'sim/dex.js'));
function pokemon(species, uuid, moves) {
  return {species, uuid, level:100, moves, movesInfo:moves.map(m=>({pp:Dex.moves.get(m).pp,maxPp:Dex.moves.get(m).pp}))};
}
const effect = vm.runInNewContext(`(${fs.readFileSync(process.argv[3], 'utf8')})`, {}, {timeout:1000});
let passed = 0;
function test(name, body) { body(); passed++; console.log(`PASS ${name}`); }
function single(id, moves = ['dragonclaw']) {
  const b = new Battle({formatid:'gen9customgame', seed:[1,2,3,4]});
  b.setPlayer('p1', {name:`P${id}`, team:[pokemon('Garchomp',`player-${id}`,moves)]});
  b.setPlayer('p2', {name:`Boss${id}`, team:[pokemon('Rayquaza',`boss-session-${id}`,['splash'])]});
  if (b.requestState === 'teampreview') b.makeChoices('team 1','team 1');
  b.dex.data.Conditions.raidboss = effect;
  assert.equal(b.sides[1].active[0].addVolatile('raidboss'), true);
  return b;
}
function privateDamage(b, from) {
  return b.log.slice(from).flatMap((line,i,log) =>
    line === '|split|p2' && log[i+1]?.startsWith('|-raiddamage|') ? [Number(log[i+1].split('|')[3])] : []);
}
test('Hook real preserva sessão e PP quando dano excede HP local', () => {
  const b=single('lethal'), boss=b.sides[1].active[0];
  boss.hp=1;
  const pp=b.sides[0].active[0].moveSlots[0].pp, from=b.log.length;
  b.makeChoices('move 1','move 1');
  assert.equal(b.ended,false);
  assert.ok(privateDamage(b,from)[0]>1);
  assert.equal(boss.hp,1);
  assert.equal(b.sides[0].active[0].moveSlots[0].pp,pp-1);
  assert.equal(b.log.slice(from).some(l=>l.startsWith('|faint|p2')),false);
});
test('Três sessões reais possuem objetos e UUIDs separados', () => {
  const battles=[single('a'),single('b'),single('c')];
  const bosses=battles.map(b=>b.sides[1].active[0]);
  assert.equal(new Set(bosses).size,3);
  assert.equal(new Set(bosses.map(p=>p.uuid)).size,3);
  for (const b of battles) {
    const from=b.log.length;
    b.makeChoices('move 1','move 1');
    assert.ok(privateDamage(b,from).length>0);
    assert.equal(b.ended,false);
  }
});
test('HP interno não acompanha automaticamente o delta enviado ao Java', () => {
  const b=single('snapshot'), boss=b.sides[1].active[0], hp=boss.hp, from=b.log.length;
  b.makeChoices('move 1','move 1');
  assert.ok(privateDamage(b,from).reduce((a,v)=>a+v,0)>0);
  // The original condition can adjust a full HP bar by one during residual.
  assert.ok(boss.hp>=hp-1);
});
test('Troca de Pokémon funciona em sessão com o hook ativo', () => {
  const b = new Battle({formatid:'gen9customgame',seed:[1,2,3,4]});
  b.setPlayer('p1',{name:'Switcher',team:[
    pokemon('Garchomp','lead',['dragonclaw']),
    pokemon('Mewtwo','bench',['psychic']),
  ]});
  b.setPlayer('p2',{name:'Boss',team:[pokemon('Rayquaza','switch-boss',['splash'])]});
  if(b.requestState==='teampreview') b.makeChoices('team 12','team 1');
  b.dex.data.Conditions.raidboss=effect;
  assert.equal(b.sides[1].active[0].addVolatile('raidboss'),true);
  b.makeChoices('switch 2','move 1');
  assert.equal(b.sides[0].active[0].uuid,'bench');
  assert.equal(b.ended,false);
});
test('Imunidade não gera dano fictício contra Rayquaza', () => {
  const b=single('immune',['earthquake']), from=b.log.length;
  b.makeChoices('move 1','move 1');
  assert.equal(privateDamage(b,from).length,0);
  assert.ok(b.log.slice(from).some(l=>l.startsWith('|-immune|p2')));
});
test('Forcetie encerra sessões sem vitória local de jogador', () => {
  const b=single('stop');
  b.forceWin();
  assert.equal(b.ended,true);
  assert.ok(b.log.some(l=>l==='|tie'));
  assert.ok(b.inputLog.includes('>forcetie'));
  assert.equal(b.log.some(l=>l.startsWith('|win|')),false);
});
console.log(`${passed} verificações no motor real passaram. Sem clientes Minecraft, ponte Java/Graal ou medição de capacidade.`);
