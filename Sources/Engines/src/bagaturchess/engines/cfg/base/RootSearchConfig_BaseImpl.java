package bagaturchess.engines.cfg.base;


import java.io.File;
import java.util.Arrays;

import bagaturchess.bitboard.api.IBoardConfig;
import bagaturchess.bitboard.impl.utils.ReflectionUtils;
import bagaturchess.search.api.IEvalConfig;
import bagaturchess.search.api.IRootSearchConfig;
import bagaturchess.search.api.ISearchConfig_AB;
import bagaturchess.uci.api.ChannelManager;
import bagaturchess.uci.api.IUCIOptionsProvider;
import bagaturchess.uci.api.IUCIOptionsRegistry;
import bagaturchess.uci.impl.commands.options.UCIOption;
import bagaturchess.uci.impl.commands.options.UCIOptionCombo;
import bagaturchess.uci.impl.commands.options.UCIOptionSpin_Integer;
import bagaturchess.uci.impl.commands.options.UCIOptionString;


public abstract class RootSearchConfig_BaseImpl implements IRootSearchConfig, IUCIOptionsProvider {
	
	
	protected static final double MEM_USAGE_TPT 					= 0.50;
	protected static final double MEM_USAGE_EVALCACHE 				= 0.35;
	protected static final double MEM_USAGE_PAWNCACHE 				= 0.15;
	
	
	private static final String DEFAULT_TbPath 						= getDefaultTBPath();
	
	private static final boolean DEFAULT_SyzygyOnline 				= false;
	
	private static final int DEFAULT_MEM_USAGE_percent 				= 73;
	
	private static final boolean DEFAULT_UseTranspositionTable 		= true;
	private static final boolean DEFAULT_UseEvalCache 				= true;
	private static final boolean DEFAULT_UseSyzygyDTZCache 			= true;
	
	private UCIOption[] options 									= new UCIOption[] {
			
			new UCIOptionSpin_Integer("MemoryUsagePercent"	, DEFAULT_MEM_USAGE_percent				, "type spin default " + DEFAULT_MEM_USAGE_percent + " min 50 max 90"),
			new UCIOption("TranspositionTable"				, "" + DEFAULT_UseTranspositionTable	, "type check default " + DEFAULT_UseTranspositionTable),
			new UCIOption("EvalCache"						, "" + DEFAULT_UseEvalCache				, "type check default " + DEFAULT_UseEvalCache),
			new UCIOptionString("SyzygyPath"				, DEFAULT_TbPath						, "type string default " + DEFAULT_TbPath),
			new UCIOption("SyzygyOnline"					, "" + DEFAULT_SyzygyOnline				, "type check default " + DEFAULT_SyzygyOnline),
			new UCIOption("SyzygyDTZCache "					, "" + DEFAULT_UseSyzygyDTZCache		, "type check default " + DEFAULT_UseSyzygyDTZCache),
			new UCIOptionSpin_Integer("MultiPV"				, new Integer(1)						, "type spin default 1 min 1 max 100"),
			//new UCIOptionSpin_Integer("Hidden Depth"		, 0										, "type spin default 0 min 0 max 10"),
	};
	
	
	private String searchImpl_ClassName;
	
	private ISearchConfig_AB searchImpl_ConfigObj;
	
	
	private IBoardConfig boardCfg;
	
	private IEvalConfig evalCfg;
	
	
	private int MEM_USAGE_percent 								= DEFAULT_MEM_USAGE_percent;
	
	private boolean useTranspositionTable 						= DEFAULT_UseTranspositionTable;
	private boolean useEvalCache 								= DEFAULT_UseEvalCache;
	private boolean useSyzygyDTZCache 							= DEFAULT_UseSyzygyDTZCache;
	
	
	private int multiPVsCount 									= 1;
	
	
	private String TbPath 										= DEFAULT_TbPath;
	
	private boolean use_online_syzygy 							= DEFAULT_SyzygyOnline;
	
	
	private int hiddenDepth 									= 0;
	
	
	public RootSearchConfig_BaseImpl(String[] args) {
		
		searchImpl_ClassName = args[0];
		
		String[] searchParams = extractParams(args, "-s");
		if (searchParams == null) {
			searchImpl_ConfigObj = (ISearchConfig_AB) ReflectionUtils.createObjectByClassName_NoArgsConstructor(args[1]);
		} else {
			searchImpl_ConfigObj = (ISearchConfig_AB) ReflectionUtils.createObjectByClassName_StringsConstructor(args[1], searchParams);
		}
		
		if (args.length > 2) {
			
			String[] boardParams = extractParams(args, "-b");
			if (boardParams == null) {
				boardCfg = (IBoardConfig) ReflectionUtils.createObjectByClassName_NoArgsConstructor(args[2]);
			} else {
				boardCfg = (IBoardConfig) ReflectionUtils.createObjectByClassName_StringsConstructor(args[2], boardParams);
			}
			
			if (args.length > 3) {
				
				String[] evalParams = extractParams(args, "-e");
				if (evalParams == null) {
					evalCfg = (IEvalConfig) ReflectionUtils.createObjectByClassName_NoArgsConstructor(args[3]);
				} else {
					evalCfg = (IEvalConfig) ReflectionUtils.createObjectByClassName_StringsConstructor(args[3], evalParams);
				}
			}
		}
		
		//ChannelManager.getChannel().dump("RootSearchConfig_BaseImpl.applyOption: UseTranspositionTable=" + useTranspositionTable);
	}


