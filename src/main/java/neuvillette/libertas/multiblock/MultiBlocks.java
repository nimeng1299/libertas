package neuvillette.libertas.multiblock;


public enum MultiBlocks {
    MAIN_STONE_MINER(29100);

    private final int id;

    MultiBlocks(int i) {
        this.id = i;
    }

    public static void  register() {
        new StoneMiner(getBlockId(MAIN_STONE_MINER), "stoneminer", "石头矿机").getStackForm(1);
    }

    public int getId() {
        return id;
    }

    public static int getBlockId(MultiBlocks  block) {
        return block.getId();
    }
}
