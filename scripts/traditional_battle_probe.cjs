// Line protocol for the Java ledger probe. Real Showdown, external raid condition.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const readline = require('node:readline');
const {Battle} = require(path.join(process.argv[2], 'sim/battle.js'));
const {Dex} = require(path.join(process.argv[2], 'sim/dex.js'));
const condition = vm.runInNewContext(`(${fs.readFileSync(process.argv[3],'utf8')})`, {}, {timeout:1000});
const battles = new Map();
function team(species, uuid, move) {
  const pp=Dex.moves.get(move).pp;
  return [{species,uuid,level:100,moves:[move],movesInfo:[{pp,maxPp:pp}]}];
}
readline.createInterface({input:process.stdin}).on('line',line=>{
  try {
    const [command,id]=line.split('\t');
    if(command==='join') {
      if(battles.has(id)) throw new Error('duplicate session');
      const battle=new Battle({formatid:'gen9customgame',seed:[1,2,3,4]});
      battle.setPlayer('p1',{name:`P${id}`,team:team('Garchomp',`player-${id}`,'dragonclaw')});
      battle.setPlayer('p2',{name:`Boss${id}`,team:team('Rayquaza',`boss-${id}`,'splash')});
      if(battle.requestState==='teampreview') battle.makeChoices('team 1','team 1');
      battle.dex.data.Conditions.raidboss=condition;
      if(!battle.sides[1].active[0].addVolatile('raidboss')) throw new Error('hook unavailable');
      battles.set(id,{battle,sequence:0});
      console.log(`joined\t${id}\t${battle.sides[1].active[0].uuid}`);
    } else if(command==='move') {
      const entry=battles.get(id), battle=entry.battle;
      if(battle.ended) throw new Error('session ended');
      const from=battle.log.length, beforePP=battle.sides[0].active[0].moveSlots[0].pp;
      battle.makeChoices('move 1','move 1');
      const log=battle.log.slice(from), events=[];
      for(let i=0;i<log.length;i++) {
        if(log[i]==='|split|p2' && log[i+1]?.startsWith('|-raiddamage|')) {
          const amount=Number(log[i+1].split('|')[3]);
          if(!Number.isSafeInteger(amount) || amount<0) throw new Error('invalid damage');
          events.push(`${++entry.sequence}:${amount}`);
        }
      }
      const afterPP=battle.sides[0].active[0].moveSlots[0].pp;
      if(afterPP!==beforePP-1 || battle.ended) throw new Error('invalid PP or local KO');
      console.log(`damage\t${id}\t${events.join(',')}`);
    } else if(command==='stop') {
      const battle=battles.get(id).battle;
      battle.forceWin();
      if(!battle.ended || battle.winner!=='') throw new Error('not a tie');
      console.log(`stopped\t${id}`);
    } else if(command==='exit') {
      if([...battles.values()].some(e=>!e.battle.ended)) throw new Error('leaked session');
      console.log('closed');
      process.exit(0);
    } else throw new Error('unknown command');
  } catch(error) {
    console.error(error.stack);
    process.exit(1);
  }
});
