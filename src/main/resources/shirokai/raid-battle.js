(function installRaidBattle(battle, raidId, bridge, damageMultiplier = 1) {
    if (!Number.isFinite(damageMultiplier) || damageMultiplier < 0 || damageMultiplier > 100) {
        throw new Error('Invalid boss damage multiplier');
    }
    battle.__shirokaiDamageMultiplier = damageMultiplier;
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
        onAnySwitchIn: function (pokemon) {
            if (pokemon.side.id === 'p1') pokemon.addVolatile('shirokaiplayer');
        },
        onAnyTryMove: function (source, target, move) {
            if (move.id === 'perishsong' ||
                (source.side.id === 'p2' && ['explosion', 'selfdestruct', 'memento', 'finalgambit'].includes(move.id))) {
                this.add('-fail', source);
                return false;
            }
        }
    };
    /* Run before normal Damage handlers such as Sturdy/Focus Sash. State belongs
       to the battle, not this shared Dex condition, so sessions cannot leak multipliers. */
    battle.dex.data.Conditions.shirokaiplayer = {
        name: 'shirokaiplayer',
        onDamagePriority: 1000,
        onDamage: function (amount, target, source, effect) {
            if (source && source.side.id === 'p2' && effect && effect.effectType === 'Move') {
                return Math.min(2147483647, Math.round(amount * this.__shirokaiDamageMultiplier));
            }
            return amount;
        }
    };
    if (!boss.addVolatile('shirokaiboss')) throw new Error('Raid hook unavailable');
    for (const pokemon of battle.sides[0].active) if (pokemon) pokemon.addVolatile('shirokaiplayer');
})
