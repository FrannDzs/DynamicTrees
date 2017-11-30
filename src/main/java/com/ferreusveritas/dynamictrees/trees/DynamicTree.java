package com.ferreusveritas.dynamictrees.trees;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.ferreusveritas.dynamictrees.ConfigHandler;
import com.ferreusveritas.dynamictrees.DynamicTrees;
import com.ferreusveritas.dynamictrees.VanillaTreeData;
import com.ferreusveritas.dynamictrees.api.IBottomListener;
import com.ferreusveritas.dynamictrees.api.TreeHelper;
import com.ferreusveritas.dynamictrees.api.TreeRegistry;
import com.ferreusveritas.dynamictrees.api.backport.BlockAndMeta;
import com.ferreusveritas.dynamictrees.api.backport.BlockPos;
import com.ferreusveritas.dynamictrees.api.backport.EnumFacing;
import com.ferreusveritas.dynamictrees.api.backport.EnumHand;
import com.ferreusveritas.dynamictrees.api.backport.IBlockState;
import com.ferreusveritas.dynamictrees.api.cells.Cells;
import com.ferreusveritas.dynamictrees.api.cells.ICell;
import com.ferreusveritas.dynamictrees.api.cells.ICellSolver;
import com.ferreusveritas.dynamictrees.api.network.GrowSignal;
import com.ferreusveritas.dynamictrees.api.substances.ISubstanceEffect;
import com.ferreusveritas.dynamictrees.api.substances.ISubstanceEffectProvider;
import com.ferreusveritas.dynamictrees.api.treedata.IBiomeSuitabilityDecider;
import com.ferreusveritas.dynamictrees.api.treedata.ITreePart;
import com.ferreusveritas.dynamictrees.blocks.BlockBonsaiPot;
import com.ferreusveritas.dynamictrees.blocks.BlockBranch;
import com.ferreusveritas.dynamictrees.blocks.BlockDynamicSapling;
import com.ferreusveritas.dynamictrees.blocks.BlockDynamicLeaves;
import com.ferreusveritas.dynamictrees.blocks.BlockRootyDirt;
import com.ferreusveritas.dynamictrees.entities.EntityLingeringEffector;
import com.ferreusveritas.dynamictrees.inspectors.NodeFruit;
import com.ferreusveritas.dynamictrees.items.Seed;
import com.ferreusveritas.dynamictrees.potion.SubstanceFertilize;
import com.ferreusveritas.dynamictrees.special.BottomListenerDropItems;
import com.ferreusveritas.dynamictrees.util.CompatHelper;
import com.ferreusveritas.dynamictrees.util.CoordUtils;
import com.ferreusveritas.dynamictrees.util.GameRegistry;
import com.ferreusveritas.dynamictrees.util.MathHelper;
import com.ferreusveritas.dynamictrees.util.SimpleVoxmap;
import com.ferreusveritas.dynamictrees.worldgen.JoCode;
import com.ferreusveritas.dynamictrees.worldgen.TreeCodeStore;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.ColorizerFoliage;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;

/**
* All data related to a tree species.
* 
* @author ferreusveritas
*/
public class DynamicTree {
	
	/** Simple name of the tree e.g. "oak" */
	private String name;
	/** ModID of mod registering this tree */
	private String modId;
	
