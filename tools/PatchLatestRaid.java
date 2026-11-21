import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class PatchLatestRaid {
 public static void main(String[] args)throws Exception {
  var node=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(node,0);int hook=0,message=0,origin=0;
  String owner="com/shirokai/raids/RaidReturnFallback";
  for(var method:node.methods){
   if(method.name.equals("returnFailure")){
    var player=Type.getArgumentTypes(method.desc)[0].getDescriptor();var cont=new LabelNode();var add=new InsnList();
    add.add(new VarInsnNode(Opcodes.ALOAD,0));add.add(new VarInsnNode(Opcodes.ALOAD,1));add.add(new VarInsnNode(Opcodes.ALOAD,3));
    add.add(new MethodInsnNode(Opcodes.INVOKESTATIC,owner,"trySpawn","(Ljava/lang/Object;"+player+"Ljava/lang/String;)Z",false));add.add(new JumpInsnNode(Opcodes.IFEQ,cont));add.add(new InsnNode(Opcodes.ICONST_1));add.add(new InsnNode(Opcodes.IRETURN));add.add(cont);add.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));method.instructions.insert(add);method.maxStack=Math.max(method.maxStack,3);hook++;
   }
   if(method.name.equals("attemptReturn")){
    for(var instruction:method.instructions.toArray())if(instruction instanceof TypeInsnNode cast&&cast.getOpcode()==Opcodes.CHECKCAST&&cast.desc.equals("com/shirokai/raids/RaidState$ReturnPosition")){
     var add=new InsnList();add.add(new VarInsnNode(Opcodes.ALOAD,1));add.add(new MethodInsnNode(Opcodes.INVOKESTATIC,owner,"overrideOrigin","(Lcom/shirokai/raids/RaidState$ReturnPosition;Lnet/minecraft/class_3222;)Lcom/shirokai/raids/RaidState$ReturnPosition;",false));method.instructions.insert(cast,add);method.maxStack++;origin++;
    }
   }
   if(method.name.equals("leave")){
    int playerLocal=-1;String player=null;
    for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call && call.desc.equals("()Lnet/minecraft/class_3222;")&&call.getNext() instanceof VarInsnNode local&&local.getOpcode()==Opcodes.ASTORE){playerLocal=local.var;player="Lnet/minecraft/class_3222;";break;}
    for(var instruction:method.instructions.toArray())if(instruction instanceof LdcInsnNode ldc&&"Retorno pendente. Confira a mensagem de recuperação e tente /raid sair novamente.".equals(ldc.cst)){
     if(playerLocal<0)throw new IllegalStateException("Known player local not found");var add=new InsnList();add.add(new VarInsnNode(Opcodes.ALOAD,0));add.add(new VarInsnNode(Opcodes.ALOAD,playerLocal));add.add(new MethodInsnNode(Opcodes.INVOKESTATIC,owner,"pendingMessage","(Ljava/lang/Object;"+player+")Ljava/lang/String;",false));method.instructions.insertBefore(ldc,add);method.instructions.remove(ldc);method.maxStack+=2;message++;
    }
   }
  }
  if(hook!=1||message!=1||origin!=1)throw new IllegalStateException("Expected pinned return hook/message: "+hook+"/"+message+"/"+origin);
  var writer=new ClassWriter(0);node.accept(writer);Files.write(Path.of(args[1]),writer.toByteArray());
 }
}
