package com.ferreusveritas.dynamictrees.worldgen;

import java.util.ArrayList;
import java.util.Random;

import com.ferreusveritas.dynamictrees.ModConfigs;
import com.ferreusveritas.dynamictrees.api.TreeHelper;
import com.ferreusveritas.dynamictrees.api.WorldGenRegistry;
import com.ferreusveritas.dynamictrees.api.backport.Biome;
import com.ferreusveritas.dynamictrees.api.backport.BlockPos;
import com.ferreusveritas.dynamictrees.api.backport.BlockState;
import com.ferreusveritas.dynamictrees.api.backport.EnumDyeColor;
import com.ferreusveritas.dynamictrees.api.backport.IBlockState;
import com.ferreusveritas.dynamictrees.api.backport.World;
import com.ferreusveritas.dynamictrees.api.worldgen.IBiomeDensityProvider.EnumChance;
import com.ferreusveritas.dynamictrees.api.worldgen.IBiomeSpeciesSelector.Decision;
import com.ferreusveritas.dynamictrees.trees.Species;
import com.ferreusveritas.dynamictrees.util.Circle;
import com.ferreusveritas.dynamictrees.util.CompatHelper;

import cpw.mods.fml.common.IWorldGenerator;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.feature.WorldGenBigMushroom;
import net.minecraftforge.common.BiomeDictionary.Type;

public class TreeGenerator implements IWorldGenerator {
	
	private static TreeGenerator INSTANCE;
	
	public BiomeTreeHandler biomeTreeHandler; //Provides forest properties for a biome
	public BiomeRadiusCoordinator radiusCoordinator; //Finds radius for coordinates
	public TreeCodeStore codeStore;
	protected ChunkCircleManager circleMan;
	protected RandomXOR random;
	
	public static TreeGenerator getTreeGenerator() {
		return INSTANCE;
	}
	
	public static void preInit() {
		if(WorldGenRegistry.isWorldGenEnabled()) {
			INSTANCE = new TreeGenerator();
		}
	}
	
	/**
	 * This is run during the init phase to cache 
	 * tree data that was created during the preInit phase
	 */
	public static void init() {
		if(WorldGenRegistry.isWorldGenEnabled()) {
			INSTANCE.biomeTreeHandler.init();
		}
	}
	
	/**
	 * This is for world debugging.
	 * The colors signify the different tree spawn failure modes.
	 *
	 */
	public enum EnumGeneratorResult {
		GENERATED(EnumDyeColor.WHITE),
		NOTREE(EnumDyeColor.BLACK),
		UNHANDLEDBIOME(EnumDyeColor.YELLOW),
		FAILSOIL(EnumDyeColor.BROWN),
		FAILCHANCE(EnumDyeColor.BLUE),
		FAILGENERATION(EnumDyeColor.RED);
		
		private final EnumDyeColor color;
		
		private EnumGeneratorResult(EnumDyeColor color) {
			this.color = color;
		}
		
		public EnumDyeColor getColor() {
			return this.color;
		}
	
	}
	
	public TreeGenerator() {
		biomeTreeHandler = new BiomeTreeHandler();
		radiusCoordinator = new BiomeRadiusCoordinator(biomeTreeHandler);
		circleMan = new ChunkCircleManager(radiusCoordinator);
		random = new RandomXOR();
	}
	
	public void onWorldUnload() {
		circleMan = new ChunkCircleManager(radiusCoordinator);//Clears the cached circles
	}
	
	public ChunkCircleManager getChunkCircleManager() {
		return circleMan;
	}
	
