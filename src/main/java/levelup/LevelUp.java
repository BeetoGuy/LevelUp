package levelup;

import levelup.api.IProcessor;
import levelup.api.LevelUpAPI;
import levelup.capabilities.LevelUpCapability;
import levelup.event.BowEventHandler;
import levelup.event.FMLEventHandler;
import levelup.event.FightEventHandler;
import levelup.event.PlayerEventHandler;
import levelup.item.ItemRespecBook;
import levelup.minetweaker.MineTweaker;
import levelup.player.IPlayerClass;
import levelup.player.PlayerExtendedProperties;
import levelup.proxy.SkillProxy;
import levelup.util.CraftingBlacklist;
import levelup.util.PlankCache;
import levelup.util.UtilRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockPlanks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod(modid = LevelUp.ID, name = "LevelUp!", version = "${version}", guiFactory = "levelup.ConfigLevelUp")
public final class LevelUp {
    public final static String ID = "levelup";
    @Instance(value = ID)
    public static LevelUp instance;
    @SidedProxy(clientSide = "levelup.proxy.SkillClientProxy", serverSide = "levelup.proxy.SkillProxy")
    public static SkillProxy proxy;
    private Property[] clientProperties;
    private Property[] serverProperties;
    private static Item xpTalisman, respecBook;
    private static Map<Object, Integer> towItems;
    private static List[] tiers;
    private static Configuration config;
    public static boolean allowHUD = true, renderTopLeft = true, renderExpBar = true, changeFOV = true;
    private static boolean bonusMiningXP = true, bonusCraftingXP = true, bonusFightingXP = true, oreMiningXP = true;
    public static FMLEventChannel initChannel, skillChannel, classChannel, configChannel;

