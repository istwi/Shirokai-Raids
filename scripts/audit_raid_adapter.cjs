// Real official simulator + the exact shipped adapter; Java ledger is stubbed here.
// Java/Graal/standard-challenge integration is tested separately on the server.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const {Battle}=require(path.join(process.argv[2],'sim/battle.js'));
const {Dex}=require(path.join(process.argv[2],'sim/dex.js'));
const install=vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../src/main/resources/shirokai/raid-battle.js'),'utf8'),{},{timeout:1000});
let count=0;
function test(name,body){body();count++;console.log(`PASS ${name}`);}
function pokemon(species,uuid,move){const pp=Dex.moves.get(move).pp;return {species,uuid,level:100,moves:[move],movesInfo:[{pp,maxPp:pp}]};}
function fixture(move='dragonclaw',health=2000,species='Garchomp',seed=[1,2,3,4],bossMove='splash') {
  const battle=new Battle({formatid:'gen9customgame',seed});
  battle.setPlayer('p1',{name:'Player',team:[pokemon(species,'player',move)]});
  battle.setPlayer('p2',{name:'Boss',team:[pokemon('Rayquaza','boss',bossMove)]});
  if(battle.requestState==='teampreview')battle.makeChoices('team 1','team 1');
  const events=[];
  const bridge={remaining(){return health;},damage(id,sequence,requested){const applied=Math.min(health,requested);health-=applied;events.push({sequence,requested,applied});return applied;}};
  install(battle,'raid-session',bridge);
  return {battle,events,health:()=>health,player:battle.sides[0].active[0],boss:battle.sides[1].active[0]};
}
test('Sem escolha de movimento não há dano nem consumo de PP',()=>{
  const f=fixture();assert.equal(f.events.length,0);assert.equal(f.health(),2000);
  assert.equal(f.player.moveSlots[0].pp,Dex.moves.get('dragonclaw').pp);
});
test('Movimento real consome PP e preserva HP local até término global',()=>{
  const f=fixture();f.boss.hp=1;const pp=f.player.moveSlots[0].pp;
  f.battle.makeChoices('move 1','move 1');
  assert.ok(f.events[0].applied>1);assert.equal(f.player.moveSlots[0].pp,pp-1);
  assert.equal(f.boss.hp,1);assert.equal(f.battle.ended,false);
});
test('Drain nativo usa apenas dano efetivo, incluindo overkill',()=>{
  const f=fixture('drainpunch',9,'Conkeldurr');f.player.hp=50;
  f.battle.makeChoices('move 1','move 1');
  assert.equal(f.events[0].applied,9);assert.equal(f.health(),0);
  assert.equal(f.player.hp,55);
});
test('Recoil nativo usa apenas dano efetivo, incluindo overkill',()=>{
  const f=fixture('doubleedge',9);const hp=f.player.hp;
  f.battle.makeChoices('move 1','move 1');
  assert.equal(f.events[0].applied,9);assert.equal(f.player.hp,hp-3);
});
test('Derrota global cancela a resposta pendente do boss no mesmo turno',()=>{
  const f=fixture('dragonclaw',9,'Garchomp',[1,2,3,4],'tackle');const hp=f.player.hp;
  f.battle.makeChoices('move 1','move 1');
  assert.equal(f.health(),0);assert.equal(f.player.hp,hp);
  assert.equal(f.boss.moveSlots[0].pp,Dex.moves.get('tackle').pp);
});
test('Imunidade de tipo não debita HP global',()=>{
  const f=fixture('earthquake');f.battle.makeChoices('move 1','move 1');
  assert.equal(f.events.length,0);assert.equal(f.health(),2000);
  assert.ok(f.battle.log.some(l=>l.startsWith('|-immune|p2')));
});
test('Ataque que erra não gera crédito fictício',()=>{
  let checked=false;
  for(let seed=1;seed<=100;seed++) {
    const f=fixture('focusblast',2000,'Mewtwo',[seed,2,3,4]);
    f.battle.makeChoices('move 1','move 1');
    if(f.battle.log.some(l=>l.startsWith('|-miss|'))) {
      assert.equal(f.events.length,0);assert.equal(f.health(),2000);checked=true;break;
    }
  }
  assert.ok(checked,'Nenhum caso de miss foi exercitado');
});
test('Multihit emite sequências distintas para cada acerto real',()=>{
  const f=fixture('bulletseed',2000,'Breloom');f.battle.makeChoices('move 1','move 1');
  assert.ok(f.events.length>=2);assert.equal(new Set(f.events.map(e=>e.sequence)).size,f.events.length);
  assert.equal(2000-f.health(),f.events.reduce((sum,e)=>sum+e.applied,0));
});
test('Desmaio direto do clone não pode finalizar a raid localmente',()=>{
  const f=fixture();assert.equal(f.boss.faint(),0);assert.equal(f.boss.fainted,false);assert.equal(f.battle.ended,false);
});
test('Política atual bloqueia cura do boss, preservando o pool',()=>{
  const f=fixture();f.boss.hp=1;
  f.battle.heal(50,f.boss,f.boss,f.battle.dex.moves.get('recover'));
  assert.equal(f.boss.hp,1);assert.equal(f.health(),2000);
});
console.log(`${count} verificações do adaptador embarcado passaram; bridge Java mockada, nenhum cliente Minecraft.`);
