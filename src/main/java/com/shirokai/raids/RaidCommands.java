package com.shirokai.raids;

final class RaidCommands {
    private RaidCommands() {}
    static boolean allowedDuringRaid(String command) {
        return command.strip().matches("raid(?: +(sair|menu))?");
    }
}