	@Override
	public void generate(Random randomUnused, int chunkX, int chunkZ, net.minecraft.world.World _world, IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {
		World world = new World(_world);

		//We use this custom random number generator because despite what everyone says the Java Random class is not thread safe.
		random.setXOR(new BlockPos(chunkX, 0, chunkZ));
		
		switch (world.real().provider.dimensionId) {
		case 0: //Overworld
			generateOverworld(random, chunkX, chunkZ, world, chunkGenerator, chunkProvider);
			break;
		case -1: //Nether
			break;
		case 1: //End
			break;
		}
	}
	
	private void generateOverworld(Random random, int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {
		ArrayList<Circle> circles = circleMan.getCircles(world, random, chunkX, chunkZ);
		
		for(Circle c: circles) {
			makeTree(world, c);
		}
		
		BlockPos pos = new BlockPos(chunkX * 16, 0, chunkZ * 16);
		if(CompatHelper.biomeHasType(world.getBiome(pos), Type.SPOOKY)) {
			roofedForestCompensation(world, random, pos);
		}
	}
	
	/**
	 * Decorate the roofedForest exactly like Minecraft, except leave out the trees and just make giant mushrooms
	 * 
	 * @param world
	 * @param random
	 * @param pos
	 */
	public void roofedForestCompensation(World world, Random random, BlockPos pos) {
		for (int xi = 0; xi < 4; ++xi) {
			for (int zi = 0; zi < 4; ++zi) {
				int posX = pos.getX() + xi * 4 + 1 + 8 + random.nextInt(3);
				int posZ = pos.getZ() + zi * 4 + 1 + 8 + random.nextInt(3);
				BlockPos blockpos = world.getHeight(pos.add(posX, 0, posZ));
				blockpos = TreeHelper.findGround(world, blockpos).up();
				
				if (random.nextInt(6) == 0) {
					new WorldGenBigMushroom().generate(world.real(), random, blockpos.getX(), blockpos.getY(), blockpos.getZ());
				}
			}
		}
	}
	
	public void makeWoolCircle(World world, Circle circle, int h, EnumGeneratorResult resultType) {
		makeWoolCircle(world, circle, h, resultType, 0);
	}
	
	public void makeWoolCircle(World world, Circle circle, int h, EnumGeneratorResult resultType, int flags) {
		//System.out.println("Making circle at: " + circle.x + "," + circle.z + ":" + circle.radius + " H: " + h);
		
		for(int ix = -circle.radius; ix <= circle.radius; ix++) {
			for(int iz = -circle.radius; iz <= circle.radius; iz++) {
				if(circle.isEdge(circle.x + ix, circle.z + iz)) {
					world.setBlockState(new BlockPos(circle.x + ix, h, circle.z + iz), new BlockState(Blocks.wool, (circle.x ^ circle.z) & 0xF), 0);
				}
			}
		}
		
		if(resultType != EnumGeneratorResult.GENERATED) {
			BlockPos pos = new BlockPos(circle.x, h, circle.z);
			EnumDyeColor color = resultType.getColor();
			world.setBlockState(pos, new BlockState(Blocks.wool, color.getMetadata()));
			world.setBlockState(pos.up(), new BlockState(Blocks.carpet, color.getMetadata()));
		}
	}
	
	private EnumGeneratorResult makeTree(World world, Circle circle) {
		
		circle.add(8, 8);//Move the circle into the "stage"
		
		BlockPos pos = world.getHeight(new BlockPos(circle.x, 0, circle.z)).down();
		while(world.isAirBlock(pos) || TreeHelper.isTreePart(world, pos)) {//Skip down past the bits of generated tree and air
			pos = pos.down();
		}
		
		IBlockState blockState = world.getBlockState(pos);
		
		EnumGeneratorResult result = EnumGeneratorResult.GENERATED;
		
		Biome biome = world.getBiome(pos);
		Decision decision = biomeTreeHandler.getSpecies(world, biome, pos, blockState, random);
		if(decision.isHandled()) {
			Species species = decision.getSpecies();
			if(species != null) {
				if(species.isAcceptableSoilForWorldgen(world, pos, blockState)) {
					if(biomeTreeHandler.chance(biome, species, circle.radius, random) == EnumChance.OK) {
						if(species.generate(world, pos, biome, random, circle.radius)) {
							result = EnumGeneratorResult.GENERATED;
						} else {
							result = EnumGeneratorResult.FAILGENERATION;
						}
					} else {
						result = EnumGeneratorResult.FAILCHANCE;
					}
				} else {
					result = EnumGeneratorResult.FAILSOIL;
				}
			} else {
				result = EnumGeneratorResult.NOTREE;
			}
		} else {
			result = EnumGeneratorResult.UNHANDLEDBIOME;
		}
		
		//Display wool circles for testing the circle growing algorithm
		if(ModConfigs.worldGenDebug) {
			makeWoolCircle(world, circle, pos.getY(), result);
		}
		
		circle.add(-8, -8);//Move the circle back to normal coords
		
		return result;
	}
	
}