	//Branches
	/** The dynamic branch used by this tree */
	private BlockBranch dynamicBranch;
	/** The primitive(vanilla) log to base the texture, drops, and other behavior from */
	private IBlockState primitiveLog;
	/** cached ItemStack of primitive logs(what is returned when wood is harvested) */
	private ItemStack primitiveLogItemStack;
	/** The primitive(vanilla) sapling for this type of tree. Used for crafting recipes */
	private IBlockState primitiveSapling;
	/** How quickly the branch thickens on it's own without branch merges [default = 0.3] */
	private float tapering = 0.3f;
	/** The probability that the direction decider will choose up out of the other possible direction weights [default = 2] */
	private int upProbability = 2;
	/** Number of blocks high we have to be before a branch is allowed to form [default = 3](Just high enough to walk under)*/
	private int lowestBranchHeight = 3;
	/** Number of times a grow signal retries before failing. Affects growing speed [default = 0] */
	private int retries = 0;
	/** Ideal signal energy. Greatest possible height that branches can reach from the root node [default = 16] */
	private float signalEnergy = 16.0f;
	/** The stick that is returned when a whole log can't be dropped */
	private ItemStack stick;
	/** Weather the branch can support cocoa pods on it's surface [default = false] */
	public boolean canSupportCocoa = false;
	
	
	//Dirt
	/** Ideal growth rate [default = 1.0]*/
	private float growthRate = 1.0f;
	/** Ideal soil longevity [default = 8]*/
	private int soilLongevity = 8;
	
	
	//Leaves
	/** The dynamic leaves used by this tree */
	private BlockDynamicLeaves dynamicLeaves;
	/** A dynamic leaves block needs a subblock number to specify which subblock we are working with **/
	private int leavesSubBlock;
	/** Maximum amount of leaves in a stack before the bottom-most leaf block dies [default = 4] **/
	private int smotherLeavesMax = 4;
	/** Minimum amount of light necessary for a leaves block to be created. **/
	private int lightRequirement = 13;
	/** A list of special effects reserved for leaves on the bottom of a stack **/
	private ArrayList<IBottomListener> bottomSpecials = new ArrayList<IBottomListener>(4);
	/** The default hydration level of a newly created leaf block [default = 4]**/
	protected byte defaultHydration = 4;
	/** The primitive(vanilla) leaves are used for many purposes including rendering, drops, and some other basic behavior. */
	private IBlockState primitiveLeaves;
	/** cached ItemStack of primitive leaves(what is returned when leaves are sheared) */
	private ItemStack primitiveLeavesItemStack;
	/** A voxel map of leaves blocks that are "stamped" on to the tree during generation */
	private SimpleVoxmap leafCluster;
	/** The solver used to calculate the leaves hydration value from the values pulled from adjacent cells [default = deciduous] */
	private ICellSolver cellSolver = Cells.deciduousSolver;
	
	
	//Seeds
	/** The seed used to reproduce this tree.  Drops from the tree and can plant itself */
	private Seed seed;
	/** The seed stack for the seed.  Hold damage value for seed items with multiple variants */
	private ItemStack seedStack;
	/** Weather or not the seed is Auto-generated */
	private boolean genSeed = false;	
	/** Enable the automatic recipe registration to create Vanilla saplings from seeds and dirt(default = true) */
	protected boolean enableSaplingRecipe = true;
	/** A blockState that will turn itself into this tree */
	private IBlockState saplingBlock;
	
	/** A map of environmental biome factors that change a tree's suitability */
	public Map <Type, Float> envFactors = new HashMap<Type, Float>();//Environmental factors
	
	/** A list of JoCodes for world generation. Initialized in addJoCodes()*/
	public TreeCodeStore joCodeStore;
	
	/** Hands Off! Only dynamictrees mod should use this and only for vanilla trees */
	public DynamicTree(VanillaTreeData.EnumType treeType) {
		this(treeType.getName().replace("_",""), treeType.getMetadata());
		simpleVanillaSetup(treeType);
	}
	
	/** Hands Off! Only {@link DynamicTrees} mod should use this */
	public DynamicTree(String name, int seq) {
		this(DynamicTrees.MODID, name, seq);
	}
	
	/**
	 * Constructor suitable for derivative mods
	 * 
	 * @param modid The MODID of the mod that is registering this tree
	 * @param name The simple name of the tree e.g. "oak"
	 * @param seq The registration sequence number for this MODID. Used for registering 4 leaves types per {@link BlockDynamicLeaves}.
	 * Sequence numbers must be unique within each mod.  It's recommended to define the sequence consecutively and avoid later rearrangement. 
	 */
	public DynamicTree(String modid, String name, int seq) {
		this.name = name;
		this.modId = modid;
		
		if(seq >= 0) {
			setDynamicLeaves(modid, seq);
		}
		setDynamicBranch(new BlockBranch(name + "branch"));
		setStick(new ItemStack(Items.stick));
		
		createLeafCluster();
	}
	
	/**
	 * This is for use with Vanilla Tree types only.  Mods depending on the dynamictrees mod should 
	 * call the here contained primitive assignment functions in their constructor instead.
	 * 
	 * @param wood
	 */
	private void simpleVanillaSetup(VanillaTreeData.EnumType wood) {
		setPrimitiveLeaves(wood.getLeavesBlockAndMeta(), wood.getLeavesBlockAndMeta().toItemStack());
		setPrimitiveLog(wood.getLogBlockAndMeta(), wood.getLogBlockAndMeta().toItemStack());
		setPrimitiveSapling(new BlockAndMeta(Blocks.sapling, wood.getMetadata()));
		setDynamicSapling(new BlockAndMeta(DynamicTrees.blockDynamicSapling, wood.getMetadata()));
	}
	