	private String[] extractParams(String[] args, String markingPrefix) {
		
		if (args == null) {
			return null;
		}
		
		int param_start_index = -1;
		int param_end_index = -1;
		
		for (int i = 0; i < args.length; i++) {
			
			String curr_arg = args[i];
			
			if (markingPrefix.equals(curr_arg)) {
				param_start_index = i + 1;
			} else {
				if (param_start_index != -1) {
					
					if (curr_arg == null) {
						param_end_index = i - 1;
						break;
					}
					
					if (curr_arg.startsWith("-")) {
						param_end_index = i - 1;
						break;
					}
					
					if (i == args.length - 1) {
						param_end_index = args.length - 1;
					}
				}
			}
		}
		
		if (param_start_index > -1) {
			for (int i=param_start_index; i<=param_end_index; i++) {
				if (!args[i].contains("=")) {
					throw new IllegalStateException("Index=" + i + ", args[i]=" + args[i]);
				}
			}
		}
		
		if (param_start_index == -1) {
			return null;
		}
		
		return Arrays.copyOfRange(args, param_start_index, param_end_index + 1);
	}
	
	
	@Override
	public boolean initCaches() {
		
		return true;
	}
	
	
	@Override
	public int getThreadsCount() {
		return 1;
	}
	
	@Override
	public int getThreadMemory_InMegabytes() {
		
		throw new IllegalStateException();
	}
	
	@Override
	public String getSearchClassName() {
		
		return searchImpl_ClassName;
	}
	
	
	@Override
	public ISearchConfig_AB getSearchConfig() {
		
		return searchImpl_ConfigObj;
	}
	
	
	@Override
	public IBoardConfig getBoardConfig() {
		
		return boardCfg;
	}
	
	
	@Override
	public IEvalConfig getEvalConfig() {
		
		return evalCfg;
	}
	
	
	@Override
	public String getTbPath() {
		return TbPath;
	}
	
	
	@Override
	public boolean useOnlineSyzygy() {
		
		return use_online_syzygy;
	}
	
	
	@Override
	public double getTPTUsagePercent() {
		
		return MEM_USAGE_TPT;
	}
	
	
	@Override
	public double getEvalCacheUsagePercent() {
		
		return MEM_USAGE_EVALCACHE;
	}
	
	
	@Override
	public double getPawnsCacheUsagePercent() {
		
		return MEM_USAGE_PAWNCACHE;
	}
	
	
	@Override
	public int getMultiPVsCount() {
		
		return multiPVsCount;
	}
	
	
	@Override
	public double get_MEMORY_USAGE_PERCENT() {
		
		return MEM_USAGE_percent / 100f;
	}
	
	
	@Override
	public boolean useTPT() {
		
		return useTranspositionTable;
	}
	

	@Override
	public boolean useEvalCache() {
		
		return useEvalCache;
	}



	@Override
	public boolean useSyzygyDTZCache() {
		
		return useSyzygyDTZCache;
	}
	
	
	@Override
	public int getHiddenDepth() {
		return hiddenDepth;
	}
	
	
	@Override
	public void registerProviders(IUCIOptionsRegistry registry) {
		
		registry.registerProvider(this);
		
		if (searchImpl_ConfigObj instanceof IUCIOptionsProvider) {
			registry.registerProvider((IUCIOptionsProvider) searchImpl_ConfigObj);
		}
		
		if (evalCfg instanceof IUCIOptionsProvider) {
			registry.registerProvider((IUCIOptionsProvider) evalCfg);
		}
	}
	
	
	@Override
	public UCIOption[] getSupportedOptions() {
		
		return options;
	}
	
	
	@Override
	public boolean applyOption(UCIOption option) {
		
		if ("MultiPV".equals(option.getName())) {
			
			multiPVsCount = (Integer) option.getValue();
			
			return true;
			
		} else if ("MemoryUsagePercent".equals(option.getName())) {
			
			MEM_USAGE_percent = (Integer) option.getValue();
			
			return true;
			
		} else if ("TranspositionTable".equals(option.getName())) {
			
			useTranspositionTable = option.getValue().equals("true");
			
			return true;
		
		} else if ("EvalCache".equals(option.getName())) {
			
			useEvalCache = option.getValue().equals("true");
			
			return true;
			
		} else if ("SyzygyDTZCache".equals(option.getName())) {
			
			useSyzygyDTZCache = option.getValue().equals("true");
			
			return true;
			
		} else if ("SyzygyPath".equals(option.getName())) {
			
			TbPath = (String) option.getValue();
			
			return true;
		
		} else if ("SyzygyOnline".equals(option.getName())) {
			
			use_online_syzygy = option.getValue().equals("true");
			
			return true;
			
		} else if ("Hidden Depth".equals(option.getName())) {
			
			hiddenDepth = (Integer) option.getValue();
			
			return true;
		}
		
		
		return false;
	}
	
	
	@Override
	public String getBoardFactoryClassName() {
		throw new UnsupportedOperationException();
	}
	
	
	@Override
	public String getSemaphoreFactoryClassName() {
		return bagaturchess.bitboard.impl.utils.BinarySemaphoreFactory_Dummy.class.getName();
	}
	
	
	private static final String getDefaultTBPath() {
		
		File work_dir = new File(".");
		
		if (work_dir.getName().equals("bin")) {
			
			work_dir = work_dir.getParentFile();
		}
		
		return work_dir.getAbsolutePath() + File.separatorChar + "data" + File.separatorChar + "egtb";
	}
}
