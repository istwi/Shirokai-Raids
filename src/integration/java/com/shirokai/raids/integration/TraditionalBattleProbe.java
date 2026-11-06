package com.shirokai.raids.integration;

import com.shirokai.raids.core.RaidCombatCoordinator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** CLI fixture only: Java ledger + Node-hosted official engine, not Java/Graal or clients. */
public final class TraditionalBattleProbe {
    private final RaidCombatCoordinator raid = new RaidCombatCoordinator(UUID.randomUUID(), 2000, 3);
    private final Map<Integer,RaidCombatCoordinator.Session> sessions = new HashMap<>();
    private final Map<UUID,Integer> indexes = new HashMap<>();
    private final Set<String> bossIds = new HashSet<>();
    private final Set<Integer> stopped = new HashSet<>();
    private final BufferedReader input;
    private final BufferedWriter output;
    private int transitions;
    private long requested;

    private TraditionalBattleProbe(Process process) {
        input = process.inputReader(StandardCharsets.UTF_8);
        output = process.outputWriter(StandardCharsets.UTF_8);
    }
    private static void check(boolean ok, String description) {
        if (!ok) throw new IllegalStateException(description);
    }
    private String[] exchange(String command) throws IOException {
        output.write(command); output.newLine(); output.flush();
        String reply=input.readLine();
        if(reply==null) throw new EOFException("Motor encerrou sem resposta: " + command);
        return reply.split("\t",-1);
    }
    private void join(int index) throws IOException {
        var session=raid.join(UUID.randomUUID()).orElseThrow();
        sessions.put(index,session); indexes.put(session.token(),index);
        var reply=exchange("join\t"+index);
        check(reply.length==3 && reply[0].equals("joined"),"Admissão no motor falhou");
        check(bossIds.add(reply[2]),"Identidade de boss reutilizada");
    }
    private void stop(int index) throws IOException {
        check(stopped.add(index),"Encerramento duplicado");
        var reply=exchange("stop\t"+index);
        check(reply[0].equals("stopped"),"Motor não encerrou sessão");
    }
    private void move(int index) throws IOException {
        var reply=exchange("move\t"+index);
        check(reply.length==3 && reply[0].equals("damage") && !reply[2].isEmpty(),"Movimento não gerou dano");
        for(var event:reply[2].split(",")) {
            String[] parts=event.split(":");
            long sequence=Long.parseLong(parts[0]), amount=Long.parseLong(parts[1]);
            requested+=amount;
            var result=raid.applyDamage(sessions.get(index),sequence,amount);
            check(result.rejection()==RaidCombatCoordinator.Rejection.NONE,"Evento válido recusado");
            check(result.applied()<=amount,"Crédito maior que dano solicitado");
            // Replay of the same private message must never credit again.
            var replay=raid.applyDamage(sessions.get(index),sequence,amount);
            check(replay.applied()==0 && !replay.terminalTransition(),"Replay alterou a raid");
            if(result.terminalTransition()) {
                transitions++;
                for(var session:result.sessionsToClose()) stop(indexes.get(session.token()));
            }
        }
    }
    private void run() throws IOException {
        join(0); join(1);
        move(0); move(1);
        long beforeLateJoin=raid.snapshot().health();
        check(beforeLateJoin>0 && beforeLateJoin<2000,"HP inicial não compartilhado");
        join(2); // Third independent engine session starts after combat is underway.
        check(raid.snapshot().health()==beforeLateJoin,"Entrada tardia restaurou HP");
        move(2);
        check(raid.snapshot().damage().size()==3,"Falta contribuição de algum dos três participantes");
        check(raid.leave(sessions.get(1)),"Saída recusada"); stop(1);
        check(raid.applyDamage(sessions.get(1),999,100).applied()==0,"Sessão encerrada aplicou dano");
        int round=0;
        while(raid.snapshot().state()==RaidCombatCoordinator.State.ACTIVE && round++<12) {
            move(0);
            if(raid.snapshot().state()==RaidCombatCoordinator.State.ACTIVE) move(2);
        }
        var result=raid.snapshot();
        check(result.state()==RaidCombatCoordinator.State.DEFEATED,"Raid não terminou antes de esgotar PP");
        check(result.health()==0 && transitions==1 && stopped.size()==3,"Transição/encerramento inconsistente");
        check(result.damage().values().stream().mapToLong(Long::longValue).sum()==2000,"HP e contribuição divergiram");
        check(result.damage().get(sessions.get(1).player())>0,"Saída perdeu contribuição");
        check(raid.join(UUID.randomUUID()).isEmpty(),"Entrada após derrota permitida");
        check(exchange("exit")[0].equals("closed"),"Sessão vazou");
        System.out.println("PASS 3 sessões Showdown reais + coordenador Java: entrada tardia, PP, replay, saída isolada, HP global, overkill e encerramento único.");
        System.out.println("Dano solicitado="+requested+", efetivo=2000. Sem clientes Minecraft ou ponte Java/Graal; capacidade de 100 não medida.");
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=3) throw new IllegalArgumentException("worker.cjs engine-directory raidboss.js");
        var process=new ProcessBuilder("node",args[0],args[1],args[2]).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
            new TraditionalBattleProbe(process).run();
            check(process.waitFor()==0,"Motor falhou");
        } finally { process.destroyForcibly(); }
    }
}