	protected void setBasicGrowingParameters(float tapering, float energy, int upProbability, int lowestBranchHeight, float growthRate) {
		this.tapering = tapering;
		this.signalEnergy = energy;
		this.upProbability = upProbability;
		this.lowestBranchHeight = lowestBranchHeight;
		this.growthRate = growthRate;
	}

	public ISubstanceEffect getSubstanceEffect(ItemStack itemStack) {
		
		//Bonemeal fertilizes the soil
		if( itemStack.getItem() == Items.dye && itemStack.getItemDamage() == 15) {
			return new SubstanceFertilize().setAmount(1);
		}
		
		//Use substance provider interface if it's available
		if(itemStack.getItem() instanceof ISubstanceEffectProvider) {
			ISubstanceEffectProvider provider = (ISubstanceEffectProvider) itemStack.getItem();
			return provider.getSubstanceEffect(itemStack);
		}
		
		return null;
	}
	
	
	///////////////////////////////////////////
	// INTERACTION
	///////////////////////////////////////////
	
	public boolean applySubstance(World world, BlockPos pos, BlockRootyDirt dirt, ItemStack itemStack) {
		
		ISubstanceEffect effect = getSubstanceEffect(itemStack);
		
		if(effect != null) {
			if(effect.isLingering()) {
				CompatHelper.spawnEntity(world, new EntityLingeringEffector(world, pos, effect));
				return true;
			} else {
				return effect.apply(world, dirt, pos);
			}
		}
		
		return false;
	}

