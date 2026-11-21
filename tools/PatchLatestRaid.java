import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class PatchLatestRaid {
 public static void main(String[] args)throws Exception{
  var node=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(node,0);int calls=0;
  for(var method:node.methods)if(method.name.equals("tryChallenge"))for(var instruction:method.instructions)
   if(instruction instanceof MethodInsnNode call && call.owner.equals("com/shirokai/raids/RaidInventory") && call.name.equals("requireEmpty")){call.name="requireParticipant";calls++;}
  if(calls!=1)throw new IllegalStateException("Expected the known 0.1.22 challenge inventory check, found "+calls);
  var writer=new ClassWriter(0);node.accept(writer);Files.write(Path.of(args[1]),writer.toByteArray());
 }
}
