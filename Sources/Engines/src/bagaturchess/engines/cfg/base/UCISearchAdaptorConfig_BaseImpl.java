package bagaturchess.engines.cfg.base;


import bagaturchess.bitboard.api.BoardUtils;
import bagaturchess.bitboard.common.Utils;
import bagaturchess.bitboard.impl.utils.ReflectionUtils;
import bagaturchess.uci.api.ISearchAdaptorConfig;
import bagaturchess.uci.api.ITimeConfig;
import bagaturchess.uci.api.IUCIOptionsProvider;
import bagaturchess.uci.api.IUCIOptionsRegistry;
import bagaturchess.uci.impl.commands.options.UCIOption;
import bagaturchess.uci.impl.commands.options.UCIOptionCombo;
import bagaturchess.uci.impl.commands.options.UCIOptions;


public class UCISearchAdaptorConfig_BaseImpl implements ISearchAdaptorConfig {
	
	
	private static final boolean DEFAULT_OwnBook 		= true;
	private static final boolean DEFAULT_Ponder 		= false;
	private static final boolean DEFAULT_AnalyseMode 	= false;
	private static final boolean DEFAULT_Chess960 		= false;
	
	
	private final ITimeConfig timeCfg 					= new TimeConfigImpl();
	
	private UCIOption[] options 						= new UCIOption[] {
			new UCIOption(UCIOptions.OPTION_NAME_OwnBook			, DEFAULT_OwnBook		, "type check default " + DEFAULT_OwnBook),
			new UCIOption(UCIOptions.OPTION_NAME_Ponder				, DEFAULT_Ponder		, "type check default " + DEFAULT_Ponder),
			new UCIOption(UCIOptions.OPTION_NAME_UCI_AnalyseMode	, DEFAULT_AnalyseMode	, "type check default " + DEFAULT_AnalyseMode),
			new UCIOption(UCIOptions.OPTION_NAME_UCI_Chess960		, DEFAULT_Chess960		, "type check default " + DEFAULT_Chess960)
	};
	
	private String rootSearchImpl_ClassName;
	private Object rootSearchImpl_ConfigObj;
	
	private boolean isOwnBookEnabled 	= DEFAULT_OwnBook;
	private boolean isPonderingEnabled 	= DEFAULT_Ponder;
	private boolean isAnalyzeMode 		= DEFAULT_AnalyseMode;
	private boolean isChess960 			= DEFAULT_Chess960;
	
	
	public UCISearchAdaptorConfig_BaseImpl(String[] args) {
		rootSearchImpl_ClassName = args[0];
		rootSearchImpl_ConfigObj = ReflectionUtils.createObjectByClassName_StringsConstructor(
											args[1], Utils.copyOfRange(args, 2)
										);
	}
	
	
	@Override
	public ITimeConfig getTimeConfig() {
		return timeCfg;
	}
	
	
	@Override
	public String getRootSearchClassName() {
		return rootSearchImpl_ClassName;
	}
	
	
	@Override
	public Object getRootSearchConfig() {
		return rootSearchImpl_ConfigObj;
	}
	
	
	@Override
	public boolean isOwnBookEnabled() {
		return isOwnBookEnabled;
	}
	
	
	@Override
	public boolean isPonderingEnabled() {
		return isPonderingEnabled;
	}
	
	
	@Override
	public boolean isAnalyzeMode() {
		return isAnalyzeMode;
	}
	
	
	@Override
	public boolean isChess960() {
		return isChess960;
	}
	
	
	@Override
	public void registerProviders(IUCIOptionsRegistry registry) {
		
		registry.registerProvider(this);
		
		if (rootSearchImpl_ConfigObj instanceof IUCIOptionsProvider) {
			((IUCIOptionsProvider) rootSearchImpl_ConfigObj).registerProviders(registry);
		}
	}
	
	
	@Override
	public UCIOption[] getSupportedOptions() {
		return options;
	}
	
	
	@Override
	public boolean applyOption(UCIOption option) {
		
		if (UCIOptions.OPTION_NAME_Ponder.equals(option.getName())) {
			isPonderingEnabled = (Boolean) option.getValue();
			return true;
		} else if (UCIOptions.OPTION_NAME_OwnBook.equals(option.getName())) {
			isOwnBookEnabled = (Boolean) option.getValue();
			return true;
		} else if (UCIOptions.OPTION_NAME_UCI_AnalyseMode.equals(option.getName())) {
			isAnalyzeMode = (Boolean) option.getValue();
			return true;
		} else if (UCIOptions.OPTION_NAME_UCI_Chess960.equals(option.getName())) {
			isChess960 = (Boolean) option.getValue();
			BoardUtils.isFRC = isChess960;
			return true;
		}
		
		return false;
	}
}