	public boolean onTreeActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, ItemStack heldItem, EnumFacing side, float hitX, float hitY, float hitZ) {
		return false;
	}

	
	//////////////////////////////
	// REGISTRATION
	//////////////////////////////

	/**
	 * This should only be called by the TreeRegistry.
	 * This registers the tree itself.  This is not used to
	 * register blocks or items with minecraft.
	 * 
	 * @return this tree for chaining
	 */
	public DynamicTree register() {
		
		//If a seed hasn't been set for this tree go ahead and generate it automatically.
		if(seed == null) {
			generateSeed();
		}
		
		//Set up the tree to drop seeds of it's kind.
		registerBottomListener(new BottomListenerDropItems(getSeedStack(), ConfigHandler.seedDropRate, true));
		
		//Add JoCodes for WorldGen
		addJoCodes();
		
		return this;
	}

	/** Used to register the blocks this tree uses.  Mainly just the {@link BlockBranch} */
	public void registerBlocks() {
		GameRegistry.register(dynamicBranch);
	}
	
	/** Used to register the items this tree uses.  Mainly just the {@link Seed} */	
	public void registerItems() {
		//No need to register ItemBlocks in 1.7.10
		if(genSeed) {//If the seed was generated internally then register it too.
			GameRegistry.register(seed);
		}
	}

	/** Used to register the recipes this tree uses. */
	public void registerRecipes() {
		
		if(primitiveSapling != null) {
			//Creates a seed from a vanilla sapling and a wooden bowl
			ItemStack saplingStack = new ItemStack(primitiveSapling.getBlock());
			saplingStack.setItemDamage(primitiveSapling.getMeta());
			
			//Create a seed from a sapling and dirt bucket
			GameRegistry.addShapelessRecipe(seedStack, new Object[]{ saplingStack, DynamicTrees.dirtBucket});
			
			//Creates a vanilla sapling from a seed and dirt bucket
			if(enableSaplingRecipe) {
				GameRegistry.addShapelessRecipe(saplingStack, new Object[]{ seedStack, DynamicTrees.dirtBucket });
			}
		}
		
	}
	
	
	//////////////////////////////
	// TREE PROPERTIES
	//////////////////////////////
	
	public String getName() {
		return name;
	}
	
	public String getModID() {
		return modId;
	}
	
	/**
	 * The qualified name of the tree complete with modId to avoid name collisions.
	 * 
	 * @return The full name of the tree
	 */
	public String getFullName() {
		return getModID() + ":" + getName();
	}
	
	/**
	 * Sets the Dynamic Leaves for this tree.
	 * 
	 * @param leaves The Dynamic Leaves Block
	 * @param sub The subtype number (0-3) for using 4 leaves type per {@link BlockDynamicLeaves} (e.g. oak=0, spruce=1, etc)
	 * @return this tree for chaining
	 */
	public DynamicTree setDynamicLeaves(BlockDynamicLeaves leaves, int sub) {
		dynamicLeaves = leaves;
		leavesSubBlock = sub;
		dynamicLeaves.setTree(leavesSubBlock, this);
		return this;
	}

	/**
	 * Set dynamic leaves from an automatically created source.
	 * 
	 * @param modid The MODID of the mod that is defining this tree
	 * @param seq The sequencing number(see constructor for details)
	 * @return this tree for chaining
	 */
	protected DynamicTree setDynamicLeaves(String modid, int seq) {
		return setDynamicLeaves(TreeHelper.getLeavesBlockForSequence(modid, seq), seq & 3);
	}
	
	public BlockDynamicLeaves getDynamicLeaves() {
		return dynamicLeaves;
	}

	public int getDynamicLeavesSub() {
		return leavesSubBlock;
	}

	protected DynamicTree setDynamicBranch(BlockBranch gBranch) {
		dynamicBranch = gBranch;//Link the tree to the branch
		dynamicBranch.setTree(this);//Link the branch back to the tree
		return this;
	}

	public BlockBranch getDynamicBranch() {
		return dynamicBranch;
	}
	
	/**
	 * This is run internally if no seed is set for the tree when it's registered
	 */
	private DynamicTree generateSeed() {
		genSeed = true;
		seed = new Seed(getName() + "seed");
		seed.setTextureName(modId + ":" + name + "seed");
		return setSeedStack(new ItemStack(seed));
	}
	
	public DynamicTree setSeedStack(ItemStack newSeedStack) {
		if(newSeedStack.getItem() instanceof Seed) {
			seedStack = newSeedStack;
			seed = (Seed) seedStack.getItem();
			seed.setTree(this, seedStack);
		} else {
			System.err.println("setSeedStack must have an ItemStack with an Item that is an instance of a Seed");
		}
		return this;
	}

	public Seed getSeed() {
		return seed;
	}
	
	public ItemStack getSeedStack() {
		return seedStack.copy();
	}

	public ItemStack getSeedStack(int qty) {
		ItemStack newSeedStack = seedStack.copy();
		newSeedStack.stackSize = qty;
		return newSeedStack;
	}
	
	protected DynamicTree setStick(ItemStack itemStack) {
		stick = itemStack;
		return this;
	}

	/**
	 * Get a quantity of whatever is considered a stick for this tree's type of wood.
	 * 
	 * @param qty Number of sticks
	 * @return an {@link ItemStack} of sticky things
	 */
	public ItemStack getStick(int qty) {
		ItemStack stack = stick.copy();
		stack.stackSize = MathHelper.clamp(qty, 0, 64);
		return stack;
	}

	/** 
	 * Sets the Dynamic Sapling for this tree type.  Also sets
	 * the tree type in the dynamic sapling.
	 * 
	 * @param sapling
	 * @return
	 */
	public DynamicTree setDynamicSapling(IBlockState sapling) {
		saplingBlock = sapling;//Link the tree to the sapling
		
		//Link the sapling to the Tree
		if(saplingBlock.getBlock() instanceof BlockDynamicSapling) {
			BlockDynamicSapling dynSap = (BlockDynamicSapling) saplingBlock.getBlock();
			dynSap.setTree(saplingBlock, this);
		}
		
		return this;
	}
	
	public IBlockState getDynamicSapling() {
		return saplingBlock;
	}
	
	protected DynamicTree setPrimitiveLeaves(IBlockState primLeaves, ItemStack primLeavesStack) {
		primitiveLeaves = primLeaves;
		primitiveLeavesItemStack = primLeavesStack;
		return this;
	}

	public IBlockState getPrimitiveLeaves() {
		return primitiveLeaves;
	}

	public ItemStack getPrimitiveLeavesItemStack(int qty) {
		ItemStack stack = primitiveLeavesItemStack.copy();
		stack.stackSize = MathHelper.clamp(qty, 0, 64);
		return stack;
	}

	protected DynamicTree setPrimitiveLog(IBlockState primLog, ItemStack primLogStack) {
		primitiveLog = primLog;
		primitiveLogItemStack = primLogStack;
		return this;
	}

	public IBlockState getPrimitiveLog() {
		return primitiveLog;
	}

	public ItemStack getPrimitiveLogItemStack(int qty) {
		ItemStack stack = primitiveLogItemStack.copy();
		stack.stackSize = MathHelper.clamp(qty, 0, 64);
		return stack;
	}

	protected DynamicTree setPrimitiveSapling(IBlockState primSapling) {
		primitiveSapling = primSapling;
		return this;
	}

	public IBlockState getPrimitiveSapling() {
		return primitiveSapling;
	}

	public float getEnergy(World world, BlockPos pos) {
		return signalEnergy;
	}

	public float getGrowthRate(World world, BlockPos pos) {
		return growthRate;
	}

	/** Probability reinforcer for up direction which is arguably the direction most trees generally grow in.*/
	public int getUpProbability() {
		return upProbability;
	}

	/** Thickness of the branch connected to a twig(radius == 1).. This should probably always be 2 [default = 2] */
	public float getSecondaryThickness() {
		return 2.0f;
	}
	
	/** Probability reinforcer for current travel direction */
	public int getReinfTravel() {
		return 1;
	}

	public int getLowestBranchHeight() {
		return lowestBranchHeight;
	}
	
	/**
	* @param world
	* @param pos 
	* @return The lowest number of blocks from the RootyDirtBlock that a branch can form.
	*/
	public int getLowestBranchHeight(World world, BlockPos pos) {
		return getLowestBranchHeight();
	}

	public void setRetries(int retries) {
		this.retries = retries;
	}
	
	public int getRetries() {
		return retries;
	}
	
	public float getTapering() {
		return tapering;
	}

	///////////////////////////////////////////
	//BRANCHES
	///////////////////////////////////////////

	
	public ICell getCellForBranch(IBlockAccess blockAccess, BlockPos pos, IBlockState blockState, EnumFacing dir, BlockBranch branch) {
		return branch.getRadius(blockState) == 1 ? Cells.branchCell : Cells.nullCell;
	}
	
	///////////////////////////////////////////
	//DIRT
	///////////////////////////////////////////
	
	public void setSoilLongevity(int longevity) {
		soilLongevity = longevity;
	}
	
	public int getSoilLongevity(World world, BlockPos pos) {
		return (int)(biomeSuitability(world, pos) * soilLongevity);
	}

	/** Used by seed to determine the proper dirt block to create for planting. */
	public BlockRootyDirt getRootyDirtBlock() {
		return DynamicTrees.blockRootyDirt;
	}

	/**
	 * Soil acceptability tester.  Mostly to test if the block is dirt but could 
	 * be overridden to allow gravel, sand, or whatever makes sense for the tree
	 * species.
	 * 
	 * @param soilBlockState
	 * @return
	 */
	public boolean isAcceptableSoil(IBlockState soilBlockState) {
		Block soilBlock = soilBlockState.getBlock();
		return soilBlock == Blocks.dirt || soilBlock == Blocks.grass || soilBlock == Blocks.mycelium || soilBlock == DynamicTrees.blockRootyDirt;
	}
	
	/**
	 * Position sensitive version of soil acceptability tester
	 * 
	 * @param blockAccess
	 * @param pos
	 * @param soilBlockState
	 * @return
	 */
	public boolean isAcceptableSoil(IBlockAccess blockAccess, BlockPos pos, IBlockState soilBlockState) {
		return isAcceptableSoil(soilBlockState);
	}
	
	/**
	 * Version of soil acceptability tester that is only run for worldgen.  This allows for Swamp oaks and stuff.
	 * 
	 * @param blockAccess
	 * @param pos
	 * @param soilBlockState
	 * @return
	 */
	public boolean isAcceptableSoilForWorldgen(IBlockAccess blockAccess, BlockPos pos, IBlockState soilBlockState) {
		return isAcceptableSoil(blockAccess, pos, soilBlockState);
	}
	
	///////////////////////////////////////////
	//RENDERING
	///////////////////////////////////////////
	
	@SideOnly(Side.CLIENT)
	public int foliageColorMultiplier(IBlockAccess blockAccess, int x, int y, int z) {
		if(blockAccess != null) {
			return Blocks.leaves2.colorMultiplier(blockAccess, x, y, z);//Access the default leaves colorizer
		}
		return ColorizerFoliage.getFoliageColorBasic();
	}
	
	///////////////////////////////////////////
	// LEAVES AUTOMATA
	///////////////////////////////////////////
	
	public void setSmotherLeavesMax(int smotherLeavesMax) {
		this.smotherLeavesMax = smotherLeavesMax;
	}
	
	public int getSmotherLeavesMax() {
		return smotherLeavesMax;
	}
	
	/** Minimum amount of light necessary for a leaves block to be created. **/
	public int getLightRequirement() {
		return lightRequirement;
	}
	
	public byte getDefaultHydration() {
		return defaultHydration;
	}
	
	public void setCellSolver(ICellSolver solver) {
		cellSolver = solver;
	}
	
	public ICellSolver getCellSolver() {
		return cellSolver;
	}
		
	public void setLeafCluster(SimpleVoxmap leafCluster) {
		this.leafCluster = leafCluster;
	}
	
	public SimpleVoxmap getLeafCluster() {
		return leafCluster;
	}
	
	/**
	 * A voxelmap of a leaf cluser for this species.  Values represent hydration value.
	 * This leaf cluster map is "stamped" on to each branch end during worldgen.  Should be
	 * representative of what the species actually produces.
	 */
	public void createLeafCluster(){

		leafCluster = new SimpleVoxmap(5, 4, 5, new byte[] {
				//Layer 0 (Bottom)
				0, 0, 0, 0, 0,
				0, 1, 1, 1, 0,
				0, 1, 1, 1, 0,
				0, 1, 1, 1, 0,
				0, 0, 0, 0, 0,

				//Layer 1
				0, 1, 1, 1, 0,
				1, 3, 4, 3, 1,
				1, 4, 0, 4, 1,
				1, 3, 4, 3, 1,
				0, 1, 1, 1, 0,
				
				//Layer 2
				0, 1, 1, 1, 0,
				1, 2, 3, 2, 1,
				1, 3, 4, 3, 1,
				1, 2, 3, 2, 1,
				0, 1, 1, 1, 0,
				
				//Layer 3(Top)
				0, 0, 0, 0, 0,
				0, 1, 1, 1, 0,
				0, 1, 1, 1, 0,
				0, 1, 1, 1, 0,
				0, 0, 0, 0, 0,
				
		}).setCenter(new BlockPos(2, 1, 2));

	}
	
	public byte getLeafClusterPoint(BlockPos twigPos, BlockPos leafPos) {
		return leafCluster.getVoxel(twigPos, leafPos);
	}
	
	
	//////////////////////////////
	// LEAVES HANDLING
	//////////////////////////////

	public boolean isCompatibleDynamicLeaves(IBlockAccess blockAccess, BlockPos pos) {

		IBlockState state = pos.getBlockState(blockAccess);
		ITreePart treePart = TreeHelper.getTreePart(state);
		
		if (treePart != null && treePart instanceof BlockDynamicLeaves) {
			return this == ((BlockDynamicLeaves)treePart).getTree(state);			
		}
		
		return false;
	}

	public boolean isCompatibleDynamicLeaves(Block leaves, int sub) {
		return leaves == getDynamicLeaves() && sub == getDynamicLeavesSub();
	}

	public boolean isCompatibleVanillaLeaves(IBlockAccess blockAccess, BlockPos pos) {
		return getPrimitiveLeaves().matches(pos.getBlockState(blockAccess), 3);
	}

	public boolean isCompatibleGenericLeaves(IBlockAccess blockAccess, BlockPos pos) {
		return isCompatibleDynamicLeaves(blockAccess, pos) || isCompatibleVanillaLeaves(blockAccess, pos);
	}
	
	public ICell getCellForLeaves(int hydro) {
		return Cells.normalCells[hydro];
	}
	
	//////////////////////////////
	// DROPS HANDLING
	//////////////////////////////
	
	/** 
	* Override to add items to the included list argument. For apples and whatnot.
	* Pay Attention!  Add items to drops parameter.
	* 
	* @param world
	* @param pos
	* @param chance
	* @param drops
	* @return
	*/
	public ArrayList<ItemStack> getDrops(IBlockAccess blockAccess, BlockPos pos, int chance, ArrayList<ItemStack> drops) {
		return drops;
	}
	
	
	//////////////////////////////
	// BIOME HANDLING
	//////////////////////////////
	
	public DynamicTree envFactor(Type type, float factor) {
		envFactors.put(type, factor);
		return this;
	}
	
	/**
	*
	* @param world The World
	* @param pos
	* @return range from 0.0 - 1.0.  (0.0f for completely unsuited.. 1.0f for perfectly suited)
	*/
	public float biomeSuitability(World world, BlockPos pos) {

		BiomeGenBase biome = world.getBiomeGenForCoords(pos.getX(), pos.getZ());
		
		//An override to allow other mods to change the behavior of the suitability for a world location. Such as Terrafirmacraft.
		if(TreeRegistry.isBiomeSuitabilityOverrideEnabled()) {
			IBiomeSuitabilityDecider.Decision override = TreeRegistry.getBiomeSuitability(world, biome, this, pos);

			if(override.isHandled()) {
				return override.getSuitability();
			}
		}
		
		if(ConfigHandler.ignoreBiomeGrowthRate || isBiomePerfect(biome)) {
			return 1.0f;
		}

		float s = defaultSuitability();
		
		for(Type t : BiomeDictionary.getTypesForBiome(biome)) {
			s *= envFactors.containsKey(t) ? envFactors.get(t) : 1.0f;
		}
		
		return MathHelper.clamp(s, 0.0f, 1.0f);
	}

	public boolean isBiomePerfect(BiomeGenBase biome) {
		return false;
	}

	/** A value that determines what a tree's suitability is before climate manipulation occurs. */
	public static final float defaultSuitability() {
		return 0.85f;
	}

	/**
	* A convenience function to test if a biome is one of the many options passed.
	* 
	* @param biomeToCheck The biome we are matching
	* @param biomes Multiple biomes to match against
	* @return True if a match is found. False if not.
	*/
	public static boolean isOneOfBiomes(BiomeGenBase biomeToCheck, BiomeGenBase ... biomes) {
		for(BiomeGenBase biome: biomes) {
			if(biomeToCheck.biomeID == biome.biomeID) {
				return true;
			}
		}
		return false;
	}
		
	/**
	* Handle rotting branches
	* @param world The world
	* @param pos
	* @param neighborCount Count of neighbors reinforcing this block
	* @param radius The radius of the branch
	* @param random Access to a random number generator
	* @return true if the branch should rot
	*/
	public boolean rot(World world, BlockPos pos, int neighborCount, int radius, Random random) {
		
		final EnumFacing upFirst[] = {EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST};
		
		if(radius <= 1) {
			for(EnumFacing dir: upFirst) {
				if(getDynamicLeaves().growLeaves(world, this, pos.offset(dir), 0)) {
					return false;
				}
			}
		}
		world.setBlockToAir(pos.getX(), pos.getY(), pos.getZ());
		return true;
	}
	
	
	///////////////////////////////////////////
	// GROWTH
	///////////////////////////////////////////
	
	/**
	* Selects a new direction for the branch(grow) signal to turn to.
	* This function uses a probability map to make the decision and is acted upon by the GrowSignal() function in the branch block.
	* Can be overridden for different species but it's preferable to override customDirectionManipulation.
	* 
	* @param world The World
	* @param pos
	* @param branch The branch block the GrowSignal is traveling in.
	* @param signal The grow signal.
	* @return
	*/
	public EnumFacing selectNewDirection(World world, BlockPos pos, BlockBranch branch, GrowSignal signal) {
		EnumFacing originDir = signal.dir.getOpposite();

		//prevent branches on the ground
		if(signal.numSteps + 1 <= getLowestBranchHeight(world, signal.origin)) {
			return EnumFacing.UP;
		}

		int probMap[] = new int[6];//6 directions possible DUNSWE

		//Probability taking direction into account
		probMap[EnumFacing.UP.ordinal()] = signal.dir != EnumFacing.DOWN ? getUpProbability(): 0;//Favor up
		probMap[signal.dir.ordinal()] += getReinfTravel(); //Favor current direction

		//Create probability map for direction change
		for(EnumFacing dir: EnumFacing.VALUES) {
			if(!dir.equals(originDir)) {
				BlockPos deltaPos = pos.offset(dir);
				//Check probability for surrounding blocks
				//Typically Air:1, Leaves:2, Branches: 2+r
				probMap[dir.getIndex()] += TreeHelper.getSafeTreePart(world, deltaPos).probabilityForBlock(world, deltaPos, branch);
			}
		}

		//Do custom stuff or override probability map for various species
		probMap = customDirectionManipulation(world, pos, branch.getRadius(world, pos), signal, probMap);

		//Select a direction from the probability map
		int choice = selectRandomFromDistribution(signal.rand, probMap);//Select a direction from the probability map
		return newDirectionSelected(EnumFacing.getFront(choice != -1 ? choice : 1), signal);//Default to up if things are screwy
	}

	/** Species can override the probability map here **/
	protected int[] customDirectionManipulation(World world, BlockPos pos, int radius, GrowSignal signal, int probMap[]) {
		return probMap;
	}

	/** Species can override to take action once a new direction is selected **/
	protected EnumFacing newDirectionSelected(EnumFacing newDir, GrowSignal signal) {
		return newDir;
	}

	/** Select a random direction weighted from the probability map **/ 
	public static int selectRandomFromDistribution(Random random, int distMap[]) {

		int distSize = 0;

		for(int i = 0; i < distMap.length; i++) {
			distSize += distMap[i];
		}

		if(distSize <= 0) {
			System.err.println("Warning: Zero sized distribution");
			return -1;
		}

		int rnd = random.nextInt(distSize) + 1;

		for(int i = 0; i < 6; i++) {
			if(rnd > distMap[i]) {
				rnd -= distMap[i];
			} else {
				return i;
			}
		}

		return 0;
	}	

	/** Gets the fruiting node analyzer for this tree.  See {@link NodeFruitCocoa} for an example.
	*  
	* @param world The World
	* @param x X-Axis of block
	* @param y Y-Axis of block
	* @param z Z-Axis of block
	*/
	public NodeFruit getNodeFruit(World world, BlockPos pos) {
		return null;//Return null to disable fruiting. Most species do.
	}
	
	
	//////////////////////////////
	// BOTTOM SPECIAL
	//////////////////////////////

	/**
	* Run special effects for bottom blocks
	* 
	* @param world The World
	* @param x X-Axis of block
	* @param y Y-Axis of block
	* @param z Z-Axis of block
	* @param random Random number access
	*/
	public void bottomSpecial(World world, BlockPos pos, Random random) {
		for(IBottomListener special: bottomSpecials) {
			float chance = special.chance();
			if(chance != 0.0f && random.nextFloat() <= chance) {
				special.run(world, this, pos, random);//Make it so!
			}
		}
	}

	/**
	* Provides an interface for other mods to add special effects like fruit, spawns or whatever
	*  
	* @param listeners
	* @return DynamicTree for function chaining
	*/
	public DynamicTree registerBottomListener(IBottomListener ... listeners) {
		for(IBottomListener listener: listeners) {
			bottomSpecials.add(listener);
		}
		return this;
	}
	
	/**
	 * Provides the {@link BlockBonsaiPot} for this tree.  Each mod will
	 * have to derive it's own BonzaiPot subclass if it wants this feature.
	 * 
	 * @return
	 */
	public BlockBonsaiPot getBonzaiPot() {
		return DynamicTrees.blockBonsaiPot;
	}
	
	
	//////////////////////////////
	// WORLDGEN STUFF
	//////////////////////////////
	
	/**
	 * A {@link JoCode} defines the block model of the {@link DynamicTree}
	 */
	public void addJoCodes() {
		joCodeStore = new TreeCodeStore(this);
		joCodeStore.addCodesFromFile("assets/" + getModID() + "/trees/"+ getName() + ".txt");
	}

	/**
	 * Default worldgen spawn mechanism.
	 * This method uses JoCodes to generate tree models.
	 * Override to use other methods.
	 * 
	 * @param world
	 * @param pos
	 * @param biome 
	 * @param facing
	 * @param radius
	 * @return true if tree was generated. false otherwise.
	 */
	public boolean generate(World world, BlockPos pos, BiomeGenBase biome, Random random, int radius) {
		EnumFacing facing = CoordUtils.getRandomDir(random);
		if(joCodeStore != null) {
			JoCode code = joCodeStore.getRandomCode(radius, random);
			if(code != null) {
				code.generate(world, this, pos, facing, radius);
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Worldgen can produce thin sickly trees from the underinflation caused by not living it's full life.
	 * This factor is an attempt to compensate for the problem.
	 * 
	 * @return
	 */
	public float getWorldGenTaperingFactor() {
		return 1.5f;
	}
	
	//////////////////////////////
	// JAVA OBJECT STUFF
	//////////////////////////////
	
	@Override
	public String toString() {
		return getName();
	}
	
}