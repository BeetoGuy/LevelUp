package levelup.event;

import com.google.common.collect.Sets;
import levelup.ClassBonus;
import levelup.api.IProcessor;
import levelup.capabilities.CapabilityBrewingStand;
import levelup.capabilities.CapabilityFurnace;
import levelup.player.IPlayerClass;
import levelup.LevelUp;
import levelup.player.PlayerExtendedProperties;
import levelup.capabilities.LevelUpCapability;
import levelup.util.PlankCache;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.*;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.oredict.OreDictionary;

import java.util.*;

public final class PlayerEventHandler {
    /**
     * Configurable flags related to breaking speed
     */
    public static boolean oldSpeedDigging = true, oldSpeedRedstone = true;
    /**
     * Configurable flags related to player death
     */
    public static float resetSkillOnDeath = 0.00F;
    public static boolean resetClassOnDeath = false;
    /**
     * If duplicated ores can be placed
     */
    public static boolean noPlaceDuplicate = true;
    /**
     * How much each level give in skill points
     */
    public static double xpPerLevel = 3.0D;
    /**
     * Level at which a player can choose a class, and get its first skill points
     */
    public final static int minLevel = 4;
    /**
     * Random additional loot for Fishing
     */
    private static ResourceLocation fishingLoot = new ResourceLocation("levelup", "fishing/fishing_loot");
    /**
     * Items given by Digging ground
     */
    private static ResourceLocation commonDig = new ResourceLocation("levelup", "digging/common_dig");
    private static ResourceLocation uncommonDig = new ResourceLocation("levelup", "digging/uncommon_dig");
    private static ResourceLocation rareDig = new ResourceLocation("levelup", "digging/rare_dig");
    private static ResourceLocation diggingLoot = new ResourceLocation("levelup", "digging/digging_loot");
    /**
     * Internal ore counter
     */
    private static Map<Block, Integer> blockToCounter = new IdentityHashMap<Block, Integer>();

    static {
        blockToCounter.put(Blocks.COAL_ORE, 0);
        blockToCounter.put(Blocks.LAPIS_ORE, 1);
        blockToCounter.put(Blocks.REDSTONE_ORE, 2);
        blockToCounter.put(Blocks.IRON_ORE, 3);
        blockToCounter.put(Blocks.GOLD_ORE, 4);
        blockToCounter.put(Blocks.EMERALD_ORE, 5);
        blockToCounter.put(Blocks.DIAMOND_ORE, 6);
        blockToCounter.put(Blocks.QUARTZ_ORE, 7);
        LootTableList.register(fishingLoot);
        LootTableList.register(commonDig);
        LootTableList.register(uncommonDig);
        LootTableList.register(rareDig);
        LootTableList.register(diggingLoot);
    }

