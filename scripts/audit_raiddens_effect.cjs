// Isolated callbacks from the supplied Raid Dens JAR, not a Showdown integration test.
// External code is read at runtime, never bundled in Shirokai Raids.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(process.argv[2], 'utf8');
const condition = vm.runInNewContext(`(${source})`, {}, {timeout: 1000});
let passed = 0;
function test(name, body) { body(); passed++; console.log(`PASS ${name}`); }
function fixture() {
  const log = [], damage = [], healing = [];
  return {log, damage, healing, context: {
    add(...args) { log.push(args); },
    damage(...args) { damage.push(args); },
    heal(...args) { healing.push(args); },
    effectState: {},
  }};
}
const target = {hp: 100, maxhp: 100, baseMaxhp: 100};
const attacker = {item: '', forceSwitchFlag: false};
const move = {id: 'dragonclaw', fullname: 'move: Dragon Claw', effectType: 'Move'};
test('Dano letal é emitido como delta, mas callback retorna zero', () => {
  const f = fixture();
  assert.equal(condition.onDamage.call(f.context, 150, target, attacker, move), 0);
  assert.equal(f.log.length, 3);
  assert.deepEqual(f.log[0], ['split', 'p2']);
  assert.equal(f.log[1][0], '-raiddamage');
  assert.equal(f.log[1][2], 150);
  assert.equal(target.hp, 100);
});
test('Mensagem pública repete o mesmo golpe como porcentagem', () => {
  const f = fixture();
  condition.onDamage.call(f.context, 25, {...target, maxhp: 200}, attacker, move);
  assert.equal(f.log[1][2], 25);
  assert.equal(f.log[2][2], 12);
});
test('Dois golpes iguais produzem eventos distintos', () => {
  const f = fixture();
  for (let i = 0; i < 2; i++) condition.onDamage.call(f.context, 25, target, attacker, move);
  assert.equal(f.log.filter(row => row[0] === 'split').length, 2);
});
test('Recoil usa dano solicitado, sem limitar pelo HP global', () => {
  const f = fixture();
  condition.onDamage.call(f.context, 150, target, attacker, {...move, recoil: [1, 3]});
  assert.equal(f.damage[0][0], 50);
});
test('Drain também usa dano solicitado', () => {
  const f = fixture();
  condition.onDamage.call(f.context, 150, target, attacker, {...move, drain: [1, 2]});
  assert.equal(f.healing[0][0], 75);
});
test('Cura com HP local cheio não emite pedido de cura global', () => {
  const f = fixture();
  assert.equal(condition.onTryHeal.call(f.context, 20, target, attacker, move), false);
  assert.equal(f.log.length, 0);
});
test('Cura parcial emite deltas sem curar o HP local', () => {
  const f = fixture(), boss = {...target, hp: 50};
  assert.equal(condition.onTryHeal.call(f.context, 20, boss, attacker, {...move, id: 'recover'}), false);
  assert.equal(f.log[1][0], '-raidheal');
  assert.equal(f.log[1][2], 20);
  assert.equal(boss.hp, 50);
  // This Move branch emits only one message after split, unlike onDamage.
  assert.equal(f.log.length, 2);
});
test('Perish Song e auto-sacrifício são explicitamente recusados', () => {
  const f = fixture();
  for (const id of ['perishsong', 'explosion', 'memento', 'transform']) {
    assert.equal(condition.onTryMove.call(f.context, attacker, target, {id}), false);
  }
});
console.log(`${passed} callbacks isolados passaram; nenhum motor Showdown, cliente ou servidor Minecraft foi exercitado.`);
