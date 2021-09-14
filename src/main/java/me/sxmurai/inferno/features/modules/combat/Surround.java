package me.sxmurai.inferno.features.modules.combat;

import me.sxmurai.inferno.Inferno;
import me.sxmurai.inferno.events.entity.JumpEvent;
import me.sxmurai.inferno.events.mc.UpdateEvent;
import me.sxmurai.inferno.features.settings.Setting;
import me.sxmurai.inferno.managers.HoleManager;
import me.sxmurai.inferno.managers.modules.Module;
import me.sxmurai.inferno.utils.BlockUtil;
import me.sxmurai.inferno.utils.InventoryUtils;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Module.Define(name = "Surround", description = "Surrounds your lower hitbox with obsidian", category = Module.Category.COMBAT)
public class Surround extends Module {
    public static Surround INSTANCE;

    public final Setting<Boolean> enableInHole = this.register(new Setting<>("EnableInHole", false)); // @todo
    public final Setting<Boolean> feet = this.register(new Setting<>("FeetCheck", true)); // @todo
    public final Setting<Toggle> toggle = this.register(new Setting<>("Toggle", Toggle.JUMP));
    public final Setting<Boolean> noAutoCrystal = this.register(new Setting<>("TurnOffAC", false));

    public final Setting<Boolean> silent = this.register(new Setting<>("Silent", false));
    public final Setting<Boolean> center = this.register(new Setting<>("Center", false));
    public final Setting<Boolean> rotate = this.register(new Setting<>("Rotate", true));
    public final Setting<Boolean> swing = this.register(new Setting<>("Swing", true));
    public final Setting<Boolean> packet = this.register(new Setting<>("Packet", false));
    public final Setting<Boolean> sneak = this.register(new Setting<>("Sneak", true));
    public final Setting<Integer> blocksPerTick = this.register(new Setting<>("BlocksPerTick", 1, 1, 4));

    private final ArrayList<BlockPos> queue = new ArrayList<>();
    private boolean finished = false;
    private int placed = 0;
    private boolean waiting = false;

    private EnumHand hand;
    private int oldSlot = -1;

    private boolean wasAutoCrystalEnabled = false;

    public Surround() {
        INSTANCE = this;
    }

    @Override
    protected void onActivated() {
        if (Module.fullNullCheck()) {
            this.toggle();
        } else {
            if (this.center.getValue()) {
                mc.player.motionX = ((Math.floor(mc.player.posX) + 0.5) - mc.player.posX) / 2;
                mc.player.motionZ = ((Math.floor(mc.player.posZ) + 0.5) - mc.player.posZ) / 2;
            }

            if (this.noAutoCrystal.getValue()) {
                AutoCrystal autoCrystal = Inferno.moduleManager.getModule(AutoCrystal.class);
                if (!autoCrystal.isToggled()) {
                    return;
                }

                this.wasAutoCrystalEnabled = true;
                autoCrystal.toggle();
            }
        }
    }

    @Override
    protected void onDeactivated() {
        this.queue.clear();
        this.finished = false;
        this.placed = 0;
        this.waiting = false;

        if (this.oldSlot != -1 && !Module.fullNullCheck()) {
            InventoryUtils.switchTo(this.oldSlot, this.silent.getValue());
        }

        this.oldSlot = -1;
        this.hand = null;
    }

    @SubscribeEvent
    public void onUpdate(UpdateEvent event) {
        if (this.finished) {
            if (this.wasAutoCrystalEnabled) {
                Inferno.moduleManager.getModule(AutoCrystal.class).toggle();
            }

            if (this.toggle.getValue() == Toggle.FINISHED) {
                this.toggle();
                return;
            } else if (this.toggle.getValue() == Toggle.SNEAK && (mc.player.isSneaking() || mc.gameSettings.keyBindSneak.isKeyDown())) {
                this.toggle();
                return;
            }
        }

        if (this.waiting) {
            this.waiting = false;
            this.placed = 0;
        }

        int slot = InventoryUtils.getHotbarBlockSlot(Blocks.OBSIDIAN, true);
        if (slot == -1) {
            Command.send(ChatColor.Dark_Gray.text("You have no obsidian in your hotbar, toggling..."));
            this.toggle();
            return;
        }

        if (slot == 45) {
            this.hand = EnumHand.OFF_HAND;
        } else {
            this.hand = EnumHand.MAIN_HAND;
            InventoryUtils.switchTo(slot, this.silent.getValue());
        }

        if (this.queue.isEmpty()) {
            List<BlockPos> positions = Arrays.stream(HoleManager.SURROUND_POSITIONS)
                    .map((pos) -> new BlockPos(mc.player.posX, mc.player.posY, mc.player.posZ).add(pos))
                    .filter((pos) -> mc.world.getBlockState(pos).getMaterial().isReplaceable())
                    .collect(Collectors.toList());

            if (!positions.isEmpty()) {
                if (!this.feet.getValue()) {
                    positions.removeIf((pos) -> pos.subtract(new BlockPos(mc.player.getPositionVector())).equals(new BlockPos(0, -1, 0)));
                }

                this.queue.addAll(positions);
                if (this.finished) {
                    this.finished = false;
                    this.waiting = false;
                    this.placed = 0;
                }
            } else {
                this.finished = true;
            }
        }

        if (!this.finished && !this.waiting) {
            for (int i = 0; i < this.queue.size(); ++i) {
                BlockPos pos = this.queue.get(i);

                this.place(pos);
                this.queue.remove(pos);

                if (mc.world.isAirBlock(pos)) {
                    this.queue.add(pos.add(0.0, -1.0, 0.0));
                    this.queue.add(pos);
                    this.placed = this.blocksPerTick.getValue();
                }

                ++this.placed;
                if (this.placed > this.blocksPerTick.getValue()) {
                    this.waiting = true;
                    break;
                }
            }

            if (this.queue.isEmpty()) {
                this.finished = true;
            }
        }
    }

    @SubscribeEvent
    public void onJump(JumpEvent event) {
        if (event.getPlayer() == mc.player && this.finished && this.toggle.getValue() == Toggle.JUMP) {
            this.toggle();
        }
    }

    private void place(BlockPos pos) {
        BlockUtil.place(pos, this.hand, this.swing.getValue(), this.sneak.getValue(), this.packet.getValue(), this.rotate.getValue());
    }

    public enum Toggle {
        JUMP, SNEAK, FINISHED, MANUAL
    }
}