    /**
     * Items given by Digging ground
     */
    private static ItemStack digLoot[] = {new ItemStack(Items.CLAY_BALL, 8), new ItemStack(Items.BOWL, 2), new ItemStack(Items.COAL, 4), new ItemStack(Items.PAINTING), new ItemStack(Items.STICK, 4),
            new ItemStack(Items.STRING, 2)};
    private static ItemStack digLoot1[] = {new ItemStack(Items.STONE_SWORD), new ItemStack(Items.STONE_SHOVEL), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_AXE)};
    private static ItemStack digLoot2[] = {new ItemStack(Items.SLIME_BALL, 2), new ItemStack(Items.REDSTONE, 8), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT)};
    private static ItemStack digLoot3[] = {new ItemStack(Items.DIAMOND)};
    /**
     * Internal ores list for Mining
     */
    private static Set<Block> ores = Sets.newIdentityHashSet();

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onBreak(PlayerEvent.BreakSpeed event) {
        ItemStack itemstack = event.getEntityPlayer().getHeldItemMainhand();
        IBlockState state = event.getState();
        Block block = state.getBlock();
        float speed = event.getOriginalSpeed();
        if (itemstack != null)
            if (oldSpeedDigging && itemstack.getItem() instanceof ItemSpade) {
                if (block instanceof BlockDirt || block instanceof BlockGravel) {
                    event.setNewSpeed(speed * 0.5F);
                }
            } else if (oldSpeedRedstone && itemstack.getItem() instanceof ItemPickaxe && block instanceof BlockRedstoneOre) {
                event.setNewSpeed(speed / 3F);
            }
        if (block instanceof BlockStone || block == Blocks.COBBLESTONE || block == Blocks.OBSIDIAN || block instanceof BlockOre || ores.contains(block)) {
            event.setNewSpeed(speed + (getSkill(event.getEntityPlayer(), 0) / 5));
        } else if (state.getMaterial() == Material.WOOD) {
            event.setNewSpeed(speed + getSkill(event.getEntityPlayer(), 3) / 5);
        }
    }

    /**
     * Track player deaths to reset values when appropriate,
     * and player final strikes on mobs to give bonus xp
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            if (resetClassOnDeath) {
                PlayerExtendedProperties.from((EntityPlayer) event.getEntityLiving()).setPlayerClass((byte) 0);
            }
            if (resetSkillOnDeath > 0.00F) {
                PlayerExtendedProperties.from((EntityPlayer) event.getEntityLiving()).takeSkillFraction(resetSkillOnDeath);
            }
        } else if (event.getEntityLiving() instanceof EntityMob && event.getSource().getEntity() instanceof EntityPlayer) {
            LevelUp.giveBonusFightingXP((EntityPlayer) event.getSource().getEntity());
        }
    }

    /**
     * Change fishing by adding some loots
     * Prevent flagged block placement
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onFishInteract(PlayerInteractEvent.RightClickItem event) {
        if (event.getResult() != Event.Result.DENY)
        {
            EntityFishHook hook = event.getEntityPlayer().fishEntity;
            if (hook != null && hook.caughtEntity == null && hook.ticksCatchable > 0) {//Not attached to some random stuff, and within the time frame for catching
                ItemStack loot = getFishingLoot(event.getWorld(), event.getEntityPlayer());
                if (loot != null) {
                    ItemStack stack = event.getEntityPlayer().inventory.getCurrentItem();
                    int i = stack.stackSize;
                    int j = stack.getItemDamage();
                    stack.damageItem(1, event.getEntityPlayer());
                    event.getEntityPlayer().swingArm(event.getHand());
                    event.getEntityPlayer().inventory.setInventorySlotContents(event.getEntityPlayer().inventory.currentItem, stack);
                    if (event.getEntityPlayer().capabilities.isCreativeMode) {
                        stack.stackSize = i;
                        if (stack.isItemStackDamageable()) {
                            stack.setItemDamage(j);
                        }
                    }
                    if (stack.stackSize <= 0) {
                        event.getEntityPlayer().inventory.setInventorySlotContents(event.getEntityPlayer().inventory.currentItem, null);
                        MinecraftForge.EVENT_BUS.post(new PlayerDestroyItemEvent(event.getEntityPlayer(), stack, event.getHand()));
                    }
                    if (!event.getEntityPlayer().isHandActive() && event.getEntityPlayer() instanceof EntityPlayerMP) {
                        ((EntityPlayerMP) event.getEntityPlayer()).sendContainerToPlayer(event.getEntityPlayer().inventoryContainer);
                    }
                    event.setResult(Event.Result.DENY);
                    if (!hook.worldObj.isRemote) {
                        EntityItem entityitem = new EntityItem(hook.worldObj, hook.posX, hook.posY, hook.posZ, loot.copy());
                        double d5 = hook.angler.posX - hook.posX;
                        double d6 = hook.angler.posY - hook.posY;
                        double d7 = hook.angler.posZ - hook.posZ;
                        double d8 = MathHelper.sqrt_double(d5 * d5 + d6 * d6 + d7 * d7);
                        double d9 = 0.1D;
                        entityitem.motionX = d5 * d9;
                        entityitem.motionY = d6 * d9 + MathHelper.sqrt_double(d8) * 0.08D;
                        entityitem.motionZ = d7 * d9;
                        hook.worldObj.spawnEntityInWorld(entityitem);
                        hook.angler.worldObj.spawnEntityInWorld(new EntityXPOrb(hook.angler.worldObj, hook.angler.posX, hook.angler.posY + 0.5D, hook.angler.posZ + 0.5D, event.getEntityPlayer().getRNG().nextInt(6) + 1));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void tryPlaceOre(PlayerInteractEvent.RightClickBlock event)
    {
        if(noPlaceDuplicate)
        {
            ItemStack stack = event.getEntityPlayer().inventory.getCurrentItem();
            if(stack != null && stack.hasTagCompound() && stack.getTagCompound().hasKey("NoPlacing")) {
                event.setUseItem(Event.Result.DENY);
            }
        }
    }

    @SubscribeEvent
    public void onHarvest(BlockEvent.HarvestDropsEvent event) {
        if (event.getHarvester() != null && !event.getWorld().isRemote) {
            int skill;
            IBlockState state = event.getState();
            Random random = event.getHarvester().getRNG();
            if (PlankCache.contains(state.getBlock(), state.getBlock().damageDropped(state))) {
                skill = getSkill(event.getHarvester(), 3);
                if (random.nextDouble() <= skill / 150D) {
                    ItemStack planks = PlankCache.getProduct(state.getBlock(), state.getBlock().damageDropped(state));
                    if (planks != null)
                        event.getDrops().add(PlankCache.getProduct(state.getBlock(), state.getBlock().damageDropped(state)).copy());
                }
                if (random.nextDouble() <= skill / 150D) {
                    event.getDrops().add(new ItemStack(Items.STICK, 2));
                }
            } else if (state.getBlock() instanceof BlockOre || state.getBlock() instanceof BlockRedstoneOre || ores.contains(state.getBlock())) {
                skill = getSkill(event.getHarvester(), 0);
                if (!blockToCounter.containsKey(state.getBlock())) {
                    blockToCounter.put(state.getBlock(), blockToCounter.size());
                }
                if (!event.isSilkTouching())
                    LevelUp.incrementOreCounter(event.getHarvester(), blockToCounter.get(state.getBlock()));
                if (random.nextDouble() <= skill / 200D) {
                    boolean foundBlock = false;
                    ItemStack newOre = null;
                    for (ItemStack stack : event.getDrops()) {
                        if (stack != null && state.getBlock() == Block.getBlockFromItem(stack.getItem())) {
                            writeNoPlacing(stack);
                            newOre = stack.copy();
                            foundBlock = true;
                            break;
                        }
                    }
                    if(newOre != null)
                        event.getDrops().add(newOre);
                    if (!foundBlock) {
                        Item ID = state.getBlock().getItemDropped(state, random, event.getFortuneLevel());
                        if (ID != null) {
                            int qutity = state.getBlock().quantityDropped(state, event.getFortuneLevel(), random);
                            if (qutity > 0)
                                event.getDrops().add(new ItemStack(ID, qutity, state.getBlock().damageDropped(state)));
                        }
                    }
                }
                else if (LevelUp.oreNoPlace) {
                    for (ItemStack stack : event.getDrops()) {
                        if (stack != null && state.getBlock() == Block.getBlockFromItem(stack.getItem())) {
                            writeNoPlacing(stack);
                            break;
                        }
                    }
                }
            } else if (!event.isSilkTouching()) {
                skill = getSkill(event.getHarvester(), 11);
                if(state.getMaterial() == Material.GROUND) {
                    if (random.nextFloat() <= skill / 200F) {
                        ItemStack loot = getDigLoot(event.getWorld(), event.getHarvester());
                        if (loot != null) {
                            removeFromList(event.getDrops(), state.getBlock());
                            ItemStack toDrop = loot.copy();
                            event.getDrops().add(toDrop);
                        }
                    }
                }
                else if(state.getBlock() instanceof BlockGravel) {
                    if (random.nextInt(10) < skill / 5) {
                        removeFromList(event.getDrops(), state.getBlock());
                        event.getDrops().add(new ItemStack(Items.FLINT));
                    }
                }
            }
        }
    }

    private ItemStack getDigLoot(World world, EntityPlayer player) {
        if(!world.isRemote) {
            LootContext.Builder build = new LootContext.Builder((WorldServer) world).withPlayer(player);
            build.withLuck((float) EnchantmentHelper.getMaxEnchantmentLevel(Enchantment.getEnchantmentByLocation("fortune"), player) + player.getLuck());
            return world.getLootTableManager().getLootTableFromLocation(diggingLoot).generateLootForPools(player.getRNG(), build.build()).get(0);
        }
        return null;
    }

    private void removeFromList(List<ItemStack> drops, Block block) {
        Iterator<ItemStack> itr = drops.iterator();
        while (itr.hasNext()) {
            ItemStack drop = itr.next();
            if (drop != null && block == Block.getBlockFromItem(drop.getItem())) {
                itr.remove();
            }
        }
    }

    /**
     * Convenience method to write the "no-placement" flag onto a block
     */
    private void writeNoPlacing(ItemStack toDrop) {
        if (!noPlaceDuplicate)
            return;
        NBTTagCompound tagCompound = toDrop.getTagCompound();
        if (tagCompound == null)
            tagCompound = new NBTTagCompound();
        tagCompound.setBoolean("NoPlacing", true);
        toDrop.setTagCompound(tagCompound);
    }

    /**
     * Converts a log block into craftable planks, if possible
     *
     * @return default planks if no crafting against the log is possible
     */
    private ItemStack getPlanks(EntityPlayer player, ItemStack drop) {
        InventoryCrafting craft = new ContainerPlayer(player.inventory, !player.worldObj.isRemote, player).craftMatrix;
        craft.setInventorySlotContents(1, drop);
        return CraftingManager.getInstance().findMatchingRecipe(craft, player.worldObj);
    }

    /**
     * Adds additional drops for Farming when breaking crops
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!event.getWorld().isRemote && event.getPlayer() != null) {
            if (event.getState().getBlock() instanceof BlockCrops || event.getState().getBlock() instanceof BlockStem) {//BlockNetherWart ?
                if(!((IGrowable) event.getState().getBlock()).canGrow(event.getWorld(), event.getPos(), event.getState(), false)) {//Fully grown
                    doCropDrops(event);
                }
            }else if(event.getState().getBlock() instanceof BlockMelon){
                doCropDrops(event);
            }
        }
    }

    private void doCropDrops(BlockEvent.BreakEvent event){
        Random random = event.getPlayer().getRNG();
        int skill = getSkill(event.getPlayer(), 9);
        if (random.nextInt(10) < skill / 5) {
            Item ID = event.getState().getBlock().getItemDropped(event.getState(), random, 0);
            if(ID == null){
                if(event.getState().getBlock() == Blocks.PUMPKIN_STEM){
                    ID = Items.PUMPKIN_SEEDS;
                }else if(event.getState().getBlock() == Blocks.MELON_STEM){
                    ID = Items.MELON_SEEDS;
                }
            }
            if (ID != null)
                event.getWorld().spawnEntityInWorld(new EntityItem(event.getWorld(), event.getPos().getX(), event.getPos().getY(), event.getPos().getZ(), new ItemStack(ID, 1, event.getState().getBlock().damageDropped(event.getState()))));
        }
    }

    /**
     * Register furnace capability
     */
    @SubscribeEvent
    public void registerFurnaceCap(AttachCapabilitiesEvent.TileEntity evt) {
        if(evt.getTileEntity() instanceof TileEntityFurnace) {
            final TileEntityFurnace furnace = (TileEntityFurnace)evt.getTileEntity();
            evt.addCapability(ClassBonus.FURNACE_LOCATION, new ICapabilitySerializable<NBTTagCompound>() {
                IProcessor instance = new CapabilityFurnace(furnace);

                @Override
                public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
                    return capability == LevelUpCapability.MACHINE_PROCESSING;
                }

                @Override
                public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
                    return capability == LevelUpCapability.MACHINE_PROCESSING ? LevelUpCapability.MACHINE_PROCESSING.<T>cast(instance) : null;
                }

                @Override
                public NBTTagCompound serializeNBT() {
                    return ((NBTTagCompound) LevelUpCapability.MACHINE_PROCESSING.getStorage().writeNBT(LevelUpCapability.MACHINE_PROCESSING, instance, null));
                }

                @Override
                public void deserializeNBT(NBTTagCompound tag) {
                    LevelUpCapability.MACHINE_PROCESSING.getStorage().readNBT(LevelUpCapability.MACHINE_PROCESSING, instance, null, tag);
                }
            });
        }
        else if(evt.getTileEntity() instanceof TileEntityBrewingStand) {
            final TileEntityBrewingStand stand = (TileEntityBrewingStand)evt.getTileEntity();
            evt.addCapability(ClassBonus.FURNACE_LOCATION, new ICapabilitySerializable<NBTTagCompound>() {
                IProcessor instance = new CapabilityBrewingStand(stand);

                @Override
                public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
                    return capability == LevelUpCapability.MACHINE_PROCESSING;
                }

                @Override
                public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
                    return capability == LevelUpCapability.MACHINE_PROCESSING ? LevelUpCapability.MACHINE_PROCESSING.<T>cast(instance) : null;
                }

                @Override
                public NBTTagCompound serializeNBT() {
                    return ((NBTTagCompound) LevelUpCapability.MACHINE_PROCESSING.getStorage().writeNBT(LevelUpCapability.MACHINE_PROCESSING, instance, null));
                }

                @Override
                public void deserializeNBT(NBTTagCompound tag) {
                    LevelUpCapability.MACHINE_PROCESSING.getStorage().readNBT(LevelUpCapability.MACHINE_PROCESSING, instance, null, tag);
                }
            });
        }
    }

    /**
     * Register base skill data to players
     */
    @SubscribeEvent
    public void onEntityConstruct(AttachCapabilitiesEvent.Entity evt)
    {
        if(evt.getEntity() instanceof EntityPlayer) {
            evt.addCapability(ClassBonus.SKILL_LOCATION, new ICapabilitySerializable<NBTTagCompound>() {
                IPlayerClass instance = LevelUpCapability.CAPABILITY_CLASS.getDefaultInstance();

                @Override
                public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
                    return capability == LevelUpCapability.CAPABILITY_CLASS;
                }

                @Override
                public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
                    return capability == LevelUpCapability.CAPABILITY_CLASS ? LevelUpCapability.CAPABILITY_CLASS.<T>cast(instance) : null;
                }

                @Override
                public NBTTagCompound serializeNBT() {
                    return ((NBTTagCompound) LevelUpCapability.CAPABILITY_CLASS.getStorage().writeNBT(LevelUpCapability.CAPABILITY_CLASS, instance, null));
                }

                @Override
                public void deserializeNBT(NBTTagCompound tag) {
                    LevelUpCapability.CAPABILITY_CLASS.getStorage().readNBT(LevelUpCapability.CAPABILITY_CLASS, instance, null, tag);
                }
            });
        }
    }

    /**
     * Copy skill data when needed
     */
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !resetClassOnDeath || resetSkillOnDeath < 1.00F) {
            NBTTagCompound data = new NBTTagCompound();
            PlayerExtendedProperties.from(event.getOriginal()).saveNBTData(data);
            PlayerExtendedProperties.from(event.getEntityPlayer()).loadNBTData(data);
        }
    }

    /**
     * Keep track of registered ores blocks, for mining xp compatibility
     */
    public static void registerOres() {
        for(String ore : OreDictionary.getOreNames()) {
            if(ore.startsWith("ore")) {
                if(OreDictionary.getOres(ore) != null && !OreDictionary.getOres(ore).isEmpty()) {
                    for(ItemStack stack : OreDictionary.getOres(ore)) {
                        if(stack.getItem() instanceof ItemBlock) {
                            Block block = ((ItemBlock)stack.getItem()).getBlock();
                            if(!(block instanceof BlockOre) && !(block instanceof BlockRedstoneOre)) {
                                if(!ores.contains(block))
                                    ores.add(block);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Helper to get a random slot value for the fish drop list
     *
     * @return null if no drop is required
     */
    public static ItemStack getFishingLoot(World world, EntityPlayer player) {
        if(!world.isRemote) {
            if (player.getRNG().nextDouble() <= (getSkill(player, 10) / 5) * 0.05D) {
                LootContext.Builder build = new LootContext.Builder((WorldServer) world);
                build.withLuck((float) EnchantmentHelper.getLuckOfSeaModifier(player) + player.getLuck());
                return world.getLootTableManager().getLootTableFromLocation(fishingLoot).generateLootForPools(player.getRNG(), build.build()).get(0);
            }
        }
        return null;
    }

    /**
     * Helper to retrieve skill points from the index
     */
    public static int getSkill(EntityPlayer player, int id) {
        return PlayerExtendedProperties.getSkillFromIndex(player, id);
    }
}