    @EventHandler
    public void load(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(BowEventHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(FightEventHandler.INSTANCE);
        SkillPacketHandler sk = new SkillPacketHandler();
        initChannel = NetworkRegistry.INSTANCE.newEventDrivenChannel(SkillPacketHandler.CHAN[0]);
        initChannel.register(sk);
        classChannel = NetworkRegistry.INSTANCE.newEventDrivenChannel(SkillPacketHandler.CHAN[1]);
        classChannel.register(sk);
        skillChannel = NetworkRegistry.INSTANCE.newEventDrivenChannel(SkillPacketHandler.CHAN[2]);
        skillChannel.register(sk);
        configChannel = NetworkRegistry.INSTANCE.newEventDrivenChannel(SkillPacketHandler.CHAN[3]);
        configChannel.register(sk);
        proxy.registerGui();
        if(xpTalisman!=null)
            proxy.register(xpTalisman, "levelup:xp_talisman");
        if(respecBook!=null)
            proxy.register(respecBook, "levelup:respec_book");
        UtilRegistry.init();
        if(Loader.isModLoaded("MineTweaker3"))
            MineTweaker.init();

        for (BlockPlanks.EnumType type : BlockPlanks.EnumType.values()) {
            ItemStack log = null;
            ItemStack plank = new ItemStack(Blocks.PLANKS, 2, type.getMetadata());
            if (type.getMetadata() < 4) {
                log = new ItemStack(Blocks.LOG, 1, type.getMetadata());

            } else {
                log = new ItemStack(Blocks.LOG2, 1, type.getMetadata() - 4);
            }
            Block block = ((ItemBlock)log.getItem()).getBlock();
            PlankCache.addBlock(block, log.getMetadata(), plank);
        }
        List<ItemStack> logs = OreDictionary.getOres("logWood");
        for(ItemStack log : logs) {
            if(log.getItem() != null && log.getItem() instanceof ItemBlock) {
                Block block = ((ItemBlock) log.getItem()).getBlock();
                if (!block.getRegistryName().getResourceDomain().equals("minecraft")) {
                    if (log.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
                        for (int i = 0; i < 4; i++) {
                            ItemStack planks = getRecipeOutput(new ItemStack(log.getItem(), 1, i));
                            if (planks != null) {
                                ItemStack cache = new ItemStack(planks.getItem(), 2, planks.getMetadata());
                                PlankCache.addBlock(block, i, cache);
                            }
                        }
                    } else {
                        ItemStack planks = getRecipeOutput(log);
                        if (planks != null) {
                            ItemStack cache = new ItemStack(planks.getItem(), 2, planks.getMetadata());
                            PlankCache.addBlock(block, log.getMetadata(), cache);
                        }
                    }
                }
            }
        }
        PlayerEventHandler.registerOres();
    }

    private static ItemStack getRecipeOutput(ItemStack input) {
        List<IRecipe> recipes = CraftingManager.getInstance().getRecipeList();
        for (IRecipe recipe : recipes) {
            if (recipe instanceof ShapelessRecipes) {
                ShapelessRecipes shapeless = (ShapelessRecipes) recipe;
                if (shapeless.recipeItems.size() == 1 && shapeless.recipeItems.get(0).isItemEqual(input)) {
                    return shapeless.getRecipeOutput();
                }
            } else if (recipe instanceof ShapelessOreRecipe) {
                ShapelessOreRecipe shapeless = (ShapelessOreRecipe) recipe;
                if(shapeless.getRecipeSize() == 1) {
                    if(shapeless.getInput().get(0) instanceof ItemStack) {
                        if(((ItemStack)shapeless.getInput().get(0)).isItemEqual(input)) {
                            return shapeless.getRecipeOutput();
                        }
                    }
                }
            }
        }
        return null;
    }

    @EventHandler
    public void load(FMLPreInitializationEvent event) {
        config = new Configuration(event.getSuggestedConfigurationFile());
        config.addCustomCategoryComment("HUD", "Entirely client side. No need to sync.");
        initClientProperties();
        config.addCustomCategoryComment("Items", "Need to be manually synced to the client on a dedicated server");
        config.addCustomCategoryComment("Cheats", "Will be automatically synced to the client on a dedicated server");
        initServerProperties();
        boolean talismanEnabled = config.getBoolean("Enable Talisman", "Items", true, "Enable item and related recipes");
        boolean bookEnabled = config.getBoolean("Enable Unlearning Book", "Items", true, "Enable item and related recipe");
        boolean legacyRecipes = config.getBoolean("Enable Recipes", "Items", true, "Enable legacy pumpkin and flint recipes");
        useServerProperties();
        CapabilityManager.INSTANCE.register(IPlayerClass.class, new LevelUpCapability.CapabilityPlayerClass<IPlayerClass>(), PlayerExtendedProperties.class);
        CapabilityManager.INSTANCE.register(IProcessor.class, new LevelUpCapability.CapabilityProcessorClass<IProcessor>(), LevelUpCapability.CapabilityProcessorDefault.class);
        List<String> blackList = Arrays.asList(config.getStringList("Crops for farming", "BlackList", new String[]{""}, "That won't be affected by farming growth skill, uses internal block name. No sync to client needed."));
        FMLEventHandler.INSTANCE.addCropsToBlackList(blackList);
        if (config.hasChanged())
            config.save();
        if (talismanEnabled) {
            towItems = new HashMap<Object, Integer>();
            xpTalisman = new Item().setUnlocalizedName("xpTalisman").setCreativeTab(CreativeTabs.TOOLS);
            GameRegistry.register(xpTalisman.setRegistryName("xp_talisman"));
            GameRegistry.addRecipe(new ShapedOreRecipe(xpTalisman, "GG ", " R ", " GG", 'G', "ingotGold", 'R', "dustRedstone"));
            initTalismanProperty("logWood", 2);
            initTalismanProperty(Items.COAL, 2);
            initTalismanProperty("ingotBrick", 4);
            initTalismanProperty(Items.BOOK, 4);
            initTalismanProperty("oreIron", 8);
            initTalismanProperty("gemLapis", 8);
            initTalismanProperty("dustRedstone", 8);
            initTalismanProperty(Items.BREAD, 10);
            initTalismanProperty(Items.MELON, 10);
            initTalismanProperty(Item.getItemFromBlock(Blocks.PUMPKIN), 10);
            initTalismanProperty(Items.COOKED_PORKCHOP, 12);
            initTalismanProperty(Items.COOKED_BEEF, 12);
            initTalismanProperty(Items.COOKED_CHICKEN, 12);
            initTalismanProperty(Items.COOKED_FISH, 12);
            initTalismanProperty(Items.COOKED_MUTTON, 12);
            initTalismanProperty(Items.COOKED_RABBIT, 12);
            initTalismanProperty("ingotIron", 16);
            initTalismanProperty("oreGold", 20);
            initTalismanProperty("ingotGold", 24);
            initTalismanProperty("gemDiamond", 40);
        }
        if (bookEnabled) {
            respecBook = new ItemRespecBook().setUnlocalizedName("respecBook").setCreativeTab(CreativeTabs.TOOLS);
            GameRegistry.register(respecBook.setRegistryName("respec_book"));
            ItemStack output = new ItemStack(respecBook);
            if (config.getBoolean("unlearning Book Reset Class", "Cheats", false, "Should unlearning book also remove class")) {
                output.setItemDamage(1);
            }
            GameRegistry.addRecipe(new ShapedOreRecipe(output, "OEO", "DBD", "ODO", 'O', "obsidian", 'D', "dye",
                    'E', "enderpearl", 'B', Items.BOOK));
        }
        if (legacyRecipes) {
            GameRegistry.addShapelessRecipe(new ItemStack(Items.PUMPKIN_SEEDS, 4), Blocks.PUMPKIN);
            GameRegistry.addRecipe(new ItemStack(Blocks.GRAVEL, 4), "##", "##", '#', Items.FLINT);
        }
        if (event.getSourceFile().getName().endsWith(".jar")) {
            proxy.tryUseMUD();
        }
        MinecraftForge.EVENT_BUS.register(FMLEventHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new PlayerEventHandler());
    }

    private void initTalismanProperty(Object item, int value) {
        towItems.put(item, value);
        GameRegistry.addRecipe(new ShapelessOreRecipe(xpTalisman, xpTalisman, item));
    }

    private void initClientProperties() {
        clientProperties = new Property[]{
                config.get("HUD", "allow HUD", allowHUD, "If anything should be rendered on screen at all.").setRequiresMcRestart(true),
                config.get("HUD", "render HUD on Top Left", renderTopLeft, "Should the player class be displayed in the top left corner."),
                config.get("HUD", "render HUD on Exp Bar", renderExpBar, "Should available skill points be displayed on the experience bar."),
                config.get("FOV", "speed based", changeFOV, "Should FOV change based on player speed from athletics / sneak skills." )};
        allowHUD = clientProperties[0].getBoolean();
        renderTopLeft = clientProperties[1].getBoolean();
        renderExpBar = clientProperties[2].getBoolean();
        changeFOV = clientProperties[3].getBoolean();
    }

    private void initServerProperties() {
        String cat = "Cheats";
        String limitedBonus = "This is a bonus related to a few classes";
        serverProperties = new Property[]{
                config.get(cat, "Max points per skill", ClassBonus.getMaxSkillPoints(), "Minimum is 1"),
                config.get(cat, "Bonus points for classes", ClassBonus.getBonusPoints(), "Points given when choosing a class, allocated automatically.\n Minimum is 0, Maximum is max points per skill times 2"),
                config.get(cat, "Xp gain per level", PlayerEventHandler.xpPerLevel, "Minimum is 0"),
                config.get(cat, "Skill points lost on death", (int) PlayerEventHandler.resetSkillOnDeath * 100, "How much skill points are lost on death, in percent.").setMinValue(0).setMaxValue(100),
                config.get(cat, "Use old speed for dirt and gravel digging", PlayerEventHandler.oldSpeedDigging),
                config.get(cat, "Use old speed for redstone breaking", PlayerEventHandler.oldSpeedRedstone, "Makes the redstone ore mining efficient"),
                config.get(cat, "Reset player class on death", PlayerEventHandler.resetClassOnDeath, "Do the player lose the class he choose on death ?"),
                config.get(cat, "Prevent duplicated ores placing", PlayerEventHandler.noPlaceDuplicate, "Some skill duplicate ores, this prevent infinite duplication by replacing"),
                config.get(cat, "Add Bonus XP on Craft", bonusCraftingXP, limitedBonus),
                config.get(cat, "Add Bonus XP on Mining", bonusMiningXP, limitedBonus),
                config.get(cat, "Add XP on Crafting some items", true, "This is a global bonus, limited to a few craftable items"),
                config.get(cat, "Add XP on Mining some ore", oreMiningXP, "This is a global bonus, limited to a few ores"),
                config.get(cat, "Add Bonus XP on Fighting", bonusFightingXP, limitedBonus),
                config.get(cat, "Furnace ejects smelting bonus", LevelUpAPI.furnaceEjection, "Disabling this will cause doubled furnace items to be added to the result slot instead of being ejected")};
    }

    public void useServerProperties() {
        ClassBonus.setSkillMax(serverProperties[0].getInt());
        ClassBonus.setBonusPoints(serverProperties[1].getInt());
        double opt = serverProperties[2].getDouble();
        if (opt >= 0.0D)
            PlayerEventHandler.xpPerLevel = opt <= ClassBonus.getMaxSkillPoints() ? opt : ClassBonus.getMaxSkillPoints();
        PlayerEventHandler.resetSkillOnDeath = (float) serverProperties[3].getInt() / 100.00F;
        PlayerEventHandler.oldSpeedDigging = serverProperties[4].getBoolean();
        PlayerEventHandler.oldSpeedRedstone = serverProperties[5].getBoolean();
        PlayerEventHandler.resetClassOnDeath = serverProperties[6].getBoolean();
        PlayerEventHandler.noPlaceDuplicate = serverProperties[7].getBoolean();
        bonusCraftingXP = serverProperties[8].getBoolean();
        bonusMiningXP = serverProperties[9].getBoolean();
        oreMiningXP = serverProperties[11].getBoolean();
        bonusFightingXP = serverProperties[12].getBoolean();
        LevelUpAPI.furnaceEjection = serverProperties[13].getBoolean();
        if (serverProperties[10].getBoolean()) {
            List<Item> ingrTier1, ingrTier2, ingrTier3, ingrTier4;
            ingrTier1 = Arrays.asList(Items.STICK, Items.LEATHER, Item.getItemFromBlock(Blocks.STONE));
            ingrTier2 = Arrays.asList(Items.IRON_INGOT, Items.GOLD_INGOT, Items.PAPER, Items.SLIME_BALL);
            ingrTier3 = Arrays.asList(Items.REDSTONE, Items.GLOWSTONE_DUST, Items.ENDER_PEARL);
            ingrTier4 = Arrays.asList(Items.DIAMOND);
            tiers = new List[]{ingrTier1, ingrTier2, ingrTier3, ingrTier4};
        }
    }

    public Property[] getServerProperties() {
        return serverProperties;
    }

    public boolean[] getClientProperties() {
        boolean[] result = new boolean[clientProperties.length];
        for (int i = 0; i < clientProperties.length; i++) {
            result[i] = clientProperties[i].getBoolean();
        }
        return result;
    }

    public void refreshValues(boolean[] values) {
        if (values.length == clientProperties.length) {
            LevelUp.allowHUD = values[0];
            LevelUp.renderTopLeft = values[1];
            LevelUp.renderExpBar = values[2];
            LevelUp.changeFOV = values[3];
            for (int i = 0; i < values.length; i++) {
                clientProperties[i].set(values[i]);
            }
            config.save();
        }
    }

    public static void giveBonusFightingXP(EntityPlayer player) {
        if (bonusFightingXP) {
            byte pClass = PlayerExtendedProperties.getPlayerClass(player);
            if (pClass == 2 || pClass == 5 || pClass == 8 || pClass == 11) {
                player.addExperience(2);
            }
        }
    }

    public static void giveBonusCraftingXP(EntityPlayer player) {
        if (bonusCraftingXP) {
            byte pClass = PlayerExtendedProperties.getPlayerClass(player);
            if (pClass == 3 || pClass == 6 || pClass == 9 || pClass == 12) {
                runBonusCounting(player, 1);
            }
        }
    }

    public static void giveBonusMiningXP(EntityPlayer player) {
        if (bonusMiningXP) {
            byte pClass = PlayerExtendedProperties.getPlayerClass(player);
            if (pClass == 1 || pClass == 4 || pClass == 7 || pClass == 10) {
                runBonusCounting(player, 0);
            }
        }
    }

    private static void runBonusCounting(EntityPlayer player, int type) {
        Map<String, int[]> counters = PlayerExtendedProperties.getCounterMap(player);
        int[] bonus = counters.get(PlayerExtendedProperties.counters[2]);
        if (bonus == null || bonus.length == 0) {
            bonus = new int[]{0, 0, 0};
        }
        if (bonus[type] < 4) {
            bonus[type]++;
        } else {
            bonus[type] = 0;
            player.addExperience(2);
        }
        counters.put(PlayerExtendedProperties.counters[2], bonus);
    }

    public static void giveCraftingXP(EntityPlayer player, ItemStack itemstack) {
        if (tiers != null)
            for (int i = 0; i < tiers.length; i++) {
                if (tiers[i].contains(itemstack.getItem()) && !isUncraftable(itemstack)) {
                    incrementCraftCounter(player, i);
                }
            }
    }

    private static void incrementCraftCounter(EntityPlayer player, int i) {
        Map<String, int[]> counters = PlayerExtendedProperties.getCounterMap(player);
        int[] craft = counters.get(PlayerExtendedProperties.counters[1]);
        if (craft.length <= i) {
            int[] craftnew = new int[i + 1];
            System.arraycopy(craft, 0, craftnew, 0, craft.length);
            counters.put(PlayerExtendedProperties.counters[1], craftnew);
            craft = craftnew;
        }
        craft[i]++;
        float f = (float) Math.pow(2D, 3 - i);
        boolean flag;
        for (flag = false; f <= craft[i]; f += 0.5F) {
            player.addExperience(1);
            flag = true;
        }
        if (flag) {
            craft[i] = 0;
        }
        counters.put(PlayerExtendedProperties.counters[1], craft);
    }

    public static void incrementOreCounter(EntityPlayer player, int i) {
        if (oreMiningXP) {
            Map<String, int[]> counters = PlayerExtendedProperties.getCounterMap(player);
            int[] ore = counters.get(PlayerExtendedProperties.counters[0]);
            if (ore.length <= i) {
                int[] orenew = new int[i + 1];
                System.arraycopy(ore, 0, orenew, 0, ore.length);
                counters.put(PlayerExtendedProperties.counters[0], orenew);
                ore = orenew;
            }
            ore[i]++;
            float f = (float) Math.pow(2D, 3 - i) / 2.0F;
            boolean flag;
            for (flag = false; f <= ore[i]; f += 0.5F) {
                player.addExperience(1);
                flag = true;
            }
            if (flag) {
                ore[i] = 0;
            }
            counters.put(PlayerExtendedProperties.counters[0], ore);
        }
        giveBonusMiningXP(player);
    }

    public static boolean isTalismanRecipe(IInventory iinventory) {
        if (xpTalisman != null)
            for (int i = 0; i < iinventory.getSizeInventory(); i++) {
                if (iinventory.getStackInSlot(i) != null && iinventory.getStackInSlot(i).getItem() == xpTalisman) {
                    return true;
                }
            }
        return false;
    }

    public static void takenFromCrafting(EntityPlayer player, ItemStack itemstack, IInventory iinventory) {
        if (isTalismanRecipe(iinventory)) {
            for (int i = 0; i < iinventory.getSizeInventory(); i++) {
                ItemStack itemstack1 = iinventory.getStackInSlot(i);
                if (itemstack1 != null) {
                    String oreDict = containsOreDictEntry(itemstack1);
                    if(oreDict != null) {
                        player.addExperience((int)Math.floor(itemstack1.stackSize * towItems.get(containsOreDictEntry(itemstack1)) / 4D));
                        iinventory.getStackInSlot(i).stackSize = 0;
                    }
                    else if (towItems.containsKey(itemstack1.getItem())) {
                        player.addExperience((int) Math.floor(itemstack1.stackSize * towItems.get(itemstack1.getItem()) / 4D));
                        iinventory.getStackInSlot(i).stackSize = 0;
                    }
                }
            }
        } else {
            for (int j = 0; j < iinventory.getSizeInventory(); j++) {
                ItemStack itemstack2 = iinventory.getStackInSlot(j);
                if (itemstack2 != null && !isUncraftable(itemstack)) {
                    giveCraftingXP(player, itemstack2);
                    giveBonusCraftingXP(player);
                }
            }
        }
    }

    private static String containsOreDictEntry(ItemStack stack) {
        String[] toCheck = {"logWood", "ingotIron", "ingotGold", "ingotBrick", "gemDiamond", "oreIron", "oreGold", "dustRedstone", "gemLapis"};
        for(String entry : toCheck) {
            if(oreDictMatches(stack, OreDictionary.getOres(entry)))
                return entry;
        }
        return null;
    }

    private static boolean oreDictMatches(ItemStack stack, List<ItemStack> oreDict) {
        for(ItemStack ore : oreDict) {
            if(ore.getItemDamage() == OreDictionary.WILDCARD_VALUE && ore.getItem() == stack.getItem())
                return true;
            else if(ItemStack.areItemsEqual(stack, ore))
                return true;

        }
        return false;
    }

    private static boolean isUncraftable(ItemStack stack) {
        return CraftingBlacklist.contains(stack);//item == Item.getItemFromBlock(Blocks.HAY_BLOCK) || item == Item.getItemFromBlock(Blocks.GOLD_BLOCK) || item == Item.getItemFromBlock(Blocks.IRON_BLOCK) || item == Item.getItemFromBlock(Blocks.DIAMOND_BLOCK);
    }

    private static boolean isUncraftable(Item item) {
        return CraftingBlacklist.contains(item);
    }
}
