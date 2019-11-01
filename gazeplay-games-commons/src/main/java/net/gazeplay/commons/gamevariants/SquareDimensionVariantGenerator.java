package net.gazeplay.commons.gamevariants;

import com.google.common.collect.Sets;
import net.gazeplay.GameSpec;

import java.util.Set;

public class SquareDimensionVariantGenerator implements GameSpec.GameVariantGenerator {

    private final int minSize;

    private final int maxSize;
    
    public SquareDimensionVariantGenerator(int minSize, int maxSize) {
        super();
        this.minSize = minSize;
        this.maxSize = maxSize;
    }

    @Override
    public Set<GameSpec.GameVariant> getVariants() {
        Set<GameSpec.GameVariant> result = Sets.newLinkedHashSet();
        for (int i = minSize; i <= maxSize; i++) {
            result.add(new GameSpec.DimensionGameVariant(i, i));
        }
        return result;
    }
}
