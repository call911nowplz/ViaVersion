package us.myles.ViaVersion.api.rewriters;

import us.myles.ViaVersion.api.minecraft.item.Item;
import us.myles.ViaVersion.api.protocol.ClientboundPacketType;
import us.myles.ViaVersion.api.protocol.Protocol;
import us.myles.ViaVersion.api.protocol.ServerboundPacketType;
import us.myles.ViaVersion.api.remapper.PacketHandler;
import us.myles.ViaVersion.api.remapper.PacketRemapper;
import us.myles.ViaVersion.api.type.Type;

// If any of these methods become outdated, just create a new rewriter overriding the methods
public class ItemRewriter {
    private final Protocol protocol;
    private final RewriteFunction toClient;
    private final RewriteFunction toServer;

    public ItemRewriter(Protocol protocol, RewriteFunction toClient, RewriteFunction toServer) {
        this.protocol = protocol;
        this.toClient = toClient;
        this.toServer = toServer;
    }

    public void registerWindowItems(ClientboundPacketType packetType, Type<Item[]> type) {
        protocol.registerOutgoing(packetType, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.UNSIGNED_BYTE); // 0 - Window ID
                map(type); // 1 - Window Values

                handler(itemArrayHandler(type));
            }
        });
    }

    public void registerSetSlot(ClientboundPacketType packetType, Type<Item> type) {
        protocol.registerOutgoing(packetType, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.BYTE); // 0 - Window ID
                map(Type.SHORT); // 1 - Slot ID
                map(type); // 2 - Slot Value

                handler(itemToClientHandler(type));
            }
        });
    }

    // Sub 1.16
    public void registerEntityEquipment(ClientboundPacketType packetType, Type<Item> type) {
        protocol.registerOutgoing(packetType, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.VAR_INT); // 0 - Entity ID
                map(Type.VAR_INT); // 1 - Slot ID
                map(type); // 2 - Item

                handler(itemToClientHandler(type));
            }
        });
    }

    // 1.16+
    public void registerEntityEquipmentArray(ClientboundPacketType packetType, Type<Item> type) {
        protocol.registerOutgoing(packetType, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.VAR_INT); // 0 - Entity ID

                handler(wrapper -> {
                    byte slot;
                    do {
                        slot = wrapper.passthrough(Type.BYTE);
                         // & 0x7F into an extra variable if slot is needed
                        toClient.rewrite(wrapper.passthrough(type));
                    } while ((slot & 0xFFFFFF80) != 0);
                });
            }
        });
    }

    public void registerCreativeInvAction(ServerboundPacketType packetType, Type<Item> type) {
        protocol.registerIncoming(packetType, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.SHORT); // 0 - Slot
                map(type); // 1 - Clicked Item

                handler(itemToServerHandler(type));
            }
        });
    }

    public void registerClickWindow(ServerboundPacketType packetType, Type<Item> type) {
        protocol.registerIncoming(packetType, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.UNSIGNED_BYTE); // 0 - Window ID
                map(Type.SHORT); // 1 - Slot
                map(Type.BYTE); // 2 - Button
                map(Type.SHORT); // 3 - Action number
                map(Type.VAR_INT); // 4 - Mode
                map(type); // 5 - Clicked Item

                handler(itemToServerHandler(type));
            }
        });
    }

    public void registerSetCooldown(ClientboundPacketType packetType, IdRewriteFunction itemIDRewriteFunction) {
        protocol.registerOutgoing(packetType, new PacketRemapper() {
            @Override
            public void registerMap() {
                handler(wrapper -> {
                    int itemId = wrapper.read(Type.VAR_INT);
                    wrapper.write(Type.VAR_INT, itemIDRewriteFunction.rewrite(itemId));
                });
            }
        });
    }

    // Only sent to the client
    public PacketHandler itemArrayHandler(Type<Item[]> type) {
        return wrapper -> {
            Item[] items = wrapper.get(type, 0);
            for (Item item : items) {
                toClient.rewrite(item);
            }
        };
    }

    public PacketHandler itemToClientHandler(Type<Item> type) {
        return wrapper -> toClient.rewrite(wrapper.get(type, 0));
    }

    public PacketHandler itemToServerHandler(Type<Item> type) {
        return wrapper -> toServer.rewrite(wrapper.get(type, 0));
    }

    @FunctionalInterface
    public interface RewriteFunction {

        void rewrite(Item item);
    }
}
