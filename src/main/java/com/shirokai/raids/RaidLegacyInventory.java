package com.shirokai.raids;
import com.shirokai.raids.mixin.PlayerListAccessor;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.util.HashSet;

/** One-time recovery, never used to store items for the new empty-inventory admission rule. */
public final class RaidLegacyInventory {
    public static final String KEY="ShirokaiRaidInventory";
    private RaidLegacyInventory() { }
    public static boolean pending(ServerPlayer p) { return ((RaidInventoryAccess)p).shirokaiInventory()!=null; }
    public static void recover(ServerPlayer p) throws IOException {
        var access=(RaidInventoryAccess)p;var old=access.shirokaiInventory();
        if(old==null || !p.isAlive()) return;
        if(old.getInt("Version")!=1 || !(old.get("Items") instanceof ListTag items)
            || !items.isEmpty()&&items.getElementType()!=Tag.TAG_COMPOUND || old.getInt("Selected")<0 || old.getInt("Selected")>8)
            throw new IOException("Backup legado inválido; preservado para recuperação administrativa.");
        var slots=new HashSet<Integer>();
        for(var tag:items) {
            var item=(CompoundTag)tag;int slot=item.getByte("Slot")&255;
            if(!(slot<36 || slot>=100&&slot<=103 || slot==150) || !slots.add(slot)
                || ItemStack.parse(p.registryAccess(),tag).filter(s->!s.isEmpty()).isEmpty())
                throw new IOException("Item legado indisponível; backup preservado.");
        }
        for(int i=0;i<p.getInventory().getContainerSize();i++) {
            var stack=p.getInventory().getItem(i);
            if(!stack.isEmpty()&&!RaidCapture.isBall(stack)) throw new IOException("Inventário atual não está vazio; backup legado preservado sem sobrescrever itens.");
        }
        if(!p.containerMenu.getCarried().isEmpty()&&!RaidCapture.isBall(p.containerMenu.getCarried())) throw new IOException("Esvazie o cursor para recuperar o inventário legado.");
        RaidInventory.removeRaidBalls(p);
        p.getInventory().load(items);p.getInventory().selected=old.getInt("Selected");access.shirokaiInventory(null);RaidInventory.sync(p);
        try {
            ((PlayerListAccessor)p.server.getPlayerList()).shirokaiSavePlayer(p);
            var saved=NbtIo.readCompressed(p.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID()+".dat"),NbtAccounter.unlimitedHeap());
            if(saved==null||saved.contains(KEY)||!saved.getList("Inventory",Tag.TAG_COMPOUND).equals(p.getInventory().save(new ListTag())))
                throw new IOException("Recuperação legada não confirmada em disco.");
        } catch(IOException error) { access.shirokaiInventory(old);throw error; }
    }
}
