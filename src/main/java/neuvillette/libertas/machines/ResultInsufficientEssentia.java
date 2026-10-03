package neuvillette.libertas.machines;

import java.io.IOException;

import javax.annotation.Nonnull;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StatCollector;

import org.jetbrains.annotations.NotNull;

import gregtech.api.recipe.check.CheckRecipeResult;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

/**
 * 配方检查失败原因：催化剂匹配到了坩埚配方，但要素提炼仓中的要素不足。
 * GUI 状态区会显示缺少的要素及数量。
 */
public class ResultInsufficientEssentia implements CheckRecipeResult {

    private AspectList missing;

    public ResultInsufficientEssentia() {}

    public ResultInsufficientEssentia(AspectList missing) {
        this.missing = missing;
    }

    @Override
    @Nonnull
    public @NotNull String getID() {
        return "insufficient_essentia";
    }

    @Override
    public boolean wasSuccessful() {
        return false;
    }

    @Override
    @Nonnull
    public @NotNull String getDisplayString() {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Aspect aspect : missing.getAspects()) {
            if (!first) builder.append(", ");
            first = false;
            builder.append(aspect.getName())
                .append(" x")
                .append(missing.getAmount(aspect));
        }
        return StatCollector.translateToLocalFormatted("libertas.gui.recipe_result.insufficient_essentia", builder);
    }

    @Override
    public @NotNull NBTTagCompound writeToNBT(@NotNull NBTTagCompound tag) {
        if (missing != null) missing.writeToNBT(tag);
        return tag;
    }

    @Override
    public void readFromNBT(@NotNull NBTTagCompound tag) {
        missing = new AspectList();
        missing.readFromNBT(tag);
    }

    @Override
    @Nonnull
    public CheckRecipeResult newInstance() {
        return new ResultInsufficientEssentia();
    }

    @Override
    public void encode(@Nonnull PacketBuffer buffer) {
        try {
            if (missing == null) {
                buffer.writeVarIntToBuffer(0);
                return;
            }
            Aspect[] aspects = missing.getAspects();
            buffer.writeVarIntToBuffer(aspects.length);
            for (Aspect aspect : aspects) {
                buffer.writeStringToBuffer(aspect.getTag());
                buffer.writeVarIntToBuffer(missing.getAmount(aspect));
            }
        } catch (IOException e) {
            // PacketBuffer 写入内存缓冲区不会抛 IOException，仅满足接口签名
        }
    }

    @Override
    public void decode(PacketBuffer buffer) {
        try {
            int count = buffer.readVarIntFromBuffer();
            missing = new AspectList();
            for (int i = 0; i < count; i++) {
                String tag = buffer.readStringFromBuffer(64);
                int amount = buffer.readVarIntFromBuffer();
                Aspect aspect = Aspect.getAspect(tag);
                if (aspect != null) missing.add(aspect, amount);
            }
        } catch (IOException e) {
            missing = new AspectList();
        }
    }

    private static boolean aspectListEquals(AspectList a, AspectList b) {
        if (a == null || b == null) return a == b;
        if (a.size() != b.size()) return false;
        for (Aspect aspect : a.getAspects()) {
            if (a.getAmount(aspect) != b.getAmount(aspect)) return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResultInsufficientEssentia that = (ResultInsufficientEssentia) o;
        return aspectListEquals(missing, that.missing);
    }
}
