package com.ferreusveritas.dynamictrees.worldgen;

import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.Random;

import com.ferreusveritas.dynamictrees.api.TreeRegistry;
import com.ferreusveritas.dynamictrees.trees.Species;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;


public class JsonMath {
	
	public MathOperator rootOp;
	
	public JsonMath(JsonElement mathElement) {
		
		if(mathElement.isJsonObject()) {
			JsonObject mathObject = mathElement.getAsJsonObject();
			
			for(Entry<String, JsonElement> entry : mathObject.entrySet()) {
				rootOp = processElement(entry.getKey(), entry.getValue());
				if(rootOp != null) {
					return;
				}
			}
		}
	}
	
	private MathOperator processElement(String key, JsonElement value) {
		
		EnumMathFunction op = EnumMathFunction.getFunction(key);
		
		ArrayList<MathOperator> paramList = new ArrayList<>();
		Species speciesArg = Species.NULLSPECIES;
		
		//If the value is an array then these are the parameters for this operation
		if(value.isJsonArray()) {
			for(JsonElement parameter : value.getAsJsonArray()) {
				MathOperator m = null;
				if(parameter.isJsonObject()) {
					Entry<String, JsonElement> entry = parameter.getAsJsonObject().entrySet().iterator().next();
					m = processElement(entry.getKey(), entry.getValue());
				} else
				if (parameter.isJsonPrimitive()) {
					if(parameter.getAsJsonPrimitive().isNumber()) {
						m = new Const(parameter.getAsFloat());
					} else 
					if(parameter.getAsJsonPrimitive().isString()) {
						String name = parameter.getAsString();
						if(EnumMathFunction.NOISE.name.equals(name)) {
							m = new Noise();
						} else 
						if(EnumMathFunction.RAND.name.equals(name)) {
							m = new Rand();
						}
						if(TreeRegistry.findSpeciesSloppy(name) != Species.NULLSPECIES) {
							speciesArg = TreeRegistry.findSpeciesSloppy(name);
						}
					}
				}
				
				if(m != null) {
					paramList.add(m);
				}
				
			}
		}
		
		MathOperator paramArray[] = paramList.toArray(new MathOperator[0]);
		
		switch(op) {
			case NOISE: return new Noise();
			case RAND: return new Rand();
			case ADD: return new Adder(paramArray);
			case SUB: return new Subtractor(paramArray);
			case MUL: return new Multiplier(paramArray);
			case DIV: return new Divider(paramArray);
			case MAX: return new Maximum(paramArray);
			case MIN: return new Minimum(paramArray);
			case IFGT: return new IfGreaterThan(paramArray);
			case SPECIES: return speciesArg != Species.NULLSPECIES ? new IfSpecies(speciesArg, paramArray) : null;
			default: return null;
		}
		
	}
	
	public float apply(Random random, float noise) {
		MathContext mc = new MathContext(noise, random);
		return rootOp.apply(mc);
	}
	
	public float apply(Random random, Species species, float radius) {
		MathContext mc = new MathSpeciesContext(random, species, radius);
		return rootOp.apply(mc);
	}
	
	public static class MathContext {
		public float noise;
		public Random rand;
		
		public MathContext(float noise, Random random) {
			this.noise = noise;
			this.rand = random;
		}
	}
	
	public static class MathSpeciesContext extends MathContext {
		public float radius;
		public Species species;
		
		public MathSpeciesContext(Random random, Species species, float radius) {
			super(0.0f, random);
			this.radius = radius;
			this.species = species;
		}
		
	}
	
	public static interface MathOperator {
		float apply(MathContext mc);
	}
	
	public static class Const implements MathOperator {
		private final float value;
		
		Const(float value) {
			this.value = value;
		}
		
		@Override
		public float apply(MathContext mc) {
			return value;
		}
	}
	
	public static class Noise implements MathOperator {
		
		@Override
		public float apply(MathContext mc) {
			return mc.noise;
		}
		
	}
	
	public static class Rand implements MathOperator {

		@Override
		public float apply(MathContext mc) {
			return mc.rand.nextFloat();
		}
		
	}
	
	public static class Adder implements MathOperator {
		
		private final MathOperator[] functions;
		private final boolean dual;
		
		public Adder(MathOperator[] functionArray) {
			this.functions = functionArray;
			dual = functions.length == 2;
		}
		
		@Override
		public float apply(MathContext mc) {
			
			if(dual) {
				return functions[0].apply(mc) + functions[1].apply(mc);
			}
			
			float r = 0;
			for(MathOperator f: functions) {
				r += f.apply(mc);
			}
			
			return r;
		}
	}
	
	public static class Subtractor implements MathOperator {
		
		private final MathOperator[] functions;
		private final boolean dual;
		
		public Subtractor(MathOperator[] functionArray) {
			this.functions = functionArray;
			dual = functions.length == 2;
		}
		
