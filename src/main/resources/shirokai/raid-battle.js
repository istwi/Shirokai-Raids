(function installRaidBattle(battle, raidId, bridge) {
    /* Only the detached boss instance is adapted. Standard Showdown still resolves
       accuracy, effectiveness, PP, abilities, drain and recoil for the chosen move. */
    const boss = battle.sides[1].active[0];
    boss.damage = function (amount, source, effect) {
        if (!Number.isFinite(amount) || amount <= 0) return 0;
        if (source === this && effect && ['recoil', 'confusion', 'strugglerecoil'].includes(effect.id)) return 0;
        battle.__shirokaiSeq = (battle.__shirokaiSeq || 0) + 1;
        const applied = bridge.damage(raidId, battle.__shirokaiSeq, Math.floor(amount));
        if (bridge.remaining(raidId) === 0) battle.queue.clear();
        return applied;
    };
    /* All boss KOs belong to the central raid, including direct faint effects. */
    boss.faint = function () { return 0; };
    battle.dex.data.Conditions.shirokaiboss = {
        name: 'shirokaiboss',
        onTryHeal: function () { return false; },
        onAnyTryMove: function (source, target, move) {
            if (move.id === 'perishsong' ||
                (source.side.id === 'p2' && ['explosion', 'selfdestruct', 'memento', 'finalgambit'].includes(move.id))) {
                this.add('-fail', source);
                return false;
            }
        }
    };
    if (!boss.addVolatile('shirokaiboss')) throw new Error('Raid hook unavailable');
})