		@Override
		public float apply(MathContext mc) {
			
			if(dual) {
				return functions[0].apply(mc) - functions[1].apply(mc);
			}
			
			Float r = null;
			for(MathOperator f: functions) {
				float v = f.apply(mc);
				r = r == null ? v : r - v;
			}
			
			return r == null ? 0.0f : r.floatValue();
		}
	}
	
	public static class Multiplier implements MathOperator {
		
		private final MathOperator[] functions;
		private final boolean dual;
		
		public Multiplier(MathOperator[] functionArray) {
			this.functions = functionArray;
			dual = functions.length == 2;
		}
		
		@Override
		public float apply(MathContext mc) {
			
			if(dual) {
				return functions[0].apply(mc) * functions[1].apply(mc);
			}
			
			float r = 1.0f;
			for(MathOperator f: functions) {
				r *= f.apply(mc);
			}
			
			return r;
		}
	}
	
	public static class Divider implements MathOperator {
		
		private final MathOperator[] functions;
		private final boolean dual;
		
		public Divider(MathOperator[] functionArray) {
			this.functions = functionArray;
			dual = functions.length == 2;
		}
		
		@Override
		public float apply(MathContext mc) {
			
			if(dual) {
				return functions[0].apply(mc) / functions[1].apply(mc);
			}
			
			Float r = null;
			for(MathOperator f: functions) {
				float v = f.apply(mc);
				r = r == null ? v : r / v;
			}
			
			return r == null ? 0.0f : r.floatValue();
		}
	}

	public static class Modulus implements MathOperator {
		
		private final MathOperator[] functions;
		private final boolean dual;
		
		public Modulus(MathOperator[] functionArray) {
			this.functions = functionArray;
			dual = functions.length == 2;
		}
		
		@Override
		public float apply(MathContext mc) {
			
			if(dual) {
				return functions[0].apply(mc) % functions[1].apply(mc);
			}
						
			return 0.0f;
		}
	}

	
	public static class Maximum implements MathOperator {
		
		private final MathOperator[] functions;
		private final boolean dual;
		
		public Maximum(MathOperator[] functionArray) {
			this.functions = functionArray;
			dual = functions.length == 2;
		}
		
		@Override
		public float apply(MathContext mc) {
			
			if(dual) {
				return Math.max(functions[0].apply(mc), functions[1].apply(mc));
			}
			
			Float r = null;
			for(MathOperator f: functions) {
				float v = f.apply(mc);
				r = r == null ? v : Math.max(r, v);
			}
			
			return r == null ? 0.0f : r.floatValue();
		}
	}
	
	public static class Minimum implements MathOperator {
		
		private final MathOperator[] functions;
		private final boolean dual;
		
		public Minimum(MathOperator[] functionArray) {
			this.functions = functionArray;
			dual = functions.length == 2;
		}
		
		@Override
		public float apply(MathContext mc) {
			
			if(dual) {
				return Math.min(functions[0].apply(mc), functions[1].apply(mc));
			}
			
			Float r = null;
			for(MathOperator f: functions) {
				float v = f.apply(mc);
				r = r == null ? v : Math.min(r, v);
			}
			
			return r == null ? 0.0f : r.floatValue();
		}
	}
	
	public static class IfGreaterThan implements MathOperator {
		
		private final MathOperator[] functions;
		
		public IfGreaterThan(MathOperator[] functionArray) {
			this.functions = functionArray;
		}
		
		@Override
		public float apply(MathContext mc) {
			
			if(functions.length == 4) {
				return functions[0].apply(mc) > functions[1].apply(mc) ? functions[2].apply(mc) : functions[3].apply(mc);
			}
			
			return 0.0f;
		}
		
	}
	
	public static class IfSpecies implements MathOperator {
		
		private final MathOperator[] functions;
		private final Species species;
		
		public IfSpecies(Species species, MathOperator[] functionArray) {
			this.species = species;
			this.functions = functionArray;
		}
		
		@Override
		public float apply(MathContext mc) {
			
			if(mc instanceof MathSpeciesContext && functions.length == 2) {
				return ((MathSpeciesContext)mc).species == species ? functions[0].apply(mc) : functions[1].apply(mc);
			}
			
			return 0.0f;
		}
		
	}
	
	public enum EnumMathFunction {
		CONST,
		NOISE,
		RAND,
		ADD,
		SUB,
		MUL,
		DIV,
		MOD,
		MAX,
		MIN,
		IFGT,
		SPECIES;
		
		public final String name;
		
		private EnumMathFunction() {
			this.name = toString().toLowerCase();
		}
		
		static EnumMathFunction getFunction(String findName) {
			for(EnumMathFunction fun : EnumMathFunction.values()) {
				if(fun.name.equals(findName)) {
					return fun;
				}
			}
			return null;
		}
		
	}
}
